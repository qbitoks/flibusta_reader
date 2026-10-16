package app.folio.core.speech

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

enum class SpeechEngineKind(val key: String) { System("system"), Piper("piper") }

/** The user's engine and voice choice, in `shared_prefs/speech.xml`. */
data class VoiceChoice(
    val engine: SpeechEngineKind = SpeechEngineKind.System,
    /** Language (`ru`) → Piper voice id. */
    val piper: Map<String, String> = emptyMap(),
)

@Singleton
class SpeechVoices @Inject constructor(
    @ApplicationContext context: Context,
    val store: VoiceStore,
) {
    private val prefs = context.getSharedPreferences("speech", Context.MODE_PRIVATE)
    private val _choice = MutableStateFlow(read())
    val choice: StateFlow<VoiceChoice> = _choice.asStateFlow()

    fun setEngine(engine: SpeechEngineKind) {
        prefs.edit().putString(KEY_ENGINE, engine.key).apply()
        _choice.value = _choice.value.copy(engine = engine)
    }

    /** Makes [voice] the Piper voice for its language and Piper the engine. */
    fun select(voice: PiperVoice) {
        prefs.edit().putString(KEY_ENGINE, SpeechEngineKind.Piper.key).putString(KEY_PIPER + voice.language, voice.id).apply()
        _choice.value = VoiceChoice(SpeechEngineKind.Piper, _choice.value.piper + (voice.language to voice.id))
    }

    /** The installed Piper voice to read [locale] with: the chosen one, else any installed for the language. */
    fun piperVoiceFor(locale: Locale): PiperVoice? {
        val chosen = _choice.value.piper[locale.language]?.let(::piperVoice)?.takeIf { store.installed(it.id) }
        return chosen ?: piperVoicesFor(locale).firstOrNull { store.installed(it.id) }
    }

    /** Piper is picked and has a voice for [locale]; otherwise the system engine reads. */
    fun usesPiper(locale: Locale): Boolean = _choice.value.engine == SpeechEngineKind.Piper && piperVoiceFor(locale) != null

    private fun read(): VoiceChoice {
        val engine = SpeechEngineKind.entries.firstOrNull { it.key == prefs.getString(KEY_ENGINE, null) } ?: SpeechEngineKind.System
        val piper = prefs.all.mapNotNull { (key, value) ->
            if (key.startsWith(KEY_PIPER) && value is String) key.removePrefix(KEY_PIPER) to value else null
        }.toMap()
        return VoiceChoice(engine, piper)
    }

    private companion object {
        const val KEY_ENGINE = "engine"
        const val KEY_PIPER = "piper_"
    }
}
