package app.folio.core.data.catalog

import android.content.Context
import android.net.ConnectivityManager
import android.net.Uri
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import app.folio.core.data.db.DownloadEntity
import app.folio.core.data.db.DownloadState
import app.folio.core.data.db.FolioDatabase
import app.folio.core.data.library.LibraryRepository
import app.folio.core.opds.CatalogEntry
import app.folio.core.opds.DownloadCheck
import app.folio.core.opds.HttpsRequest
import app.folio.core.opds.Resume
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/** A download as the UI shows it: the stored row with live byte counts while it runs. */
data class DownloadItem(val entity: DownloadEntity, val bytes: Long, val total: Long, val inLibrary: Boolean) {
    val id: String get() = entity.id
    val state: DownloadState get() = entity.downloadState
    val fraction: Float? get() = if (total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else null
    /** Finished and the book is still in the library, so it can be opened. */
    val readable: Boolean get() = state == DownloadState.Done && inLibrary && entity.bookId != null
}

/**
 * Catalog downloads that survive dropped connections and restarts. Each one is a row in
 * `downloads` plus unique WorkManager work that waits for a network, resumes the partial file
 * with HTTP Range (`files/downloads/<id>.part`), retries with backoff and finally imports the
 * book into the library.
 */
@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    db: FolioDatabase,
    private val catalog: CatalogRepository,
    private val library: LibraryRepository,
) {
    private val dao = db.downloads()
    private val rows = Mutex()
    private val dir = File(context.filesDir, "downloads")
    /** id → bytes, total of running transfers; Room gets them when a transfer stops. */
    private val live = MutableStateFlow<Map<String, Pair<Long, Long>>>(emptyMap())

    val downloads: Flow<List<DownloadItem>> = combine(dao.observeAll(), live, library.allBooks) { rows, progress, books ->
        val ids = books.mapTo(HashSet()) { it.id }
        rows.map { row ->
            val (bytes, total) = progress[row.id] ?: (row.bytes to row.total)
            DownloadItem(row, bytes, total, row.bookId != null && row.bookId in ids)
        }
    }

    /** Starts (or resumes) downloading [format] of [entry]; a book already downloaded is left as is. */
    suspend fun enqueue(entry: CatalogEntry, format: String) {
        val link = entry.downloads[format] ?: throw IllegalArgumentException("Формат недоступен")
        val id = sha1(link)
        val now = System.currentTimeMillis()
        val schedule = rows.withLock {
            val existing = dao.get(id)
            when {
                existing == null -> {
                    dao.upsert(
                        DownloadEntity(
                            id = id, link = link, format = format, title = entry.title, author = entry.author,
                            coverUrl = entry.cover, state = DownloadState.Queued.name, error = offlineCode(), createdAt = now, updatedAt = now,
                        ),
                    )
                    true
                }
                existing.downloadState == DownloadState.Done && existing.bookId != null && library.contains(existing.bookId) -> false
                existing.downloadState == DownloadState.Queued || existing.downloadState == DownloadState.Running -> false
                else -> {
                    val restart = existing.downloadState == DownloadState.Done
                    dao.upsert(
                        existing.copy(
                            state = DownloadState.Queued.name, error = offlineCode(), attempts = 0, bookId = null, updatedAt = now,
                            createdAt = if (restart) now else existing.createdAt,
                            bytes = if (restart) 0 else existing.bytes, total = if (restart) -1 else existing.total,
                        ),
                    )
                    true
                }
            }
        }
        if (schedule) schedule(id)
    }

    suspend fun pause(id: String) {
        rows.withLock {
            val row = dao.get(id) ?: return
            if (row.downloadState != DownloadState.Queued && row.downloadState != DownloadState.Running) return
            dao.upsert(row.copy(state = DownloadState.Paused.name, updatedAt = System.currentTimeMillis()))
        }
        WorkManager.getInstance(context).cancelUniqueWork(workName(id))
    }

    /** Continues a paused or failed download from where it stopped. */
    suspend fun resume(id: String) {
        rows.withLock {
            val row = dao.get(id) ?: return
            if (row.downloadState != DownloadState.Paused && row.downloadState != DownloadState.Failed) return
            dao.upsert(row.copy(state = DownloadState.Queued.name, error = offlineCode(), attempts = 0, updatedAt = System.currentTimeMillis()))
        }
        schedule(id)
    }

    /** Stops the download and forgets it; a finished book stays in the library. */
    suspend fun remove(id: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(id))
        rows.withLock { dao.delete(id) }
        live.update { it - id }
        withContext(Dispatchers.IO) { part(id).delete() }
    }

    suspend fun clearFinished() = rows.withLock { dao.deleteDone() }

    private fun schedule(id: String) {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(DownloadWorker.KEY_ID to id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(workName(id), ExistingWorkPolicy.REPLACE, request)
    }

    internal enum class Outcome { Done, Retry, Failed }

    /** One worker run: transfer what is missing, then import. */
    internal suspend fun run(id: String, attempt: Int): Outcome {
        val row = dao.get(id) ?: return Outcome.Failed
        if (row.downloadState != DownloadState.Queued && row.downloadState != DownloadState.Running) return Outcome.Done
        update(id) { it.copy(state = DownloadState.Running.name, attempts = attempt, error = null) }
        val part = part(id)
        val meta = Meta(row.total, row.validator)
        try {
            // A connection that drops after some data is resumed right away; a stalled one waits for backoff.
            var quickRetries = 0
            while (true) {
                val before = withContext(Dispatchers.IO) { part.length() }
                try {
                    transfer(id, catalog.downloadAddress(row.link), part, meta)
                    break
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    currentCoroutineContext().ensureActive()
                    val progressed = withContext(Dispatchers.IO) { part.length() } > before
                    if (!NetworkErrors.isTransient(e) || !progressed || quickRetries++ >= MAX_QUICK_RETRIES) throw e
                }
            }
            val head = withContext(Dispatchers.IO) { part.inputStream().use { it.readNBytes(1024) } }
            if (DownloadCheck.looksLikeHtml(head)) throw DownloadException(NetworkErrors.NOT_BOOK, "Сервер вернул страницу вместо книги")
            val result = library.import(Uri.fromFile(part), sourceUri = row.link)
            val size = withContext(Dispatchers.IO) { part.length().also { part.delete() } }
            live.update { it - id }
            update(id) { it.copy(state = DownloadState.Done.name, bookId = result.bookId, bytes = size, total = size, error = null) }
            return Outcome.Done
        } catch (e: CancellationException) {
            stopped(id, part, meta)
            throw e
        } catch (e: Exception) {
            // Disconnecting on cancellation surfaces as a socket error: still a stop, not a failure.
            if (!currentCoroutineContext().isActive) {
                stopped(id, part, meta)
                throw CancellationException("Download stopped").apply { initCause(e) }
            }
            val retry = NetworkErrors.isTransient(e) && attempt + 1 < MAX_ATTEMPTS
            Log.w(TAG, "Download ${row.format} ${row.link} failed (attempt $attempt, retry $retry)", e)
            val keepPart = NetworkErrors.isTransient(e)
            val bytes = withContext(Dispatchers.IO) { if (!keepPart) part.delete(); part.length() }
            live.update { it - id }
            update(id) {
                it.copy(
                    state = (if (retry) DownloadState.Queued else DownloadState.Failed).name,
                    error = NetworkErrors.code(e), bytes = bytes,
                    total = if (keepPart) meta.total else -1, validator = if (keepPart) meta.validator else null,
                )
            }
            return if (retry) Outcome.Retry else Outcome.Failed
        }
    }

    /** Size and validator of the file being fetched, updated as soon as response headers arrive. */
    private class Meta(@Volatile var total: Long, @Volatile var validator: String?)

    /** Stopped by pause/remove (row already changed) or by the system (lost network): wait for the next run. */
    private suspend fun stopped(id: String, part: File, meta: Meta) = withContext(NonCancellable) {
        val bytes = part.length()
        val offline = offlineCode() != null
        live.update { it - id }
        update(id) { r ->
            val waiting = r.downloadState == DownloadState.Running
            r.copy(
                state = if (waiting) DownloadState.Queued.name else r.state,
                error = if (waiting && offline) NetworkErrors.OFFLINE else r.error,
                bytes = bytes, total = meta.total, validator = meta.validator,
            )
        }
    }

    /**
     * Fetches the rest of the file into [part]. The blocking read is cut short on cancellation by
     * disconnecting the socket, so «Пауза» takes effect at once.
     */
    private suspend fun transfer(id: String, address: String, part: File, meta: Meta) = coroutineScope {
        val connection = AtomicReference<HttpURLConnection?>()
        val watcher = launch {
            try { awaitCancellation() } finally { connection.get()?.disconnect() }
        }
        try {
            runInterruptible(Dispatchers.IO) {
                if (!fetch(id, address, part, meta, connection)) {
                    part.delete()
                    meta.validator = null
                    if (!fetch(id, address, part, meta, connection)) throw DownloadException(NetworkErrors.DROPPED, "Сервер не может продолжить загрузку")
                }
            }
        } finally {
            watcher.cancel()
        }
    }

    /** Fetches into [part], updating [meta]; false when the partial file must be dropped ([Resume.Discard]). */
    private fun fetch(id: String, address: String, part: File, meta: Meta, connection: AtomicReference<HttpURLConnection?>): Boolean {
        part.parentFile?.mkdirs()
        val have = if (part.exists()) part.length() else 0L
        val headers = buildMap {
            put("Accept-Encoding", "identity")
            if (have > 0) {
                put("Range", "bytes=$have-")
                meta.validator?.let { put("If-Range", it) }
            }
        }
        val request = HttpsRequest(readTimeoutMs = READ_TIMEOUT_MS) { url -> (url.openConnection() as HttpURLConnection).also(connection::set) }
        return request.open(address, headers) { response ->
            val validator = response.header("ETag")?.takeUnless { it.startsWith("W/") } ?: response.header("Last-Modified")
            val (append, total) = when (val plan = Resume.plan(have, response.code, response.header("Content-Range"), response.contentLength)) {
                Resume.Complete -> { meta.total = have; return@open true }
                Resume.Discard -> return@open false
                is Resume.Append -> true to plan.total
                is Resume.Restart -> false to plan.total
            }
            meta.total = total
            meta.validator = validator
            if (total > LibraryRepository.MAX_BOOK_BYTES) throw DownloadException(NetworkErrors.TOO_BIG, "Книга больше 50 МБ")
            if (have > 0) Log.i(TAG, "Download $id: ${if (append) "resuming at $have" else "server sent the whole file again"} of $total")
            var written = if (append) have else 0L
            var published = 0L
            live.update { it + (id to (written to total)) }
            FileOutputStream(part, append).use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    if (Thread.currentThread().isInterrupted) throw InterruptedException()
                    val count = response.stream.read(buffer)
                    if (count < 0) break
                    written += count
                    if (written > LibraryRepository.MAX_BOOK_BYTES) throw DownloadException(NetworkErrors.TOO_BIG, "Книга больше 50 МБ")
                    output.write(buffer, 0, count)
                    val now = System.nanoTime()
                    if (now - published > 200_000_000L) {
                        published = now
                        live.update { it + (id to (written to total)) }
                    }
                }
            }
            if (written == 0L) throw DownloadException(NetworkErrors.DROPPED, "Сервер прислал пустой ответ")
            if (total > 0 && written < total) throw DownloadException(NetworkErrors.DROPPED, "Соединение оборвалось")
            if (total > 0 && written > total) {
                part.delete()
                throw DownloadException(NetworkErrors.DROPPED, "Размер файла не сошёлся")
            }
            meta.total = written
            true
        }
    }

    private suspend fun update(id: String, change: (DownloadEntity) -> DownloadEntity) = rows.withLock {
        dao.get(id)?.let { dao.upsert(change(it).copy(updatedAt = System.currentTimeMillis())) }
    }

    private fun part(id: String) = File(dir, "$id.part")

    /** [NetworkErrors.OFFLINE] while there is no network, so a waiting download says why. */
    private fun offlineCode(): String? =
        NetworkErrors.OFFLINE.takeIf { context.getSystemService(ConnectivityManager::class.java)?.activeNetwork == null }

    private fun workName(id: String) = "download-$id"

    internal companion object {
        const val TAG = "catalog-download"
        const val MAX_ATTEMPTS = 8
        const val MAX_QUICK_RETRIES = 5
        /** Flibusta builds EPUB on request and stays silent for up to a minute and a half first. */
        const val READ_TIMEOUT_MS = 150_000
    }
}

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val downloads: DownloadManager,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_ID) ?: return Result.failure()
        return when (downloads.run(id, runAttemptCount)) {
            DownloadManager.Outcome.Done -> Result.success()
            DownloadManager.Outcome.Retry -> Result.retry()
            DownloadManager.Outcome.Failed -> Result.failure()
        }
    }

    internal companion object {
        const val KEY_ID = "id"
    }
}
