package app.folio.core.speech

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline neural voices: Piper models run by sherpa-onnx. The next phrase is synthesized while the
 * current one plays ([prepare]), so phrases follow each other without a pause.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class PiperEngine @Inject constructor(private val voices: SpeechVoices) : SpeechEngine {
    // sherpa-onnx's OfflineTts is not thread-safe: one thread does all loading and synthesis.
    private val synthesis = Dispatchers.Default.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + synthesis)
    private var tts: Pair<String, OfflineTts>? = null
    private var track: AudioTrack? = null
    private var prepared: Pair<Key, Deferred<Audio>>? = null

    private data class Key(val voice: String, val text: String, val rate: Float)
    private class Audio(val samples: FloatArray, val sampleRate: Int)

    override fun prepare(text: String, locale: Locale, rate: Float) {
        val voice = voices.piperVoiceFor(locale) ?: return
        val key = Key(voice.id, text, rate)
        if (prepared?.first == key) return
        prepared = key to scope.async { synthesize(key) }
    }

    override suspend fun speak(text: String, locale: Locale, rate: Float, pitch: Float) {
        val voice = voices.piperVoiceFor(locale) ?: throw SpeechUnavailableException("No Piper voice for $locale")
        val key = Key(voice.id, text, rate)
        val ready = prepared?.takeIf { it.first == key }?.second
        prepared = null
        val audio = ready?.await() ?: withContext(synthesis) { synthesize(key) }
        play(audio)
    }

    override fun release() {
        prepared?.second?.cancel()
        prepared = null
        track?.release()
        track = null
        scope.launch {
            tts?.second?.release()
            tts = null
        }
    }

    private fun synthesize(key: Key): Audio {
        val engine = engineFor(key.voice)
        val generated = engine.generate(key.text, 0, key.rate)
        return Audio(generated.samples, generated.sampleRate)
    }

    private fun engineFor(id: String): OfflineTts {
        tts?.let { (loaded, engine) -> if (loaded == id) return engine else engine.release() }
        tts = null
        val files = voiceFiles(voices.store.dir(id)) ?: throw SpeechUnavailableException("Voice $id is not installed")
        val config = OfflineTtsConfig(model = OfflineTtsModelConfig(
            vits = OfflineTtsVitsModelConfig(model = files.model.path, tokens = files.tokens.path, dataDir = files.espeakData.path),
            numThreads = 2,
        ))
        return OfflineTts(config = config).also { tts = id to it }
    }

    /** Plays [audio] to the end; cancelling stops the sound at once. */
    private suspend fun play(audio: Audio) = withContext(Dispatchers.IO) {
        val t = track(audio.sampleRate)
        val start = t.playbackHeadPosition
        try {
            t.play()
            var offset = 0
            while (offset < audio.samples.size) {
                currentCoroutineContext().ensureActive()
                val n = minOf(CHUNK, audio.samples.size - offset)
                val written = t.write(audio.samples, offset, n, AudioTrack.WRITE_BLOCKING)
                if (written < 0) throw SpeechUnavailableException("AudioTrack error $written")
                offset += written
            }
            while (t.playbackHeadPosition - start < audio.samples.size) delay(15)
        } catch (e: Throwable) {
            t.pause()
            t.flush()
            throw e
        }
    }

    private fun track(sampleRate: Int): AudioTrack {
        track?.takeIf { it.sampleRate == sampleRate }?.let { return it }
        track?.release()
        val format = AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
        return AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(format)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT) * 4)
            .build()
            .also { track = it }
    }

    private companion object {
        const val CHUNK = 4096
    }
}

/**
 * The engine the user picked: Piper when chosen and it has a voice for the book's language,
 * the system engine otherwise (so a missing download never leaves a book silent).
 */
@Singleton
class SelectedEngine @Inject constructor(
    private val voices: SpeechVoices,
    private val system: SystemTtsEngine,
    private val piper: PiperEngine,
) : SpeechEngine {
    private fun pick(locale: Locale): SpeechEngine = if (voices.usesPiper(locale)) piper else system

    override fun prepare(text: String, locale: Locale, rate: Float) = pick(locale).prepare(text, locale, rate)

    override suspend fun speak(text: String, locale: Locale, rate: Float, pitch: Float) = pick(locale).speak(text, locale, rate, pitch)

    override fun release() {
        system.release()
        piper.release()
    }
}
