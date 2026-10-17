package app.folio.core.speech

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.thread

/** The user's engine and voice choice, in `shared_prefs/speech.xml`. */
data class VoiceChoice(
    /** Package of the TTS engine; null = the system default engine. */
    val engine: String? = null,
    /** Language (`ru`) → voice name of [engine]; absent = the engine's default voice. */
    val voices: Map<String, String> = emptyMap(),
)

/** An installed text-to-speech engine app (Folio Piper, Folio Silero, Google, RHVoice…). */
data class TtsEngine(val packageName: String, val label: String)

/**
 * One speaker of an engine: its on-device ([local]) and online ([network]) voices as `TextToSpeech.getVoices()`
 * names them, at least one set. [title] is the speaker's name for Folio engines; other engines name voices by codes
 * (`ru-ru-x-dfc-network`), so they get [speaker], a number per distinct speaker.
 */
data class TtsVoice(val local: String?, val network: String?, val locale: Locale, val title: String?, val speaker: Int) {
    val names: List<String> get() = listOfNotNull(local, network)
    /** Stable id of the speaker (favorites): its on-device name, else the online one. */
    val key: String get() = local ?: network!!
    /** The voice to read with: online when asked for and there is one, else on-device. */
    fun variant(online: Boolean): String = if (online) network ?: local!! else local ?: network!!
}

/** A voice the user starred, from any engine. */
data class FavoriteVoice(val engine: String, val voice: String)

@Singleton
class SpeechVoices @Inject constructor(@ApplicationContext private val context: Context) {
    private val prefs = context.getSharedPreferences("speech", Context.MODE_PRIVATE)
    private val _choice = MutableStateFlow(read())
    val choice: StateFlow<VoiceChoice> = _choice.asStateFlow()

    private val _favorites = MutableStateFlow(readFavorites())
    val favorites: StateFlow<Set<FavoriteVoice>> = _favorites.asStateFlow()

    /** Set by [SystemTtsEngine]: the chosen online voice is replaced by its on-device twin (no network). */
    private val _offline = MutableStateFlow(false)
    val offline: StateFlow<Boolean> = _offline.asStateFlow()

    internal fun setOffline(offline: Boolean) {
        _offline.value = offline
    }

    init {
        dropBuiltInVoices()
    }

    /** Stars or unstars [voice] (a [TtsVoice.key]) of [engine]. */
    fun toggleFavorite(engine: String, voice: String) {
        val f = FavoriteVoice(engine, voice)
        val next = if (f in _favorites.value) _favorites.value - f else _favorites.value + f
        prefs.edit().putStringSet(KEY_FAVORITES, next.map { it.engine + '	' + it.voice }.toSet()).apply()
        _favorites.value = next
    }

    /** Reads with [engine] (null = the system default); its voices are chosen per language again. */
    fun setEngine(engine: String?) {
        val edit = prefs.edit()
        prefs.all.keys.filter { it.startsWith(KEY_VOICE) }.forEach(edit::remove)
        if (engine == null) edit.remove(KEY_ENGINE) else edit.putString(KEY_ENGINE, engine)
        edit.apply()
        _choice.value = VoiceChoice(engine)
    }

    /** Makes [voice] the one to read [language] with. */
    fun setVoice(language: String, voice: String) {
        prefs.edit().putString(KEY_VOICE + language, voice).apply()
        _choice.value = _choice.value.copy(voices = _choice.value.voices + (language to voice))
    }

    /** Engine apps on the device that implement Android's TTS service. */
    fun engines(): List<TtsEngine> {
        val pm = context.packageManager
        return pm.queryIntentServices(Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), 0)
            .map { TtsEngine(it.serviceInfo.packageName, it.loadLabel(pm).toString()) }
            .distinctBy { it.packageName }
            .sortedWith(compareByDescending<TtsEngine> { it.packageName.startsWith(FOLIO_ENGINES) }.thenBy { it.label })
    }

    /**
     * Voices [engine] offers for [locale]'s language, the book's region first; installed (offline) ones only
     * when the engine marks the rest «not installed». Connects to the engine for a moment; empty on failure.
     */
    suspend fun voices(engine: String?, locale: Locale): List<TtsVoice> = withContext(Dispatchers.Main) {
        val ready = CompletableDeferred<Boolean>()
        val tts = TextToSpeech(context, { ready.complete(it == TextToSpeech.SUCCESS) }, engine)
        try {
            if (withTimeoutOrNull(5_000) { ready.await() } != true) return@withContext emptyList()
            val all = withContext(Dispatchers.IO) { tts.voices.orEmpty() }
            describeVoices(
                all.filter { it.locale.language == locale.language && TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features }
                    .map { Triple(it.name, it.locale, it.isNetworkConnectionRequired) },
                locale,
            )
        } finally {
            tts.shutdown()
        }
    }

    private fun readFavorites(): Set<FavoriteVoice> = prefs.getStringSet(KEY_FAVORITES, null).orEmpty()
        .mapNotNull { line -> line.split('	').takeIf { it.size == 2 }?.let { FavoriteVoice(it[0], it[1]) } }.toSet()

    private fun read(): VoiceChoice {
        val voices = prefs.all.mapNotNull { (key, value) ->
            if (key.startsWith(KEY_VOICE) && value is String) key.removePrefix(KEY_VOICE) to value else null
        }.toMap()
        return VoiceChoice(prefs.getString(KEY_ENGINE, null), voices)
    }

    /**
     * Piper and Silero lived inside Folio up to 0.5: their prefs and `files/voices/` (hundreds of MB) are
     * dead now that the engines are separate apps, which download their own voices.
     */
    private fun dropBuiltInVoices() {
        val old = prefs.all.keys.filter { it == "engine" || it.startsWith("piper_") || it == "silero_speaker" }
        if (old.isNotEmpty()) prefs.edit().apply { old.forEach(::remove) }.apply()
        val dir = File(context.filesDir, "voices")
        if (dir.exists()) thread(name = "drop-voices") { dir.deleteRecursively() }
    }

    private companion object {
        const val KEY_ENGINE = "engine_package"
        const val KEY_VOICE = "voice_"
        const val KEY_FAVORITES = "favorites"
        const val FOLIO_ENGINES = "app.folio.tts."
    }
}

/**
 * [voices] (name, locale, needs network) for the reader's list, one entry per speaker: the book's region first, then
 * by speaker. A speaker's `-local` and `-network` voices become one [TtsVoice]. Folio engines' `piper-ru_RU-irina-medium`
 * / `silero-ru_dmitriy` are titled «Irina» / «Dmitriy»; other speakers are numbered.
 */
fun describeVoices(voices: List<Triple<String, Locale, Boolean>>, book: Locale): List<TtsVoice> {
    fun cap(s: String) = s.replaceFirstChar { it.titlecase(Locale.ROOT) }
    fun title(name: String) = when {
        name.startsWith("piper-") -> name.removePrefix("piper-").split('-').getOrNull(1)?.replace('_', ' ')?.let(::cap)
        name.startsWith("silero-") -> cap(name.removePrefix("silero-").substringAfter('_'))
        else -> null
    }
    val sorted = voices.sortedWith(
        compareByDescending<Triple<String, Locale, Boolean>> { it.second.country.equals(book.country, ignoreCase = true) }
            .thenBy { speakerKey(it.first) },
    )
    return sorted.groupBy { speakerKey(it.first) }.values.mapIndexed { i, group ->
        val local = group.firstOrNull { !it.third }?.first
        val network = group.firstOrNull { it.third }?.first
        TtsVoice(local, network, group.first().second, title(local ?: network!!), i + 1)
    }
}

/** A voice name without its `-local` / `-network` ending: the speaker both variants share. */
internal fun speakerKey(name: String) = name.lowercase().removeSuffix("-local").removeSuffix("-network")
