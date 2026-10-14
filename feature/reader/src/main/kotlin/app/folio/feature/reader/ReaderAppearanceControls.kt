package app.folio.feature.reader

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.BrightnessLow
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Edit
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
import app.folio.core.data.settings.Appearance
import app.folio.core.data.settings.BookOverrides
import app.folio.core.data.settings.CustomColors
import app.folio.core.data.settings.TextSet
import app.folio.core.data.settings.ReaderTheme
import app.folio.core.data.settings.textPreset
import app.folio.core.designsystem.components.GroupedCard
import app.folio.core.designsystem.*
import app.folio.core.designsystem.components.IosSlider
import kotlin.math.roundToInt

/**
 * «Оформление»: brightness with «Авто», then one card of size, Light/Dark and themes («Моя» last);
 * «Моя» opens its colour and text cards below.
 */
@Composable
internal fun ReaderAppearanceSettings(editor: SettingsEditor, brightness: SheetBrightness?) {
    val settings = editor.settings
    // Resolve swatches from appearance, not from the low-contrast Quiet palette.
    val dark = ReaderPalettes.isDark(settings.appearance, androidInDarkTheme())
    if (brightness != null) {
        SettingsGroupHeader(stringResource(R.string.settings_brightness))
        GroupedCard { BrightnessSlider(brightness.value, brightness.onChange) }
    }
    SettingsGroupHeader(stringResource(R.string.settings_themes),
        if (settings.custom) stringResource(R.string.theme_mine) else themeName(settings.theme),
        own = editor.isOwn(listOf(BookOverrides.THEME, BookOverrides.APPEARANCE, BookOverrides.FONT_SIZE) + BookOverrides.TEXT))
    GroupedCard {
        SettingsSlider(stringResource(R.string.settings_font_size), settings.fontSize, 13f..42f, 1f, "${settings.fontSize.roundToInt()}") { size ->
            editor.edit { it.copy(fontSize = size) }
        }
        SettingsDivider()
        SettingsSegments(settings.appearance, listOf(
            SegmentOption(Appearance.Auto, stringResource(R.string.appearance_app_short), stringResource(R.string.appearance_app), Icons.Rounded.BrightnessAuto),
            SegmentOption(Appearance.Light, stringResource(R.string.appearance_light), icon = Icons.Rounded.LightMode),
            SegmentOption(Appearance.Dark, stringResource(R.string.appearance_dark), icon = Icons.Rounded.DarkMode),
        )) { value -> editor.edit { it.copy(appearance = value) } }
        Row(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            ReaderTheme.entries.forEach { theme ->
                ThemeSwatch(themeName(theme), !settings.custom && settings.theme == theme, theme.textPreset.text!!,
                    ReaderPalettes.of(theme, dark), Modifier.weight(1f)) { editor.edit { it.withTheme(theme) } }
            }
            val colors = settings.customColors
            ThemeSwatch(stringResource(R.string.theme_mine), settings.custom, settings.customText,
                ReaderPalettes.custom(colors ?: CustomColors.of(settings.theme), dark), Modifier.weight(1f),
                unset = colors == null) { editor.edit { it.withCustom(true) } }
        }
        Spacer(Modifier.height(12.dp))
    }
    if (settings.custom) {
        CustomThemeSettings(editor, dark)
        Spacer(Modifier.height(16.dp))
        // Back to the last standard theme, named so the effect is clear.
        val reset = settings.withCustomReset()
        ResetButton(stringResource(R.string.settings_reset_mine, themeName(settings.theme)), reset != settings) { editor.edit { reset } }
    }
    if (editor.bookScope) {
        Spacer(Modifier.height(16.dp))
        ResetButton(stringResource(R.string.settings_reset_book_text), editor.isOwn(BookOverrides.TEXT), editor::resetText)
    }
    SettingsNote(stringResource(if (editor.bookScope) R.string.settings_scope_book_note else R.string.settings_scope_all_note))
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

/** A compact «Aa» sample in the theme's palette and font; the name is spoken and shown in the header. */
@Composable
private fun ThemeSwatch(name: String, selected: Boolean, style: TextSet, palette: ReaderPalette, modifier: Modifier,
                        unset: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(modifier.clip(shape).background(palette.background)
        .border(if (selected) 2.dp else 0.5.dp, if (selected) Folio.colors.label else Folio.colors.separator, shape)
        .selectable(selected, role = Role.RadioButton, onClick = onClick)
        .semantics { contentDescription = name }
        .heightIn(min = 52.dp), contentAlignment = Alignment.Center) {
        // «Моя» before its first use shows a pencil: there is nothing of its own to sample yet.
        if (unset) Icon(Icons.Rounded.Edit, null, tint = palette.text, modifier = Modifier.size(20.dp))
        else Text("Aa", fontFamily = ReaderFont.of(style.font).composeFamily, fontSize = 18.sp, color = palette.text, maxLines = 1,
            fontWeight = if (style.boldText) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.clearAndSetSemantics { })
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
