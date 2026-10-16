package app.folio.core.speech

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
import kotlinx.coroutines.launch
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
    private val player = PcmPlayer()
    private var prepared: Pair<Key, Deferred<Pcm>>? = null

    private data class Key(val voice: String, val text: String, val rate: Float)

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
        player.play(audio)
    }

    override fun release() {
        prepared?.second?.cancel()
        prepared = null
        player.release()
        scope.launch {
            tts?.second?.release()
            tts = null
        }
    }

    private fun synthesize(key: Key): Pcm {
        val engine = engineFor(key.voice)
        val generated = engine.generate(key.text, 0, key.rate)
        return Pcm(generated.samples, generated.sampleRate)
    }

    private fun engineFor(id: String): OfflineTts {
        tts?.let { (loaded, engine) -> if (loaded == id) return engine else engine.release() }
        tts = null
        val files = piperVoice(id)?.let { voiceFiles(voices.store.dir(it)) } ?: throw SpeechUnavailableException("Voice $id is not installed")
        val config = OfflineTtsConfig(model = OfflineTtsModelConfig(
            vits = OfflineTtsVitsModelConfig(model = files.model.path, tokens = files.tokens.path, dataDir = files.espeakData.path),
            numThreads = 2,
        ))
        return OfflineTts(config = config).also { tts = id to it }
    }
}
