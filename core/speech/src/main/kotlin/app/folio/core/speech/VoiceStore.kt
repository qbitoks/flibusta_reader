package app.folio.core.speech

import android.content.Context
import android.util.Log
import app.folio.core.opds.HttpsRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Where a catalog voice stands on this device. */
sealed interface VoiceStatus {
    data object Missing : VoiceStatus
    /** [fraction] null while the size is unknown or the archive is being unpacked. */
    data class Downloading(val fraction: Float?) : VoiceStatus
    data object Failed : VoiceStatus
    data object Installed : VoiceStatus
}

/** A downloadable offline voice: a [PiperVoice] or the [SileroModel]. */
sealed interface OfflineVoice {
    /** Unique across engines; also the folder name under `files/voices/<folder>/`. */
    val id: String
    val folder: String
    /** A `.tar.bz2` voice archive (Piper) or a single `.onnx` model (Silero). */
    val url: String
    val bytes: Long
}

/** Every voice the store manages. */
val OfflineVoices: List<OfflineVoice> = PiperCatalog + SileroModel

/**
 * Offline voices on disk: Piper in `files/voices/piper/<id>/` (model `.onnx`, `tokens.txt`, `espeak-ng-data/`),
 * Silero in `files/voices/silero/<id>/model.onnx`. A voice counts as installed only once complete and renamed
 * into place, so a crash mid-download leaves only `<id>.part` / `<id>.tmp`, cleaned up by the next attempt.
 */
@Singleton
class VoiceStore @Inject constructor(@ApplicationContext context: Context) {
    private val root = File(context.filesDir, "voices")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = mutableMapOf<String, Job>()
    private val _status = MutableStateFlow(OfflineVoices.associate { it.id to statusOnDisk(it) })
    val status: StateFlow<Map<String, VoiceStatus>> = _status.asStateFlow()

    fun installed(id: String): Boolean = _status.value[id] == VoiceStatus.Installed

    fun dir(voice: OfflineVoice) = File(File(root, voice.folder), voice.id)

    fun download(voice: OfflineVoice) {
        if (jobs[voice.id]?.isActive == true || installed(voice.id)) return
        set(voice.id, VoiceStatus.Downloading(0f))
        jobs[voice.id] = scope.launch {
            try {
                install(voice)
                set(voice.id, VoiceStatus.Installed)
            } catch (e: CancellationException) {
                cleanUp(voice)
                set(voice.id, VoiceStatus.Missing)
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Voice ${voice.id} failed", e)
                cleanUp(voice)
                set(voice.id, VoiceStatus.Failed)
            }
        }
    }

    fun cancel(id: String) {
        jobs.remove(id)?.cancel()
    }

    fun delete(voice: OfflineVoice) {
        cancel(voice.id)
        scope.launch {
            dir(voice).deleteRecursively()
            cleanUp(voice)
            set(voice.id, VoiceStatus.Missing)
        }
    }

    private suspend fun install(voice: OfflineVoice) {
        val parent = File(root, voice.folder).apply { mkdirs() }
        cleanUp(voice)
        val part = File(parent, "${voice.id}.part")
        runInterruptible {
            HttpsRequest(readTimeoutMs = 60_000).open(voice.url) { response ->
                val total = response.contentLength.takeIf { it > 0 } ?: voice.bytes
                if (total > MAX_ARCHIVE_BYTES) throw IOException("Voice archive too big: $total")
                copy(response.stream, part) { done -> set(voice.id, VoiceStatus.Downloading((done.toFloat() / total).coerceIn(0f, 1f))) }
            }
        }
        currentCoroutineContext().ensureActive()
        set(voice.id, VoiceStatus.Downloading(null))
        val tmp = File(parent, "${voice.id}.tmp")
        when (voice) {
            is PiperVoice -> runInterruptible { unpack(part, tmp) }
            SileroModel -> {
                if (part.length() != voice.bytes) throw IOException("Model size ${part.length()} != ${voice.bytes}")
                tmp.mkdirs()
                if (!part.renameTo(File(tmp, SileroModel.FILE))) throw IOException("Can't move ${voice.id} into place")
            }
        }
        part.delete()
        check(complete(voice, tmp)) { "No model in ${voice.id}" }
        if (!tmp.renameTo(dir(voice))) throw IOException("Can't move ${voice.id} into place")
    }

    private fun copy(input: InputStream, to: File, progress: (Long) -> Unit) {
        var written = 0L
        var published = 0L
        FileOutputStream(to).use { out ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                if (Thread.currentThread().isInterrupted) throw InterruptedException()
                val n = input.read(buffer)
                if (n < 0) break
                written += n
                if (written > MAX_ARCHIVE_BYTES) throw IOException("Voice archive too big")
                out.write(buffer, 0, n)
                val now = System.nanoTime()
                if (now - published > 200_000_000L) {
                    published = now
                    progress(written)
                }
            }
        }
    }

    private fun set(id: String, status: VoiceStatus) = _status.update { it + (id to status) }

    private fun statusOnDisk(voice: OfflineVoice) = if (complete(voice, dir(voice))) VoiceStatus.Installed else VoiceStatus.Missing

    private fun cleanUp(voice: OfflineVoice) {
        val parent = File(root, voice.folder)
        File(parent, "${voice.id}.part").delete()
        File(parent, "${voice.id}.tmp").deleteRecursively()
    }

    private companion object {
        const val TAG = "VoiceStore"
        const val MAX_ARCHIVE_BYTES = 200L * 1024 * 1024
    }
}

/** Whether [dir] holds everything [voice]'s engine loads. */
internal fun complete(voice: OfflineVoice, dir: File): Boolean = when (voice) {
    is PiperVoice -> voiceFiles(dir) != null
    SileroModel -> File(dir, SileroModel.FILE).isFile
}

/** The files sherpa-onnx needs from an unpacked voice, or null when it isn't complete. */
internal data class VoiceFiles(val model: File, val tokens: File, val espeakData: File)

internal fun voiceFiles(dir: File): VoiceFiles? {
    val model = dir.listFiles { f -> f.isFile && f.name.endsWith(".onnx") }?.firstOrNull() ?: return null
    val tokens = File(dir, "tokens.txt").takeIf { it.isFile } ?: return null
    val espeak = File(dir, "espeak-ng-data").takeIf { it.isDirectory } ?: return null
    return VoiceFiles(model, tokens, espeak)
}

/**
 * Unpacks a sherpa-onnx voice `.tar.bz2` into [into], dropping the archive's top folder. Entries that
 * would land outside [into] (`..`, absolute paths) and links are refused.
 */
internal fun unpack(archive: File, into: File) {
    into.mkdirs()
    val base = into.canonicalPath + File.separator
    TarArchiveInputStream(BZip2CompressorInputStream(archive.inputStream().buffered())).use { tar ->
        while (true) {
            if (Thread.currentThread().isInterrupted) throw InterruptedException()
            val entry = tar.nextEntry ?: break
            val relative = entry.name.trimStart('/').substringAfter('/', "")
            if (relative.isEmpty()) continue
            if (entry.isSymbolicLink || entry.isLink) throw IOException("Link in voice archive: ${entry.name}")
            val target = File(into, relative)
            if (!target.canonicalPath.startsWith(base)) throw IOException("Entry outside the voice folder: ${entry.name}")
            if (entry.isDirectory) {
                target.mkdirs()
            } else {
                target.parentFile?.mkdirs()
                target.outputStream().use { tar.copyTo(it) }
            }
        }
    }
}
