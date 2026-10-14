package app.folio.feature.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import app.folio.core.data.settings.CustomColors
import app.folio.core.data.settings.ReaderTheme
import app.folio.core.data.settings.TextSet
import app.folio.core.designsystem.*
import app.folio.core.designsystem.components.*
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Parameters of «Моя»: a colour card (page, warmth, contrast) and a text card (font and spacing).
 * Changes apply at once through [editor].
 */
@Composable
internal fun CustomThemeSettings(editor: SettingsEditor, dark: Boolean) {
    val settings = editor.settings
    val colors = settings.customColors ?: CustomColors.of(settings.theme)
    val style = settings.textSet
    fun update(value: TextSet) = editor.edit { it.withText(value) }
    fun paint(value: CustomColors) = editor.edit { it.withColors(value) }
    SettingsGroupHeader(stringResource(R.string.settings_color))
    GroupedCard {
    PaperChoice(colors, dark) { paint(colors.copy(base = it)) }
    SettingsDivider()
    SettingsSlider(stringResource(R.string.settings_warmth), colors.warmth.toFloat(), -10f..10f, 1f,
        signed(colors.warmth)) { paint(colors.copy(warmth = it.roundToInt())) }
    SettingsDivider()
    SettingsSlider(stringResource(R.string.settings_contrast), colors.contrast.toFloat(), -10f..10f, 1f,
        signed(colors.contrast)) { paint(colors.copy(contrast = it.roundToInt())) }
    }
    SettingsGroupHeader(stringResource(R.string.settings_text))
    GroupedCard {
    SettingsChoice(stringResource(R.string.settings_font), style.font,
        ReaderFont.entries.map { it.key to it.displayName }, { update(style.copy(font = it)) }) {
        FolioType.body.copy(fontFamily = ReaderFont.of(it).composeFamily)
    }
    SettingsDivider()
    SettingsToggle(stringResource(R.string.settings_bold_text), style.boldText) { update(style.copy(boldText = it)) }
    SettingsDivider()
    // Sliders show what is drawn; moving one turns the set's spacing on.
    val shown = if (style.customize) style else TextSet(font = style.font)
    fun spacing(change: TextSet.() -> TextSet) = update(shown.copy(boldText = style.boldText, justify = style.justify, customize = true).change())
    SettingsSlider(stringResource(R.string.settings_line_spacing), shown.lineSpacing, 0.8f..2.5f, 0.05f,
        String.format(Locale.getDefault(), "%.2f", shown.lineSpacing)) { spacing { copy(lineSpacing = it) } }
    SettingsDivider()
    SettingsSlider(stringResource(R.string.settings_paragraph_spacing), style.paragraphSpacing, 0f..2f, 0.05f,
        String.format(Locale.getDefault(), "%.2f", style.paragraphSpacing)) { update(style.copy(paragraphSpacing = it)) }
    SettingsDivider()
    SettingsSlider(stringResource(R.string.settings_first_line_indent), style.firstLineIndent, 0f..3f, 0.1f,
        String.format(Locale.getDefault(), "%.1f", style.firstLineIndent)) { update(style.copy(firstLineIndent = it)) }
    SettingsDivider()
    SettingsSlider(stringResource(R.string.settings_character_spacing), shown.letterSpacingPct.toFloat(), -10f..10f, 1f,
        "${shown.letterSpacingPct} %") { spacing { copy(letterSpacingPct = it.roundToInt()) } }
    SettingsDivider()
    SettingsSlider(stringResource(R.string.settings_word_spacing), shown.wordSpacingPct.toFloat(), -20f..20f, 1f,
        "${shown.wordSpacingPct} %") { spacing { copy(wordSpacingPct = it.roundToInt()) } }
    SettingsDivider()
    SettingsSlider(stringResource(R.string.settings_margins), shown.marginsPct.toFloat(), -10f..10f, 1f,
        "${shown.marginsPct} %") { spacing { copy(marginsPct = it.roundToInt()) } }
    SettingsDivider()
    SettingsToggle(stringResource(R.string.settings_justify), style.justify) { update(style.copy(justify = it)) }
    }
}

private fun signed(value: Int) = if (value > 0) "+$value" else "$value"

/** «Фон»: one circle per theme palette in the current appearance (five: Bold repeats Original), «Aa» in its text colour. */
@Composable
private fun PaperChoice(colors: CustomColors, dark: Boolean, onSelect: (ReaderTheme) -> Unit) {
    val c = Folio.colors
    Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
        Text(stringResource(R.string.settings_paper), style = FolioType.body, color = c.label)
        Row(Modifier.fillMaxWidth().padding(top = 10.dp).selectableGroup(), horizontalArrangement = Arrangement.SpaceBetween) {
            CustomColors.BASES.forEach { theme ->
                val palette = ReaderPalettes.of(theme, dark)
                val selected = colors.base == theme
                val name = themeName(theme)
                Box(Modifier.size(44.dp).clip(CircleShape).background(palette.background)
                    .border(if (selected) 2.dp else 0.5.dp, if (selected) c.label else c.separator, CircleShape)
                    .selectable(selected, role = Role.RadioButton) { onSelect(theme) }
                    .semantics { contentDescription = name }, contentAlignment = Alignment.Center) {
                    Text("Aa", color = palette.text, fontSize = 15.sp, modifier = Modifier.clearAndSetSemantics { })
                }
            }
        }
    }
}

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
