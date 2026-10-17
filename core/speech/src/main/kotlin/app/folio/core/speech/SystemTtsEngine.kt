package app.folio.core.speech

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
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
 * synthesizes it while this one sounds and phrases follow without a pause. A chosen online voice reads with its
 * on-device twin while there is no network or after it failed (for a minute), and returns on its own.
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
    /** [tts]'s voices, read once per engine connection. */
    private var known: Set<Voice>? = null
    /** The voice being applied needs the network (no twin replaced it). */
    private var usingNetwork = false
    /** Until then the online voice stays replaced: it failed a moment ago. */
    private var fallbackUntil = 0L
    private val ids = AtomicLong()

    /** Hint from the controller: the phrase it says next. */
    private var ahead: String? = null
    /** The phrase queued behind the current one. */
    private var queued: Queued? = null

    private data class Queued(val text: String, val rate: Float, val pitch: Float, val id: String)

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

    /**
     * The voice to read [locale] with now: the chosen one, or its on-device twin while an online voice can't be
     * reached. Tells [SpeechVoices.offline] which it is.
     */
    private fun effectiveVoice(t: TextToSpeech, locale: Locale): String? {
        val chosen = voices.choice.value.voices[locale.language]
        val all = known ?: t.voices.orEmpty().also { known = it }
        val voice = chosen?.let { name -> all.firstOrNull { it.name == name } }
        val needsNetwork = voice?.isNetworkConnectionRequired == true
        val twin = if (needsNetwork && (System.currentTimeMillis() < fallbackUntil || !online())) {
            all.firstOrNull { !it.isNetworkConnectionRequired && speakerKey(it.name) == speakerKey(chosen!!) }
        } else null
        voices.setOffline(twin != null)
        usingNetwork = needsNetwork && twin == null
        return twin?.name ?: chosen
    }

    private fun online(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        return cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    }

    /** Sets the language and [voiceName] for it, when they changed since the last phrase. */
    private fun apply(t: TextToSpeech, locale: Locale, voiceName: String?) {
        if (applied == locale to voiceName) return
        val result = t.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            throw SpeechUnavailableException("No voice for $locale")
        }
        voiceName?.let { name -> known?.firstOrNull { it.name == name }?.let { t.voice = it } }
        applied = locale to voiceName
    }

    override fun prepare(text: String, locale: Locale, rate: Float) {
        ahead = text
    }

    override suspend fun speak(text: String, locale: Locale, rate: Float, pitch: Float) {
        val t = engine()
        val next = ahead
        ahead = null
        val voice = effectiveVoice(t, locale)
        // The queued phrase is reused only if it was synthesized as this one would be now (a voice switch flushes it).
        val id = queued?.takeIf { it.text == text && it.rate == rate && it.pitch == pitch && applied == locale to voice }?.id ?: run {
            synchronized(lock) { ended.clear() }
            apply(t, locale, voice)
            t.setSpeechRate(rate)
            t.setPitch(pitch)
            enqueue(t, text, TextToSpeech.QUEUE_FLUSH)
        }
        queued = null
        if (next != null && next != text) {
            queued = Queued(next, rate, pitch, enqueue(t, next, TextToSpeech.QUEUE_ADD))
        }
        try {
            await(t, id)
        } catch (e: SpeechUnavailableException) {
            // An online voice that failed (network lost mid-phrase): say the phrase again with its twin.
            if (!usingNetwork || System.currentTimeMillis() < fallbackUntil) throw e
            fallbackUntil = System.currentTimeMillis() + FALLBACK_MS
            queued = null
            ahead = next
            speak(text, locale, rate, pitch)
        }
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
        known = null
        ahead = null
        queued = null
        synchronized(lock) {
            pending = null
            ended.clear()
        }
    }

    private companion object {
        const val FALLBACK_MS = 60_000L
    }
}
