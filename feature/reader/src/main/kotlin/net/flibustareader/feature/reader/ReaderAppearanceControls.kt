package net.flibustareader.feature.reader

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.BrightnessLow
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import net.flibustareader.core.data.settings.Appearance
import net.flibustareader.core.data.settings.BookOverrides
import net.flibustareader.core.data.settings.ReaderSettings
import net.flibustareader.core.data.settings.ReaderTheme
import net.flibustareader.core.designsystem.components.GroupedCard
import net.flibustareader.core.designsystem.*
import net.flibustareader.core.designsystem.components.IosSlider
import kotlin.math.roundToInt

/** «Оформление»: theme swatches with Light/Dark in one card, then window brightness when there is a window. */
@Composable
internal fun ReaderAppearanceSettings(editor: SettingsEditor, brightness: SheetBrightness?) {
    val settings = editor.settings
    // Resolve swatches from appearance, not from the low-contrast Quiet palette.
    val dark = ReaderPalettes.isDark(settings.appearance, isSystemInDarkTheme())
    SettingsGroupHeader(stringResource(R.string.settings_themes), themeName(settings.theme),
        own = editor.isOwn(listOf(BookOverrides.THEME, BookOverrides.APPEARANCE)))
    GroupedCard {
        Row(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ReaderTheme.entries.forEach { theme ->
                ThemeSwatch(theme, settings, ReaderPalettes.of(theme, dark), Modifier.weight(1f)) { editor.edit { it.copy(theme = theme) } }
            }
        }
        SettingsSegments(settings.appearance, listOf(
            SegmentOption(Appearance.Auto, stringResource(R.string.appearance_app_short), stringResource(R.string.appearance_app), Icons.Rounded.BrightnessAuto),
            SegmentOption(Appearance.Light, stringResource(R.string.appearance_light), icon = Icons.Rounded.LightMode),
            SegmentOption(Appearance.Dark, stringResource(R.string.appearance_dark), icon = Icons.Rounded.DarkMode),
        )) { value -> editor.edit { it.copy(appearance = value) } }
    }
    if (brightness != null) {
        SettingsGroupHeader(stringResource(R.string.settings_brightness))
        GroupedCard {
            SettingsToggle(stringResource(R.string.settings_device_brightness), brightness.value < 0) {
                brightness.onChange(if (it) -1f else brightness.manual)
            }
            SettingsDivider()
            BrightnessSlider(brightness.value, brightness.manual, brightness.onChange)
        }
        if (brightness.value < 0) SettingsNote(stringResource(R.string.settings_device_brightness_note))
    }
    SettingsNote(stringResource(if (editor.bookScope) R.string.settings_scope_book_note else R.string.settings_scope_all_note))
}

@Composable
private fun themeName(theme: ReaderTheme) = stringResource(when (theme) {
    ReaderTheme.Original -> R.string.theme_original
    ReaderTheme.Quiet -> R.string.theme_quiet
    ReaderTheme.Paper -> R.string.theme_paper
    ReaderTheme.Bold -> R.string.theme_bold
    ReaderTheme.Calm -> R.string.theme_calm
    ReaderTheme.Focus -> R.string.theme_focus
})

/** A compact «Aa» sample in the theme's own palette and font; the name is spoken and shown in the header. */
@Composable
private fun ThemeSwatch(theme: ReaderTheme, settings: ReaderSettings, palette: ReaderPalette, modifier: Modifier, onClick: () -> Unit) {
    val selected = settings.theme == theme
    val style = settings.themeStyles.getValue(theme)
    val shape = RoundedCornerShape(12.dp)
    val name = themeName(theme)
    Box(modifier.clip(shape).background(palette.background)
        .border(if (selected) 2.dp else 0.5.dp, if (selected) Flibusta.colors.label else Flibusta.colors.separator, shape)
        .selectable(selected, role = Role.RadioButton, onClick = onClick)
        .semantics { contentDescription = name }
        .heightIn(min = 52.dp), contentAlignment = Alignment.Center) {
        Text("Aa", fontFamily = ReaderFont.of(style.font).composeFamily, fontSize = 20.sp, color = palette.text, maxLines = 1,
            fontWeight = if (style.boldText) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.clearAndSetSemantics { })
    }
}

@Composable
internal fun BrightnessSlider(brightness: Float, manual: Float, onChange: (Float) -> Unit) {
    val enabled = brightness >= 0
    val c = Flibusta.colors
    val title = stringResource(R.string.settings_brightness)
    Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = FlibustaType.body, color = c.label, modifier = Modifier.weight(1f))
            Text(if (enabled) "${(brightness * 100).roundToInt()} %" else "—", style = FlibustaType.subheadline, color = c.secondaryLabel)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.BrightnessLow, null, tint = c.secondaryLabel, modifier = Modifier.size(20.dp))
            IosSlider(value = if (enabled) brightness else manual, onValueChange = onChange,
                valueRange = 0.02f..1f, enabled = enabled,
                contentDescription = title,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics {
                    if (enabled) {
                        progressBarRangeInfo = ProgressBarRangeInfo(brightness, 0.02f..1f)
                        setProgress { onChange(it.coerceIn(0.02f, 1f)); true }
                    }
                })
            Icon(Icons.Rounded.BrightnessHigh, null, tint = c.secondaryLabel, modifier = Modifier.size(24.dp))
        }
    }
}
