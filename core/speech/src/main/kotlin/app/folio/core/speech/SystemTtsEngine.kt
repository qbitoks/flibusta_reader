package app.folio.core.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Android's [TextToSpeech] with the engine and voice picked in [SpeechVoices] (a Folio engine app, or any
 * other installed one). The phrase after the current one ([prepare]) is queued behind it, so the engine
 * synthesizes it while this one sounds and phrases follow without a pause.
 */
@Singleton
class SystemTtsEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val voices: SpeechVoices,
) : SpeechEngine {
    private var tts: TextToSpeech? = null
    private var ready: CompletableDeferred<TextToSpeech>? = null
    /** What [tts] was created for and set to; a change recreates or re-applies it. */
    private var engine: String? = null
    private var applied: Pair<Locale, String?>? = null
    private val ids = AtomicLong()

    /** Hint from the controller: the phrase it says next. */
    private var ahead: String? = null
    /** The phrase queued behind the current one: its text, rate and utterance id. */
    private var queued: Triple<String, Float, String>? = null

    private val lock = Any()
    private var pending: Pair<String, CancellableContinuation<Unit>>? = null
    /** Utterances that ended before anyone waited for them (a short queued phrase), and how. */
    private val ended = HashMap<String, Exception?>()

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String) {}
        override fun onDone(utteranceId: String) = finish(utteranceId, null)
        override fun onStop(utteranceId: String, interrupted: Boolean) = finish(utteranceId, null)

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String) = finish(utteranceId, SpeechUnavailableException("TTS error"))
        override fun onError(utteranceId: String, errorCode: Int) = finish(utteranceId, SpeechUnavailableException("TTS error $errorCode"))
    }

    private fun finish(id: String, error: Exception?) {
        val cont = synchronized(lock) {
            val (pendingId, cont) = pending ?: run {
                ended[id] = error
                return
            }
            if (pendingId != id) {
                ended[id] = error
                return
            }
            pending = null
            cont
        }
        if (cont.isActive) if (error == null) cont.resume(Unit) else cont.resumeWithException(error)
    }

    private suspend fun engine(): TextToSpeech {
        val wanted = voices.choice.value.engine
        if (wanted != engine) release()
        ready?.let { return it.await() }
        val deferred = CompletableDeferred<TextToSpeech>()
        ready = deferred
        engine = wanted
        var created: TextToSpeech? = null
        created = TextToSpeech(context, { status ->
            val t = created
            if (status == TextToSpeech.SUCCESS && t != null) {
                t.setOnUtteranceProgressListener(listener)
                deferred.complete(t)
            } else {
                deferred.completeExceptionally(SpeechUnavailableException("No TTS engine"))
            }
        }, wanted)
        tts = created
        return try {
            deferred.await()
        } catch (e: SpeechUnavailableException) {
            release()
            throw e
        }
    }

    /** Sets the language and the chosen voice for it, when they changed since the last phrase. */
    private fun apply(t: TextToSpeech, locale: Locale) {
        val voiceName = voices.choice.value.voices[locale.language]
        if (applied == locale to voiceName) return
        val result = t.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            throw SpeechUnavailableException("No voice for $locale")
        }
        voiceName?.let { name -> t.voices?.firstOrNull { it.name == name }?.let { t.voice = it } }
        applied = locale to voiceName
    }

    override fun prepare(text: String, locale: Locale, rate: Float) {
        ahead = text
    }

    override suspend fun speak(text: String, locale: Locale, rate: Float, pitch: Float) {
        val t = engine()
        val next = ahead
        ahead = null
        val id = queued?.takeIf { it.first == text && it.second == rate }?.third ?: run {
            synchronized(lock) { ended.clear() }
            apply(t, locale)
            t.setSpeechRate(rate)
            t.setPitch(pitch)
            enqueue(t, text, TextToSpeech.QUEUE_FLUSH)
        }
        queued = null
        if (next != null && next != text) {
            t.setSpeechRate(rate)
            queued = Triple(next, rate, enqueue(t, next, TextToSpeech.QUEUE_ADD))
        }
        await(t, id)
    }

    private fun enqueue(t: TextToSpeech, text: String, mode: Int): String {
        val id = "folio-${ids.incrementAndGet()}"
        if (t.speak(text, mode, null, id) != TextToSpeech.SUCCESS) throw SpeechUnavailableException("TTS refused the text")
        return id
    }

    private suspend fun await(t: TextToSpeech, id: String) = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation {
            synchronized(lock) {
                pending = null
                ended.clear()
            }
            queued = null
            t.stop()
        }
        val (done, error) = synchronized(lock) {
            if (ended.containsKey(id)) true to ended.remove(id) else {
                pending = id to cont
                false to null
            }
        }
        if (done) if (error == null) cont.resume(Unit) else cont.resumeWithException(error)
    }

    override fun release() {
        tts?.shutdown()
        tts = null
        ready = null
        engine = null
        applied = null
        ahead = null
        queued = null
        synchronized(lock) {
            pending = null
            ended.clear()
        }
    }
}
