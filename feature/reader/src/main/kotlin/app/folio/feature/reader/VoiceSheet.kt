package app.folio.feature.reader

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folio.core.data.modules.InstallState
import app.folio.core.data.modules.ModuleApk
import app.folio.core.designsystem.Folio
import app.folio.core.designsystem.FolioType
import app.folio.core.designsystem.ReaderPalette
import app.folio.core.designsystem.components.CircleButton
import app.folio.core.designsystem.components.FolioSheet
import app.folio.core.designsystem.components.GroupHeader
import app.folio.core.designsystem.components.GroupedCard
import app.folio.core.designsystem.components.GroupedRow
import app.folio.core.designsystem.components.SheetHeader

/**
 * «Голос»: every voice for the book's language from every installed engine, grouped by engine; a tap reads with
 * it. Below, the Folio engine modules to install, update or open for more voices. Nothing picked = system voice.
 */
@Composable
internal fun VoiceSheet(vm: ReaderViewModel, palette: ReaderPalette, onDismiss: () -> Unit) {
    val choice by vm.voiceChoice.collectAsStateWithLifecycle()
    val groups by vm.engineVoices.collectAsStateWithLifecycle()
    val installed by vm.installedModules.collectAsStateWithLifecycle()
    val locale = vm.speechLocale
    // Re-read voices when an engine is installed or removed, and on return from an engine's voice screen.
    LifecycleResumeEffect(Unit) {
        vm.refreshModules()
        vm.loadEngineVoices()
        onPauseOrDispose {}
    }
    LaunchedEffect(installed) { vm.loadEngineVoices() }
    FolioSheet(onDismiss = onDismiss, containerColor = palette.sheet()) {
        SheetHeader(stringResource(R.string.speech_voice), trailing = {
            CircleButton(Icons.Rounded.Close, stringResource(R.string.reader_close), onDismiss)
        })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            val list = groups
            val chosen = choice.voices[locale.language]
            when {
                list == null -> Box(Modifier.fillMaxWidth().padding(24.dp)) {
                    CircularProgressIndicator(Modifier.size(24.dp).align(androidx.compose.ui.Alignment.Center), color = Folio.colors.accent, strokeWidth = 2.dp)
                }
                list.isEmpty() -> Note(stringResource(R.string.speech_no_voices_yet))
                else -> list.forEach { (engine, voices) ->
                    GroupHeader(engine.label)
                    GroupedCard {
                        voices.forEachIndexed { i, v ->
                            val selected = choice.engine == engine.packageName && v.name == chosen
                            GroupedRow(v.title ?: stringResource(R.string.speech_voice_number, v.speaker),
                                value = when {
                                    v.title != null -> null
                                    v.needsNetwork -> stringResource(R.string.speech_voice_online)
                                    else -> null
                                },
                                trailing = { if (selected) Check() }, showDivider = i != voices.lastIndex,
                                onClick = { vm.setSpeechVoice(engine, v) })
                        }
                    }
                }
            }
            ModulesGroup(vm, installed)
        }
    }
}

/** The Folio engine apps from the modules repository: install, update, open. */
@Composable
private fun ModulesGroup(vm: ReaderViewModel, installed: Map<String, Long>) {
    val modules by vm.modules.collectAsStateWithLifecycle()
    val states by vm.moduleStates.collectAsStateWithLifecycle()
    val context = LocalContext.current
    GroupHeader(stringResource(R.string.speech_modules))
    if (modules.isEmpty()) {
        Note(stringResource(R.string.speech_modules_loading))
        return
    }
    GroupedCard {
        modules.forEachIndexed { i, m ->
            val version = installed[m.packageName]
            val state = states[m.packageName] ?: InstallState.Idle
            GroupedRow(
                title = m.title,
                value = moduleValue(context, m, version, state),
                showDivider = i != modules.lastIndex,
                trailing = { ModuleAction(m, version, state) },
                onClick = {
                    when {
                        state is InstallState.Downloading -> vm.cancelModule(m)
                        version == null || version < m.versionCode -> vm.installModule(m)
                        else -> openEngineVoices(context, m.packageName)
                    }
                },
            )
        }
    }
    Note(stringResource(R.string.speech_modules_about))
}

@Composable
private fun moduleValue(context: Context, m: ModuleApk, version: Long?, state: InstallState): String? = when (state) {
    is InstallState.Downloading -> state.fraction?.let { "${(it * 100).toInt()} %" } ?: stringResource(R.string.speech_module_downloading)
    InstallState.Installing -> stringResource(R.string.speech_module_installing)
    InstallState.NeedsPermission -> stringResource(R.string.speech_module_permission)
    is InstallState.Failed -> stringResource(R.string.speech_module_failed)
    InstallState.Idle -> when {
        version == null -> Formatter.formatShortFileSize(context, m.bytes)
        version < m.versionCode -> m.versionName
        else -> null
    }
}

@Composable
private fun ModuleAction(m: ModuleApk, version: Long?, state: InstallState) {
    val c = Folio.colors
    val label = when {
        state is InstallState.Downloading -> R.string.speech_voice_cancel
        state is InstallState.Installing -> null
        state is InstallState.Failed -> R.string.speech_voice_retry
        version == null -> R.string.speech_module_install
        version < m.versionCode -> R.string.speech_module_update
        else -> R.string.speech_module_voices
    }
    if (label != null) {
        Text(stringResource(label), style = FolioType.body, color = c.accent, modifier = Modifier.padding(start = 12.dp))
    }
}

/** A Folio engine's voice screen (download voices, default voice). */
private fun openEngineVoices(context: Context, packageName: String) {
    runCatching {
        context.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).setPackage(packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
private fun Check() = Icon(Icons.Rounded.Check, null, tint = Folio.colors.accent, modifier = Modifier.padding(start = 8.dp).size(22.dp))

@Composable
private fun Note(text: String) {
    Text(text, style = FolioType.footnote, color = Folio.colors.secondaryLabel,
        modifier = Modifier.padding(start = 36.dp, end = 36.dp, top = 8.dp))
}
