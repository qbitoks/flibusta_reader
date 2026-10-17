package app.folio.feature.reader

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folio.core.designsystem.Folio
import app.folio.core.designsystem.FolioType
import app.folio.core.designsystem.ReaderPalette
import app.folio.core.designsystem.components.CircleButton
import app.folio.core.designsystem.components.FolioSheet
import app.folio.core.designsystem.components.GroupHeader
import app.folio.core.designsystem.components.GroupedCard
import app.folio.core.designsystem.components.GroupedRow
import app.folio.core.designsystem.components.IosSlider
import app.folio.core.designsystem.components.SegmentedControl
import app.folio.core.designsystem.components.SheetHeader
import app.folio.core.speech.SleepTimer
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * «Голос»: the voice reading now, rate and pitch, the sleep timer, starred voices, then every engine by name with its
 * voices and, for Folio engines, voice downloads and updates; Folio engines not installed yet come last.
 */
@Composable
internal fun VoiceSheet(reader: ReaderViewModel, palette: ReaderPalette, onDismiss: () -> Unit, vm: VoiceViewModel = hiltViewModel()) {
    val locale = reader.speechLocale
    val defaultSample = stringResource(R.string.speech_sample)
    val sample = remember { reader.speechSample() ?: defaultSample }
    val installed by vm.installedModules.collectAsStateWithLifecycle()
    // Re-read voices when an engine is installed or removed, and on return from an engine's voice screen.
    LifecycleResumeEffect(Unit) {
        vm.refreshModules()
        vm.loadEngines(locale)
        onPauseOrDispose {}
    }
    LaunchedEffect(installed) { vm.loadEngines(locale) }
    DisposableEffect(Unit) { onDispose { vm.stopPreview() } }
    FolioSheet(onDismiss = onDismiss, containerColor = palette.sheet()) {
        SheetHeader(stringResource(R.string.speech_voice), trailing = {
            CircleButton(Icons.Rounded.Close, stringResource(R.string.reader_close), onDismiss)
        })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            val list by vm.engines.collectAsStateWithLifecycle()
            val current = list
            if (current == null) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), color = Folio.colors.accent, strokeWidth = 2.dp)
                }
            } else {
                NowReading(vm, current, locale, sample)
                RateAndPitch(vm)
                SleepTimerGroup(vm)
                EngineGroups(vm, current, installed, locale, sample)
            }
        }
    }
}

/** The voice reading now (engine, on device / online / offline now), with its sample. */
@Composable
private fun NowReading(vm: VoiceViewModel, engines: List<EngineVoices>, locale: Locale, sample: String) {
    val choice by vm.choice.collectAsStateWithLifecycle()
    val offline by vm.offline.collectAsStateWithLifecycle()
    val previewing by vm.previewing.collectAsStateWithLifecycle()
    val chosen = choice.voices[locale.language]
    val group = engines.firstOrNull { it.engine.packageName == choice.engine }
    val voice = group?.voices?.firstOrNull { chosen in it.names }
    Spacer(Modifier.height(8.dp))
    GroupedCard {
        if (group == null || voice == null || chosen == null) {
            GroupedRow(stringResource(R.string.speech_now_system), showDivider = false)
        } else {
            val variant = when {
                chosen != voice.network -> stringResource(R.string.speech_variant_device)
                offline -> stringResource(R.string.speech_variant_offline_now)
                else -> stringResource(R.string.speech_voice_online)
            }
            GroupedRow(voiceTitle(voice), showDivider = false,
                leading = { PreviewButton(previewing == chosen) { vm.preview(group.engine.packageName, chosen, locale, sample) } },
                value = stringResource(R.string.speech_now_engine, group.engine.label, variant), valueStyle = FolioType.subheadline)
        }
    }
}

/** Rate and pitch sliders; a change applies when the finger lifts (each one restarts the phrase). */
@Composable
private fun RateAndPitch(vm: VoiceViewModel) {
    val state by vm.speechState.collectAsStateWithLifecycle()
    GroupedCard(Modifier.padding(top = 16.dp)) {
        SpeechSlider(stringResource(R.string.speech_rate), state.rate, 0.5f..2f, { stringResource(R.string.speech_rate_value, formatRate(it)) }, vm::setRate)
        SpeechSlider(stringResource(R.string.speech_pitch), state.pitch, 0.5f..1.5f, { formatRate(it) }, vm::setPitch)
        if (abs(state.rate - 1f) > 0.01f || abs(state.pitch - 1f) > 0.01f) {
            Text(stringResource(R.string.speech_reset), style = FolioType.body, color = Folio.colors.accent,
                modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) {
                    vm.setRate(1f)
                    vm.setPitch(1f)
                }.padding(horizontal = 18.dp, vertical = 14.dp))
        }
    }
}

@Composable
private fun SpeechSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, format: @Composable (Float) -> String, onDone: (Float) -> Unit) {
    var dragged by remember(value) { mutableFloatStateOf(value) }
    Column(Modifier.padding(start = 18.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = FolioType.body, color = Folio.colors.label, modifier = Modifier.weight(1f))
            Text(format(dragged), style = FolioType.body, color = Folio.colors.secondaryLabel)
        }
        IosSlider(dragged, { dragged = it }, Modifier.fillMaxWidth().padding(top = 4.dp), valueRange = range, steps = 0.05f,
            onValueChangeFinished = { if (abs(dragged - value) > 0.001f) onDone(dragged) },
            onValueChangeCancelled = { dragged = value }, contentDescription = label)
    }
}

/** Off / 15 / 30 / 60 min / end of chapter, and when reading stops. */
@Composable
private fun SleepTimerGroup(vm: VoiceViewModel) {
    val state by vm.speechState.collectAsStateWithLifecycle()
    val sleep = state.sleep
    val minutes = listOf(15, 30, 60)
    val options = listOf(stringResource(R.string.speech_sleep_off)) +
        minutes.map { stringResource(R.string.speech_sleep_minutes, it) } + stringResource(R.string.speech_sleep_chapter)
    val selected = when {
        sleep == null -> 0
        sleep.chapter != null -> options.lastIndex
        else -> minutes.indexOf(sleep.minutes).let { if (it < 0) 0 else it + 1 }
    }
    GroupHeader(stringResource(R.string.speech_sleep))
    SegmentedControl(options, selected, onSelect = { i ->
        when (i) {
            0 -> vm.sleepIn(null)
            options.lastIndex -> vm.sleepAtChapterEnd()
            else -> vm.sleepIn(minutes[i - 1])
        }
    }, modifier = Modifier.padding(horizontal = 16.dp), role = Role.RadioButton)
    sleepNote(sleep)?.let { VoiceNote(it) }
}

@Composable
private fun sleepNote(sleep: SleepTimer?): String? {
    val context = LocalContext.current
    return when {
        sleep == null -> null
        sleep.chapter != null -> stringResource(R.string.speech_sleep_chapter_note)
        else -> sleep.endsAt?.let { stringResource(R.string.speech_sleep_until, DateFormat.getTimeFormat(context).format(Date(it))) }
    }
}

@Composable
internal fun VoiceNote(text: String) {
    Text(text, style = FolioType.footnote, color = Folio.colors.secondaryLabel,
        modifier = Modifier.padding(start = 36.dp, end = 36.dp, top = 8.dp))
}

/** «▶» / «■» before a voice: says the sample, or stops it. */
@Composable
internal fun PreviewButton(playing: Boolean, onClick: () -> Unit) {
    val description = stringResource(if (playing) R.string.speech_preview_stop else R.string.speech_preview)
    Box(Modifier.padding(end = 10.dp).size(32.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center) {
        Icon(if (playing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, description, tint = Folio.colors.accent, modifier = Modifier.size(22.dp))
    }
}
