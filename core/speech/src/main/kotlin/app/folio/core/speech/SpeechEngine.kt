package app.folio.core.speech

import java.util.Locale

/** A text-to-speech backend; the app reads through [SystemTtsEngine]. */
interface SpeechEngine {
    /**
     * Speaks [text] and returns when it has been spoken. Cancelling the caller stops the voice at once.
     * Throws [SpeechUnavailableException] when the engine can't speak [locale].
     */
    suspend fun speak(text: String, locale: Locale, rate: Float, pitch: Float)

    /** Hint: [text] is spoken next, so an engine that synthesizes ahead can start on it now. */
    fun prepare(text: String, locale: Locale, rate: Float) {}

    /** Frees the engine; the next [speak] starts it again. */
    fun release()
}

class SpeechUnavailableException(message: String) : Exception(message)
