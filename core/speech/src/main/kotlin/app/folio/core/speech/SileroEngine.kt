package app.folio.core.speech

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline Russian voices: the Silero model ([SileroModel]) run by onnxruntime. Like [PiperEngine], the
 * next phrase is synthesized while the current one plays ([prepare]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class SileroEngine @Inject constructor(private val voices: SpeechVoices) : SpeechEngine {
    // One thread loads the model and synthesizes, so phrases never compete for the CPU.
    private val synthesis = Dispatchers.Default.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + synthesis)
    private var session: OrtSession? = null
    private val player = PcmPlayer()
    private var prepared: Pair<Key, Deferred<Pcm>>? = null

    private data class Key(val speaker: Long, val text: String, val rate: Float)

    override fun prepare(text: String, locale: Locale, rate: Float) {
        if (!voices.usesSilero(locale)) return
        val key = Key(voices.choice.value.silero.index, text, rate)
        if (prepared?.first == key) return
        prepared = key to scope.async { synthesize(key) }
    }

    override suspend fun speak(text: String, locale: Locale, rate: Float, pitch: Float) {
        if (!voices.usesSilero(locale)) throw SpeechUnavailableException("No Silero model for $locale")
        val key = Key(voices.choice.value.silero.index, text, rate)
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
            session?.close()
            session = null
        }
    }

    /** All chunks of the phrase, joined; a phrase with nothing the model can read is silence. */
    private fun synthesize(key: Key): Pcm {
        val parts = sileroChunks(key.text).map(::sileroTokens).filter { it.size > 2 }.map { run(it, key) }
        val samples = FloatArray(parts.sumOf { it.size })
        var at = 0
        for (p in parts) {
            p.copyInto(samples, at)
            at += p.size
        }
        return Pcm(samples, SileroModel.SAMPLE_RATE)
    }

    private fun run(tokens: LongArray, key: Key): FloatArray {
        val env = OrtEnvironment.getEnvironment()
        val inputs = mapOf(
            "text" to OnnxTensor.createTensor(env, LongBuffer.wrap(tokens), longArrayOf(1, tokens.size.toLong())),
            "speaker" to OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(key.speaker)), longArrayOf(1)),
            "speed" to OnnxTensor.createTensor(env, FloatBuffer.wrap(floatArrayOf(key.rate)), longArrayOf()),
        )
        try {
            session().run(inputs).use { result ->
                return (result[0] as OnnxTensor).floatBuffer.let { b -> FloatArray(b.remaining()).also(b::get) }
            }
        } finally {
            inputs.values.forEach(OnnxTensor::close)
        }
    }

    private fun session(): OrtSession {
        session?.let { return it }
        val model = File(voices.store.dir(SileroModel), SileroModel.FILE)
        if (!model.isFile) throw SpeechUnavailableException("Silero model is not installed")
        val options = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(2)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }
        return OrtEnvironment.getEnvironment().createSession(model.path, options).also { session = it }
    }
}
