package app.folio.feature.reader

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.folio.core.data.modules.InstallState
import app.folio.core.data.modules.ModuleApk
import app.folio.core.data.modules.ModuleInstaller
import app.folio.core.speech.SpeechController
import app.folio.core.speech.SpeechState
import app.folio.core.speech.SpeechVoices
import app.folio.core.speech.TtsEngine
import app.folio.core.speech.TtsVoice
import app.folio.core.speech.VoiceChoice
import app.folio.core.speech.VoicePreview
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale
import javax.inject.Inject

/** An installed engine and its speakers for the book's language; Folio engines are listed even with no voice yet. */
internal data class EngineVoices(val engine: TtsEngine, val voices: List<TtsVoice>)

/** The «Голос» sheet: engines and voices, voice samples, Folio engine modules, rate/pitch/sleep timer. */
@HiltViewModel
internal class VoiceViewModel @Inject constructor(
    private val speech: SpeechController,
    private val voices: SpeechVoices,
    private val preview: VoicePreview,
    private val installer: ModuleInstaller,
) : ViewModel() {
    val choice: StateFlow<VoiceChoice> = voices.choice
    /** The chosen online voice reads with its on-device twin right now. */
    val offline: StateFlow<Boolean> = voices.offline
    val speechState: StateFlow<SpeechState> = speech.state

    val modules: StateFlow<List<ModuleApk>> = installer.engines
    val moduleStates: StateFlow<Map<String, InstallState>> = installer.states
    val installedModules: StateFlow<Map<String, Long>> = installer.installed

    private val _engines = MutableStateFlow<List<EngineVoices>?>(null)
    /** Installed engines by name; null while loading. */
    val engines: StateFlow<List<EngineVoices>?> = _engines.asStateFlow()
    private var enginesJob: Job? = null

    private val _previewing = MutableStateFlow<String?>(null)
    /** The voice name a sample is being said with. */
    val previewing: StateFlow<String?> = _previewing.asStateFlow()
    private var previewJob: Job? = null

    fun loadEngines(locale: Locale) {
        enginesJob?.cancel()
        enginesJob = viewModelScope.launch {
            val collator = Collator.getInstance(Locale.getDefault())
            _engines.value = voices.engines()
                .map { EngineVoices(it, voices.voices(it.packageName, locale)) }
                .filter { it.voices.isNotEmpty() || it.engine.packageName.startsWith(FOLIO_ENGINES) }
                .sortedWith(compareBy(collator) { it.engine.label })
        }
    }

    /** Reads [locale]'s language with [voice] of [engine] from the current phrase on. */
    fun select(engine: String, voice: String, locale: Locale) {
        if (voices.choice.value.engine != engine) voices.setEngine(engine)
        voices.setVoice(locale.language, voice)
        speech.voiceChanged()
    }

    /** Says [sample] with [voice]; a second tap on the same voice stops it. Reading aloud pauses first. */
    fun preview(engine: String, voice: String, locale: Locale, sample: String) {
        val again = _previewing.value == voice
        previewJob?.cancel()
        if (again) return
        if (speech.state.value.playing) speech.pause()
        _previewing.value = voice
        previewJob = viewModelScope.launch {
            try {
                val s = speech.state.value
                preview.say(engine, voice, locale, sample, s.rate, s.pitch)
            } finally {
                if (_previewing.value == voice) _previewing.value = null
            }
        }
    }

    /** The sheet closed: stop a sample and free its engine. */
    fun stopPreview() {
        previewJob?.cancel()
        preview.release()
    }

    private val _expanded = MutableStateFlow<Set<String>>(emptySet())
    /** Engine groups the user opened; all start folded. Kept here so a recomposition can't fold them back. */
    val expanded: StateFlow<Set<String>> = _expanded.asStateFlow()

    fun toggleGroup(engine: String) {
        _expanded.value = if (engine in _expanded.value) _expanded.value - engine else _expanded.value + engine
    }

    /** An engine installed while the sheet is open opens, to download a voice right away. */
    fun expand(engines: Set<String>) {
        _expanded.value = _expanded.value + engines
    }

    fun setRate(rate: Float) = speech.setRate(rate)
    fun setPitch(pitch: Float) = speech.setPitch(pitch)
    fun sleepIn(minutes: Int?) = speech.sleepIn(minutes)
    fun sleepAtChapterEnd() = speech.sleepAtChapterEnd()

    /** Re-reads installed engines and, at most a few times a day, the modules index. */
    fun refreshModules() {
        installer.refreshInstalled()
        viewModelScope.launch {
            try {
                installer.refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("VoiceViewModel", "Modules index not refreshed", e)
            }
        }
    }

    fun installModule(module: ModuleApk) = installer.install(module)
    fun cancelModule(module: ModuleApk) = installer.cancel(module.packageName)

    override fun onCleared() {
        previewJob?.cancel()
        preview.release()
    }

    private companion object {
        const val FOLIO_ENGINES = "app.folio.tts."
    }
}
