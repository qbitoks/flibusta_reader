package app.folio.core.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * «▶» in the voice list: says a sample with one voice through its own [TextToSpeech], apart from the reading
 * engine. It stays connected to the last engine until [release], so trying several voices doesn't reconnect.
 */
@Singleton
class VoicePreview @Inject constructor(@ApplicationContext private val context: Context) {
    private var tts: TextToSpeech? = null
    private var engine: String? = null

    /** Says [text] with [voice] of [engine]; returns when it is spoken or failed. Cancelling stops it. */
    suspend fun say(engine: String?, voice: String, locale: Locale, text: String, rate: Float, pitch: Float) = withContext(Dispatchers.Main) {
        val t = connect(engine) ?: return@withContext
        val v = withContext(Dispatchers.IO) { t.voices.orEmpty() }.firstOrNull { it.name == voice }
        if (v != null) t.voice = v else t.setLanguage(locale)
        t.setSpeechRate(rate)
        t.setPitch(pitch)
        suspendCancellableCoroutine { cont ->
            t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String) {}
                override fun onDone(utteranceId: String) = done()
                override fun onStop(utteranceId: String, interrupted: Boolean) = done()
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String) = done()
                override fun onError(utteranceId: String, errorCode: Int) = done()
                private fun done() {
                    if (cont.isActive) cont.resume(Unit)
                }
            })
            cont.invokeOnCancellation { t.stop() }
            if (t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "folio-preview") != TextToSpeech.SUCCESS) cont.resume(Unit)
        }
    }

    private suspend fun connect(wanted: String?): TextToSpeech? {
        tts?.takeIf { engine == wanted }?.let { return it }
        release()
        val ready = CompletableDeferred<Boolean>()
        val t = TextToSpeech(context, { ready.complete(it == TextToSpeech.SUCCESS) }, wanted)
        if (withTimeoutOrNull(5_000) { ready.await() } != true) {
            t.shutdown()
            return null
        }
        tts = t
        engine = wanted
        return t
    }

    fun release() {
        tts?.shutdown()
        tts = null
        engine = null
    }
}
