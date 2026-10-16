package app.folio.core.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Android's own [TextToSpeech]: whichever engine and voice the user picked in system settings. */
@Singleton
class SystemTtsEngine @Inject constructor(@ApplicationContext private val context: Context) : SpeechEngine {
    private var tts: TextToSpeech? = null
    private var ready: CompletableDeferred<TextToSpeech>? = null
    private var locale: Locale? = null
    private val ids = AtomicLong()

    @Volatile
    private var pending: Pair<String, CancellableContinuation<Unit>>? = null

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String) {}
        override fun onDone(utteranceId: String) = finish(utteranceId, null)
        override fun onStop(utteranceId: String, interrupted: Boolean) = finish(utteranceId, null)

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String) = finish(utteranceId, SpeechUnavailableException("TTS error"))
        override fun onError(utteranceId: String, errorCode: Int) = finish(utteranceId, SpeechUnavailableException("TTS error $errorCode"))
    }

    private fun finish(id: String, error: Exception?) {
        val (pendingId, cont) = pending ?: return
        if (pendingId != id) return
        pending = null
        if (cont.isActive) if (error == null) cont.resume(Unit) else cont.resumeWithException(error)
    }

    private suspend fun engine(): TextToSpeech {
        ready?.let { return it.await() }
        val deferred = CompletableDeferred<TextToSpeech>()
        ready = deferred
        var created: TextToSpeech? = null
        created = TextToSpeech(context) { status ->
            val t = created
            if (status == TextToSpeech.SUCCESS && t != null) {
                t.setOnUtteranceProgressListener(listener)
                deferred.complete(t)
            } else {
                deferred.completeExceptionally(SpeechUnavailableException("No TTS engine"))
            }
        }
        tts = created
        return try {
            deferred.await()
        } catch (e: SpeechUnavailableException) {
            release()
            throw e
        }
    }

    override suspend fun speak(text: String, locale: Locale, rate: Float, pitch: Float) {
        val t = engine()
        if (this.locale != locale) {
            val result = t.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                throw SpeechUnavailableException("No voice for $locale")
            }
            this.locale = locale
        }
        t.setSpeechRate(rate)
        t.setPitch(pitch)
        val id = "folio-${ids.incrementAndGet()}"
        suspendCancellableCoroutine { cont ->
            pending = id to cont
            cont.invokeOnCancellation {
                pending = null
                t.stop()
            }
            if (t.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) {
                pending = null
                cont.resumeWithException(SpeechUnavailableException("TTS refused the text"))
            }
        }
    }

    override fun release() {
        tts?.shutdown()
        tts = null
        ready = null
        locale = null
        pending = null
    }
}
