package app.folio.feature.reader

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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import app.folio.core.data.settings.Appearance
import app.folio.core.data.settings.BookOverrides
import app.folio.core.data.settings.CustomColors
import app.folio.core.data.settings.ReaderTheme
import app.folio.core.designsystem.components.GroupedCard
import app.folio.core.designsystem.*
import app.folio.core.designsystem.components.IosSlider
import kotlin.math.roundToInt

/** Basic settings: palettes first, then text size, screen controls and turning, each under a short heading. */
@Composable
internal fun ReaderAppearanceSettings(editor: SettingsEditor, brightness: SheetBrightness?, rotation: SheetRotation?) {
    val settings = editor.settings
    val dark = ReaderPalettes.isDark(settings.appearance, androidInDarkTheme())
    SettingsGroupHeader(stringResource(R.string.settings_basic_theme))
    GroupedCard {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 12.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            CustomColors.BASES.forEach { theme ->
                ThemeSwatch(themeName(theme), settings.theme == theme, ReaderPalettes.of(theme, dark), Modifier.weight(1f)) {
                    editor.edit { it.withTheme(theme) }
                }
            }
        }
        SettingsDivider()
        SettingsSegments(settings.appearance, listOf(
            SegmentOption(Appearance.Auto, stringResource(R.string.appearance_app_short), stringResource(R.string.appearance_app), Icons.Rounded.BrightnessAuto),
            SegmentOption(Appearance.Light, stringResource(R.string.appearance_light), icon = Icons.Rounded.LightMode),
            SegmentOption(Appearance.Dark, stringResource(R.string.appearance_dark), icon = Icons.Rounded.DarkMode),
        )) { value -> editor.edit { it.copy(appearance = value) } }
    }
    SettingsGroupHeader(stringResource(R.string.settings_basic_text))
    GroupedCard { FontSizeSlider(settings.fontSize) { size -> editor.edit { it.copy(fontSize = size) } } }
    if (brightness != null || rotation != null) {
        SettingsGroupHeader(stringResource(R.string.settings_basic_screen))
        GroupedCard {
            if (brightness != null) BrightnessSlider(brightness.value, brightness.onChange)
            if (brightness != null && rotation != null) SettingsDivider()
            if (rotation != null) SettingsToggle(stringResource(R.string.reader_rotation_lock), rotation.locked, rotation.onChange)
        }
    }
    SettingsGroupHeader(stringResource(R.string.settings_page_turn))
    ReaderPageTurnSettings(editor)
}

/** Books-style size row: a small and a large «A» frame the slider; the value sits in the title row. */
@Composable
private fun FontSizeSlider(size: Float, onChange: (Float) -> Unit) {
    val c = Folio.colors
    val title = stringResource(R.string.settings_font_size)
    val range = 13f..42f
    val change by rememberUpdatedState(onChange)
    Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = FolioType.body, color = c.label, modifier = Modifier.weight(1f))
            Text("${size.roundToInt()}", style = FolioType.subheadline, color = c.secondaryLabel)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("A", fontFamily = ReaderFont.of("literata").composeFamily, fontSize = 14.sp, color = c.secondaryLabel,
                modifier = Modifier.clearAndSetSemantics { })
            IosSlider(value = size, onValueChange = { change(it) }, valueRange = range, steps = 1f,
                contentDescription = title,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics {
                    progressBarRangeInfo = ProgressBarRangeInfo(size, range, (range.endInclusive - range.start).toInt() - 1)
                    setProgress { change(it.coerceIn(range).roundToInt().toFloat()); true }
                })
            Text("A", fontFamily = ReaderFont.of("literata").composeFamily, fontSize = 24.sp, color = c.secondaryLabel,
                modifier = Modifier.clearAndSetSemantics { })
        }
    }
}
@Composable
internal fun themeName(theme: ReaderTheme) = stringResource(when (theme) {
    ReaderTheme.Original -> R.string.theme_original
    ReaderTheme.Quiet -> R.string.theme_quiet
    ReaderTheme.Paper -> R.string.theme_paper
    ReaderTheme.Bold -> R.string.theme_bold
    ReaderTheme.Calm -> R.string.theme_calm
    ReaderTheme.Focus -> R.string.theme_focus
})

/** Identical typography in every palette; the name is spoken and shown under the swatch. */
@Composable
private fun ThemeSwatch(name: String, selected: Boolean, palette: ReaderPalette, modifier: Modifier, onClick: () -> Unit) =
    ChoiceTile(name, selected, palette.background, modifier, onClick = onClick) {
        Text("Aa", fontFamily = ReaderFont.of("literata").composeFamily, fontSize = 20.sp, color = palette.text, maxLines = 1)
    }

/** Auto follows the device; switching it off starts at the currently shown device brightness. Laid out like text size. */
@Composable
internal fun BrightnessSlider(brightness: Float, onChange: (Float) -> Unit) {
    val auto = brightness < 0
    val c = Folio.colors
    val title = stringResource(R.string.settings_brightness)
    val deviceBrightness = rememberDeviceBrightness()
    val shown = if (auto) deviceBrightness else brightness
    Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = FolioType.body, color = c.label, modifier = Modifier.weight(1f))
            Box(Modifier.clip(CircleShape).background(if (auto) c.accent.copy(alpha = 0.16f) else c.fill)
                .toggleable(auto, role = Role.Switch) { onChange(if (it) -1f else deviceBrightness) }
                .heightIn(min = 30.dp).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.settings_brightness_auto), style = FolioType.subheadline,
                    fontWeight = FontWeight.SemiBold, color = if (auto) c.accent else c.secondaryLabel)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Rounded.BrightnessLow, null, tint = c.secondaryLabel, modifier = Modifier.size(18.dp))
            IosSlider(value = shown, onValueChange = { onChange(it.coerceIn(0.02f, 1f)) },
                valueRange = 0.02f..1f, enabled = !auto,
                contentDescription = title,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics {
                    progressBarRangeInfo = ProgressBarRangeInfo(shown, 0.02f..1f)
                    if (!auto) setProgress { onChange(it.coerceIn(0.02f, 1f)); true }
                })
            Icon(Icons.Rounded.BrightnessHigh, null, tint = c.secondaryLabel, modifier = Modifier.size(26.dp))
        }
    }
}
