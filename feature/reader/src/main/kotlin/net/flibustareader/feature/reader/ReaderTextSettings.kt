package net.flibustareader.feature.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.flibustareader.core.data.settings.BookOverrides
import net.flibustareader.core.data.settings.ThemeStyle
import net.flibustareader.core.designsystem.*
import net.flibustareader.core.designsystem.components.*
import java.util.Locale
import kotlin.math.roundToInt

/** Inline typography controls over the theme's own text; changes apply at once through [editor]. */
@Composable
internal fun ReaderTextSettings(editor: SettingsEditor) {
    val settings = editor.settings
    val theme = settings.theme
    val style = settings.themeStyle
    fun update(value: ThemeStyle) = editor.edit { it.withThemeStyle(value) }
    SettingsGroupHeader(stringResource(R.string.settings_font), own = editor.isOwn(listOf(BookOverrides.FONT_SIZE, BookOverrides.FONT, BookOverrides.BOLD)))
    GroupedCard {
        SettingsSlider(stringResource(R.string.settings_font_size), settings.fontSize, 13f..42f, 1f, "${settings.fontSize.roundToInt()}") { size ->
            editor.edit { it.copy(fontSize = size) }
        }
        SettingsDivider()
        SettingsChoice(stringResource(R.string.settings_font), style.font,
            ReaderFont.entries.map { it.key to it.displayName }, { update(style.copy(font = it)) }) {
            FlibustaType.body.copy(fontFamily = ReaderFont.of(it).composeFamily)
        }
        SettingsDivider()
        SettingsToggle(stringResource(R.string.settings_bold_text), style.boldText) { update(style.copy(boldText = it)) }
    }
    SettingsGroupHeader(stringResource(R.string.settings_spacing), own = editor.isOwn(BookOverrides.TEXT - BookOverrides.FONT - BookOverrides.BOLD))
    GroupedCard {
        SettingsToggle(stringResource(R.string.settings_custom_spacing), style.customize) { update(style.copy(customize = it)) }
        if (style.customize) {
            SettingsDivider()
            SettingsSlider(stringResource(R.string.settings_line_spacing), style.lineSpacing, 0.8f..2.5f, 0.05f,
                String.format(Locale.getDefault(), "%.2f", style.lineSpacing)) { update(style.copy(lineSpacing = it)) }
            SettingsDivider()
            SettingsSlider(stringResource(R.string.settings_character_spacing), style.letterSpacingPct.toFloat(), -10f..10f, 1f,
                "${style.letterSpacingPct} %") { update(style.copy(letterSpacingPct = it.roundToInt())) }
            SettingsDivider()
            SettingsSlider(stringResource(R.string.settings_word_spacing), style.wordSpacingPct.toFloat(), -20f..20f, 1f,
                "${style.wordSpacingPct} %") { update(style.copy(wordSpacingPct = it.roundToInt())) }
            SettingsDivider()
            SettingsSlider(stringResource(R.string.settings_margins), style.marginsPct.toFloat(), -10f..10f, 1f,
                "${style.marginsPct} %") { update(style.copy(marginsPct = it.roundToInt())) }
            SettingsDivider()
            SettingsToggle(stringResource(R.string.settings_justify), style.justify) { update(style.copy(justify = it)) }
        }
    }
    Spacer(Modifier.height(16.dp))
    // In a book this drops its own text; for all books it restores the theme's typography.
    val canReset = if (editor.bookScope) editor.isOwn(BookOverrides.TEXT) else style != ThemeStyle.defaultFor(theme)
    GroupedCard {
        Box(Modifier.fillMaxWidth().clickable(enabled = canReset) { editor.resetText() }
            .heightIn(min = 52.dp).padding(18.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(if (editor.bookScope) R.string.settings_reset_book_text else R.string.settings_reset_text), style = FlibustaType.body,
                color = if (canReset) Flibusta.colors.label else Flibusta.colors.tertiaryLabel)
        }
    }
    SettingsNote(stringResource(R.string.settings_text_scope))
}

@Composable
internal fun SettingRow(title: String, onClick: (() -> Unit)? = null, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 54.dp)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = FlibustaType.body, color = Flibusta.colors.label, modifier = Modifier.weight(1f).padding(end = 12.dp))
        trailing()
    }
}
