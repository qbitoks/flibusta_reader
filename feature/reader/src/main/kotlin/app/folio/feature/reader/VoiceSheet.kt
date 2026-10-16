package app.folio.feature.reader

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folio.core.designsystem.Folio
import app.folio.core.designsystem.FolioType
import app.folio.core.designsystem.ReaderPalette
import app.folio.core.designsystem.components.CircleButton
import app.folio.core.designsystem.components.FolioSheet
import app.folio.core.designsystem.components.GroupHeader
import app.folio.core.designsystem.components.GroupedCard
import app.folio.core.designsystem.components.GroupedRow
import app.folio.core.designsystem.components.SheetHeader
import app.folio.core.speech.PiperVoice
import app.folio.core.speech.SpeechEngineKind
import app.folio.core.speech.VoiceStatus
import app.folio.core.speech.piperVoicesFor

/** «Голос»: system engine or an offline Piper voice for the book's language, with downloads. */
@Composable
internal fun VoiceSheet(vm: ReaderViewModel, palette: ReaderPalette, onDismiss: () -> Unit) {
    val choice by vm.voiceChoice.collectAsStateWithLifecycle()
    val status by vm.voiceStatus.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val locale = vm.speechLocale
    val piper = choice.engine == SpeechEngineKind.Piper
    val voices = piperVoicesFor(locale)
    val chosen = choice.piper[locale.language] ?: voices.firstOrNull { status[it.id] == VoiceStatus.Installed }?.id
    FolioSheet(onDismiss = onDismiss, containerColor = palette.sheet()) {
        SheetHeader(stringResource(R.string.speech_voice), trailing = {
            CircleButton(Icons.Rounded.Close, stringResource(R.string.reader_close), onDismiss)
        })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            GroupHeader(stringResource(R.string.speech_engine))
            GroupedCard {
                GroupedRow(stringResource(R.string.speech_engine_system), trailing = { if (!piper) Check() },
                    onClick = { vm.setSpeechEngine(SpeechEngineKind.System) })
                GroupedRow(stringResource(R.string.speech_engine_piper), trailing = { if (piper) Check() }, showDivider = false,
                    onClick = { vm.setSpeechEngine(SpeechEngineKind.Piper) })
            }
            if (!piper) {
                GroupedCard(Modifier.padding(top = 16.dp)) {
                    GroupedRow(stringResource(R.string.speech_system_settings), chevron = true, showDivider = false,
                        onClick = { openTtsSettings(context) })
                }
            } else {
                GroupHeader(stringResource(R.string.speech_voices, locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }))
                if (voices.isEmpty()) {
                    Note(stringResource(R.string.speech_no_piper_voices))
                } else {
                    GroupedCard {
                        voices.forEachIndexed { i, voice ->
                            VoiceRow(vm, voice, status[voice.id] ?: VoiceStatus.Missing, voice.id == chosen, last = i == voices.lastIndex)
                        }
                    }
                    if (voices.none { status[it.id] == VoiceStatus.Installed }) Note(stringResource(R.string.speech_piper_fallback))
                }
                Note(stringResource(R.string.speech_piper_about))
            }
        }
    }
}

@Composable
private fun VoiceRow(vm: ReaderViewModel, voice: PiperVoice, status: VoiceStatus, selected: Boolean, last: Boolean) {
    val c = Folio.colors
    val context = LocalContext.current
    val size = Formatter.formatShortFileSize(context, voice.bytes)
    val value = when (status) {
        VoiceStatus.Missing -> size
        VoiceStatus.Failed -> stringResource(R.string.speech_voice_failed)
        is VoiceStatus.Downloading -> status.fraction?.let { "${(it * 100).toInt()} %" } ?: stringResource(R.string.speech_voice_unpacking)
        VoiceStatus.Installed -> null
    }
    GroupedRow(
        title = "${voice.speaker} · ${voice.tag}",
        value = value,
        showDivider = !last,
        trailing = {
            when (status) {
                VoiceStatus.Installed -> {
                    if (selected) Check()
                    SmallIcon(Icons.Rounded.DeleteOutline, stringResource(R.string.speech_voice_delete), c.destructive) { vm.deleteVoice(voice) }
                }
                is VoiceStatus.Downloading -> SmallIcon(Icons.Rounded.Close, stringResource(R.string.speech_voice_cancel), c.secondaryLabel) { vm.cancelVoice(voice) }
                else -> Text(stringResource(if (status == VoiceStatus.Failed) R.string.speech_voice_retry else R.string.speech_voice_download), style = FolioType.body, color = c.accent,
                    modifier = Modifier.padding(start = 12.dp))
            }
        },
        onClick = { if (status !is VoiceStatus.Downloading) vm.selectVoice(voice) },
    )
}

@Composable
private fun Check() = Icon(Icons.Rounded.Check, null, tint = Folio.colors.accent, modifier = Modifier.padding(start = 8.dp).size(22.dp))

@Composable
private fun SmallIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, tint: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Box(Modifier.padding(start = 4.dp).size(40.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = androidx.compose.ui.Alignment.Center) {
        Icon(icon, description, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = FolioType.footnote, color = Folio.colors.secondaryLabel,
        modifier = Modifier.padding(start = 36.dp, end = 36.dp, top = 8.dp))
}
