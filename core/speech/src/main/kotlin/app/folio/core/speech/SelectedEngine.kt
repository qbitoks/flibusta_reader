package app.folio.core.speech

import android.util.Log
import kotlinx.coroutines.CancellationException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The engine the user picked: Piper or Silero when chosen and able to read the book's language,
 * the system engine otherwise (so a missing download never leaves a book silent).
 * An offline engine that fails (native library, broken model) is dropped until restart: the system voice reads on.
 */
@Singleton
class SelectedEngine @Inject constructor(
    private val voices: SpeechVoices,
    private val system: SystemTtsEngine,
    private val piper: PiperEngine,
    private val silero: SileroEngine,
) : SpeechEngine {
    private val broken = mutableSetOf<SpeechEngine>()

    private fun pick(locale: Locale): SpeechEngine = when {
        voices.usesSilero(locale) && silero !in broken -> silero
        voices.usesPiper(locale) && piper !in broken -> piper
        else -> system
    }

    override fun prepare(text: String, locale: Locale, rate: Float) {
        val engine = pick(locale)
        try {
            engine.prepare(text, locale, rate)
        } catch (e: Throwable) {
            fail(engine, e)
        }
    }

    override suspend fun speak(text: String, locale: Locale, rate: Float, pitch: Float) {
        val engine = pick(locale)
        try {
            engine.speak(text, locale, rate, pitch)
        } catch (e: Throwable) {
            fail(engine, e)
            system.speak(text, locale, rate, pitch)
        }
    }

    private fun fail(engine: SpeechEngine, e: Throwable) {
        if (e is CancellationException || e is SpeechUnavailableException || engine === system) throw e
        Log.e("SelectedEngine", "${engine.javaClass.simpleName} failed, falling back to the system voice", e)
        broken += engine
        runCatching { engine.release() }
    }

    override fun release() {
        system.release()
        piper.release()
        silero.release()
    }
}
