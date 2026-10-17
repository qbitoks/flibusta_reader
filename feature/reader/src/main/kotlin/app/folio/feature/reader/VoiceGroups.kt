package app.folio.feature.reader

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folio.core.data.modules.InstallState
import app.folio.core.data.modules.ModuleApk
import app.folio.core.designsystem.Folio
import app.folio.core.designsystem.FolioType
import app.folio.core.designsystem.components.GroupedCard
import app.folio.core.designsystem.components.GroupedRow
import app.folio.core.designsystem.components.SegmentedControl
import app.folio.core.speech.TtsVoice
import java.util.Locale

/** The speaker's name for Folio engines, «Голос N» for code-named ones. */
@Composable
internal fun voiceTitle(voice: TtsVoice): String = voice.title ?: stringResource(R.string.speech_voice_number, voice.speaker)

/**
 * Starred voices, then installed engines by name (the one reading now open, the rest folded), then Folio engines
 * not installed yet. Only the reading engine's group starts open; an engine installed while the sheet is open opens.
 */
@Composable
internal fun EngineGroups(vm: VoiceViewModel, engines: List<EngineVoices>, installed: Map<String, Long>, locale: Locale, sample: String) {
    val choice by vm.choice.collectAsStateWithLifecycle()
    val modules by vm.modules.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(setOfNotNull(choice.engine ?: engines.firstOrNull()?.engine?.packageName)) }
    var known by remember { mutableStateOf(installed.keys) }
    LaunchedEffect(installed) {
        open = open + (installed.keys - known)
        known = installed.keys
    }
    val chosen = choice.voices[locale.language]
    fun selected(engine: String, v: TtsVoice) = choice.engine == engine && chosen in v.names

    val starred = engines.flatMap { g -> g.voices.filter { v -> favorites.any { it.engine == g.engine.packageName && it.voice == v.key } }.map { g to it } }
    if (starred.isNotEmpty()) {
        SectionHeader(stringResource(R.string.speech_favorites))
        GroupedCard {
            starred.forEachIndexed { i, (g, v) ->
                VoiceRow(vm, g.engine.packageName, v, selected(g.engine.packageName, v), chosen, locale, sample,
                    engineLabel = g.engine.label, showDivider = i != starred.lastIndex)
            }
        }
    }

    engines.forEach { g ->
        val pkg = g.engine.packageName
        val module = modules.firstOrNull { it.packageName == pkg }
        val expanded = pkg in open
        val count = if (g.voices.isEmpty()) null else pluralStringResource(R.plurals.speech_voices_count, g.voices.size, g.voices.size)
        SectionHeader(g.engine.label, value = if (expanded) null else count, expanded = expanded) {
            open = if (expanded) open - pkg else open + pkg
        }
        if (!expanded) return@forEach
        val version = installed[pkg]
        val update = module?.takeIf { version != null && version < it.versionCode }
        val more = module != null
        GroupedCard {
            if (g.voices.isEmpty()) {
                GroupedRow(stringResource(R.string.speech_engine_no_voices), titleColor = Folio.colors.secondaryLabel, showDivider = more)
            }
            g.voices.forEachIndexed { i, v ->
                VoiceRow(vm, pkg, v, selected(pkg, v), chosen, locale, sample, showDivider = i != g.voices.lastIndex || more)
            }
            if (module != null) {
                val context = LocalContext.current
                GroupedRow(stringResource(R.string.speech_download_voices), titleColor = Folio.colors.accent, chevron = true,
                    showDivider = update != null, onClick = { openEngineVoices(context, pkg) })
            }
            if (update != null) ModuleRow(vm, update, stringResource(R.string.speech_update_to, update.versionName), showDivider = false)
        }
    }

    val missing = modules.filter { it.packageName !in installed }.sortedBy { it.title.lowercase() }
    if (engines.all { it.voices.isEmpty() } && starred.isEmpty()) VoiceNote(stringResource(R.string.speech_no_voices_yet))
    missing.forEach { m ->
        val context = LocalContext.current
        SectionHeader(m.title, value = Formatter.formatShortFileSize(context, m.bytes))
        GroupedCard {
            if (m.description.isNotBlank()) {
                Text(m.description, style = FolioType.subheadline, color = Folio.colors.secondaryLabel,
                    modifier = Modifier.padding(start = 18.dp, end = 16.dp, top = 12.dp, bottom = 4.dp))
            }
            ModuleRow(vm, m, stringResource(R.string.speech_module_install), showDivider = false)
        }
    }
    if (favorites.isEmpty() && engines.any { it.voices.isNotEmpty() }) VoiceNote(stringResource(R.string.speech_favorites_hint))
}

/**
 * One speaker: «▶» sample, name, a check when it reads. A tap reads with it, a long press stars it. The chosen speaker
 * with both an on-device and an online voice shows «На устройстве | Онлайн» under it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VoiceRow(
    vm: VoiceViewModel, engine: String, v: TtsVoice, selected: Boolean, chosen: String?, locale: Locale, sample: String,
    engineLabel: String? = null, showDivider: Boolean,
) {
    val previewing by vm.previewing.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val starred = favorites.any { it.engine == engine && it.voice == v.key }
    // The voice a tap or «▶» uses: the chosen variant of this speaker, else on-device.
    val name = if (selected && chosen != null) chosen else v.variant(online = false)
    val added = stringResource(R.string.speech_favorite_added)
    val removed = stringResource(R.string.speech_favorite_removed)
    val value = when {
        engineLabel != null -> engineLabel
        v.local == null -> stringResource(R.string.speech_voice_online)
        else -> null
    }
    GroupedRow(
        voiceTitle(v),
        modifier = Modifier.combinedClickable(
            onClick = { vm.select(engine, name, locale) },
            onLongClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                vm.toggleFavorite(engine, v)
                Toast.makeText(context, if (starred) removed else added, Toast.LENGTH_SHORT).show()
            },
        ),
        leading = { PreviewButton(previewing == name) { vm.preview(engine, name, locale, sample) } },
        value = value,
        valueStyle = FolioType.subheadline,
        showDivider = showDivider && !(selected && v.local != null && v.network != null),
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (starred && engineLabel == null) Icon(Icons.Rounded.Star, null, tint = Folio.colors.secondaryLabel, modifier = Modifier.padding(start = 8.dp).size(18.dp))
                if (selected) Icon(Icons.Rounded.Check, null, tint = Folio.colors.accent, modifier = Modifier.padding(start = 8.dp).size(22.dp))
            }
        },
    )
    if (selected && v.local != null && v.network != null) {
        val online = chosen == v.network
        Column(Modifier.padding(start = 18.dp, end = 16.dp, bottom = 12.dp)) {
            SegmentedControl(listOf(stringResource(R.string.speech_device), stringResource(R.string.speech_online)),
                if (online) 1 else 0, onSelect = { vm.select(engine, v.variant(online = it == 1), locale) }, role = Role.RadioButton)
            if (online) {
                Text(stringResource(R.string.speech_online_note), style = FolioType.footnote, color = Folio.colors.secondaryLabel,
                    modifier = Modifier.padding(top = 8.dp))
            }
        }
        if (showDivider) HorizontalDivider(color = Folio.colors.separator, thickness = 0.5.dp, modifier = Modifier.padding(start = 60.dp, end = 16.dp))
    }
}

/** An engine (or section) name; folding engines get a chevron and, folded, their voice count. */
@Composable
private fun SectionHeader(title: String, value: String? = null, expanded: Boolean? = null, onToggle: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (onToggle != null) Modifier.clickable(role = Role.Button, onClick = onToggle) else Modifier)
            .padding(start = 36.dp, end = 30.dp, top = 20.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = FolioType.title3, color = Folio.colors.label, modifier = Modifier.weight(1f))
        value?.let { Text(it, style = FolioType.subheadline, color = Folio.colors.secondaryLabel) }
        if (expanded != null) {
            Icon(Icons.Rounded.KeyboardArrowDown, null, tint = Folio.colors.secondaryLabel,
                modifier = Modifier.padding(start = 4.dp).size(22.dp).rotate(if (expanded) 0f else -90f))
        }
    }
}

/** «Установить» / «Обновить до …» for a Folio engine module, with download progress and cancel. */
@Composable
private fun ModuleRow(vm: VoiceViewModel, m: ModuleApk, title: String, showDivider: Boolean) {
    val states by vm.moduleStates.collectAsStateWithLifecycle()
    val state = states[m.packageName] ?: InstallState.Idle
    val value = when (state) {
        is InstallState.Downloading -> state.fraction?.let { "${(it * 100).toInt()} %" } ?: stringResource(R.string.speech_module_downloading)
        InstallState.Installing -> stringResource(R.string.speech_module_installing)
        InstallState.NeedsPermission -> stringResource(R.string.speech_module_permission)
        is InstallState.Failed -> stringResource(R.string.speech_module_failed)
        InstallState.Idle -> null
    }
    val action = when (state) {
        is InstallState.Downloading -> stringResource(R.string.speech_voice_cancel)
        is InstallState.Failed -> stringResource(R.string.speech_voice_retry)
        else -> title
    }
    GroupedRow(action, titleColor = Folio.colors.accent, value = value, valueStyle = FolioType.subheadline, showDivider = showDivider,
        enabled = state != InstallState.Installing,
        onClick = { if (state is InstallState.Downloading) vm.cancelModule(m) else vm.installModule(m) })
}

/** A Folio engine's voice screen (download voices, default voice). */
private fun openEngineVoices(context: Context, packageName: String) {
    runCatching {
        context.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).setPackage(packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
