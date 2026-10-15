package app.folio.feature.reader

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.BrightnessLow
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.ScreenLockRotation
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
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
import app.folio.core.data.settings.Appearance
import app.folio.core.data.settings.BookOverrides
import app.folio.core.data.settings.CustomColors
import app.folio.core.data.settings.ReaderTheme
import app.folio.core.designsystem.components.GroupedCard
import app.folio.core.designsystem.*
import app.folio.core.designsystem.components.IosSlider
import kotlin.math.roundToInt

/** Basic settings: window controls, palette-only cards and the independent text size. */
@Composable
internal fun ReaderAppearanceSettings(editor: SettingsEditor, brightness: SheetBrightness?, rotation: SheetRotation?) {
    val settings = editor.settings
    val dark = ReaderPalettes.isDark(settings.appearance, androidInDarkTheme())
    Spacer(Modifier.height(8.dp))
    GroupedCard {
        if (brightness != null) {
            BrightnessSlider(brightness.value, brightness.onChange)
            SettingsDivider()
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
        SettingsSegments(settings.appearance, listOf(
            SegmentOption(Appearance.Auto, stringResource(R.string.appearance_app_short), stringResource(R.string.appearance_app), Icons.Rounded.BrightnessAuto),
            SegmentOption(Appearance.Light, stringResource(R.string.appearance_light), icon = Icons.Rounded.LightMode),
            SegmentOption(Appearance.Dark, stringResource(R.string.appearance_dark), icon = Icons.Rounded.DarkMode),
        )) { value -> editor.edit { it.copy(appearance = value) } }
        }
        if (rotation != null) {
            IconToggleButton(checked = rotation.locked, onCheckedChange = rotation.onChange,
                modifier = Modifier.padding(end = 8.dp).background(
                    if (rotation.locked) Folio.colors.fill else androidx.compose.ui.graphics.Color.Transparent, CircleShape)) {
                Icon(if (rotation.locked) Icons.Rounded.ScreenLockRotation else Icons.Rounded.ScreenRotation,
                    stringResource(R.string.reader_rotation_lock), tint = Folio.colors.label)
            }
        }
        }
    }
    Spacer(Modifier.height(12.dp))
    GroupedCard {
        Row(Modifier.padding(12.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            CustomColors.BASES.forEach { theme ->
                ThemeSwatch(themeName(theme), settings.theme == theme, ReaderPalettes.of(theme, dark), Modifier.weight(1f)) {
                    editor.edit { it.withTheme(theme) }
                }
            }
        }
        SettingsDivider()
        SettingsSlider(stringResource(R.string.settings_font_size), settings.fontSize, 13f..42f, 1f, "${settings.fontSize.roundToInt()}") { size ->
            editor.edit { it.copy(fontSize = size) }
        }
    }
    Spacer(Modifier.height(12.dp))
    ReaderPageTurnSettings(editor)
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

/** Identical typography in every palette; the name is spoken and shown in the header. */
@Composable
private fun ThemeSwatch(name: String, selected: Boolean, palette: ReaderPalette, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(modifier.clip(shape).background(palette.background)
        .border(if (selected) 2.dp else 0.5.dp, if (selected) Folio.colors.label else Folio.colors.separator, shape)
        .selectable(selected, role = Role.RadioButton, onClick = onClick)
        .semantics { contentDescription = name }
        .heightIn(min = 52.dp), contentAlignment = Alignment.Center) {
        Text("Aa", fontFamily = ReaderFont.of("literata").composeFamily, fontSize = 18.sp, color = palette.text, maxLines = 1,
            modifier = Modifier.clearAndSetSemantics { })
    }
}

/** Auto follows the device; switching it off starts at the currently shown device brightness. */
@Composable
internal fun BrightnessSlider(brightness: Float, onChange: (Float) -> Unit) {
    val auto = brightness < 0
    val c = Folio.colors
    val title = stringResource(R.string.settings_brightness)
    val deviceBrightness = rememberDeviceBrightness()
    val shown = if (auto) deviceBrightness else brightness
    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.clip(CircleShape).background(if (auto) c.label else c.fill)
            .toggleable(auto, role = Role.Switch) { onChange(if (it) -1f else deviceBrightness) }
            .heightIn(min = 36.dp).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.settings_brightness_auto), style = FolioType.subheadline,
                fontWeight = FontWeight.SemiBold, color = if (auto) c.groupedBackground else c.label)
        }
        Icon(Icons.Rounded.BrightnessLow, null, tint = c.secondaryLabel, modifier = Modifier.size(20.dp))
        IosSlider(value = shown, onValueChange = { onChange(it.coerceIn(0.02f, 1f)) },
            valueRange = 0.02f..1f, enabled = !auto,
            contentDescription = title,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(shown, 0.02f..1f)
                if (!auto) setProgress { onChange(it.coerceIn(0.02f, 1f)); true }
            })
        Icon(Icons.Rounded.BrightnessHigh, null, tint = c.secondaryLabel, modifier = Modifier.size(24.dp))
    }
}
