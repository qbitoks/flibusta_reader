package app.folio.feature.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.folio.core.data.settings.CustomColors
import app.folio.core.data.settings.BookOverrides
import app.folio.core.data.settings.TextSet
import app.folio.core.designsystem.*
import app.folio.core.designsystem.components.*
import java.util.Locale
import kotlin.math.roundToInt

/** Independent palette adjustments on the Advanced tab. */
@Composable
internal fun ReadingColorSettings(editor: SettingsEditor) {
    val settings = editor.settings
    val colors = (settings.customColors ?: CustomColors.of(settings.theme)).copy(base = settings.theme)
    fun paint(value: CustomColors) = editor.edit { it.withColors(value) }
    SettingsDisclosure(stringResource(R.string.settings_tone_contrast)) {
    SettingsSlider(stringResource(R.string.settings_warmth), colors.warmth.toFloat(), -10f..10f, 1f,
        signed(colors.warmth)) { paint(colors.copy(warmth = it.roundToInt())) }
    SettingsDivider()
    SettingsSlider(stringResource(R.string.settings_contrast), colors.contrast.toFloat(), -10f..10f, 1f,
        signed(colors.contrast)) { paint(colors.copy(contrast = it.roundToInt())) }
    }
}

/** Typography on the Advanced tab; text size stays on Basic. */
@Composable
internal fun ReadingTextSettings(editor: SettingsEditor) {
    val style = editor.settings.textSet
    fun update(value: TextSet) = editor.edit { it.withText(value) }
    SettingsDisclosure(stringResource(R.string.settings_font)) {
    SettingsOptions(style.font,
        ReaderFont.entries.map { it.key to it.displayName }, { update(style.copy(font = it)) }, optionStyle = {
        FolioType.body.copy(fontFamily = ReaderFont.of(it).composeFamily)
    })
    SettingsDivider()
    SettingsToggle(stringResource(R.string.settings_bold_text), style.boldText) { update(style.copy(boldText = it)) }
    }
    SpacingSettingsCard(style, ::update) {
    SettingsSlider(stringResource(R.string.settings_line_spacing), style.lineSpacing, 0.8f..2.5f, 0.05f,
        String.format(Locale.getDefault(), "%.2f", style.lineSpacing)) { update(style.copy(lineSpacing = it)) }
    SettingsDivider()
    SettingsSlider(stringResource(R.string.settings_margins), style.marginsPct.toFloat(), 0f..15f, 1f,
        "${style.marginsPct} %") { update(style.copy(marginsPct = it.roundToInt())) }
    SettingsDivider()
    SettingsSlider(stringResource(R.string.settings_paragraph_spacing), style.paragraphSpacing, 0f..2f, 0.05f,
        String.format(Locale.getDefault(), "%.2f", style.paragraphSpacing)) { update(style.copy(paragraphSpacing = it)) }
    SettingsDivider()
    SettingsSlider(stringResource(R.string.settings_first_line_indent), style.firstLineIndent, 0f..3f, 0.1f,
        String.format(Locale.getDefault(), "%.1f", style.firstLineIndent)) { update(style.copy(firstLineIndent = it)) }
    SettingsDivider()
    SettingsSlider(stringResource(R.string.settings_character_spacing), style.letterSpacingPct.toFloat(), -10f..10f, 1f,
        "${style.letterSpacingPct} %") { update(style.copy(letterSpacingPct = it.roundToInt())) }
    SettingsDivider()
    SettingsSlider(stringResource(R.string.settings_word_spacing), style.wordSpacingPct.toFloat(), -20f..20f, 1f,
        "${style.wordSpacingPct} %") { update(style.copy(wordSpacingPct = it.roundToInt())) }
    SettingsDivider()
    SettingsToggle(stringResource(R.string.settings_justify), style.justify) { update(style.copy(justify = it)) }
    }
}

/** Disclosure state belongs to the tab, never to persisted reading settings. */
@Composable
private fun SettingsDisclosure(title: String, content: @Composable () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val state = stringResource(if (expanded) R.string.line_details_expanded else R.string.line_details_collapsed)
    Spacer(Modifier.height(12.dp))
    GroupedCard {
        Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { expanded = !expanded }
            .semantics { stateDescription = state }.heightIn(min = 54.dp)
            .padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = FolioType.body, color = Folio.colors.label, modifier = Modifier.weight(1f))
            Icon(if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                null, tint = Folio.colors.secondaryLabel, modifier = Modifier.size(22.dp))
        }
        if (expanded) {
            SettingsDivider()
            content()
        }
    }
}

private fun signed(value: Int) = if (value > 0) "+$value" else "$value"

/** Centred reset row in its own card, greyed out while there is nothing to reset. */
@Composable
internal fun ResetButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    GroupedCard {
        Box(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick)
            .heightIn(min = 52.dp).padding(18.dp), contentAlignment = Alignment.Center) {
            Text(text, style = FolioType.body, color = if (enabled) Folio.colors.label else Folio.colors.tertiaryLabel)
        }
    }
}

@Composable
internal fun SettingRow(title: String, onClick: (() -> Unit)? = null, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 54.dp)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = FolioType.body, color = Folio.colors.label, modifier = Modifier.weight(1f).padding(end = 12.dp))
        trailing()
    }
}
