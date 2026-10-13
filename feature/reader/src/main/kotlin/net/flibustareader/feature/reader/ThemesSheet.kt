package net.flibustareader.feature.reader

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.flibustareader.core.data.settings.Appearance
import net.flibustareader.core.data.settings.ReaderSettings
import net.flibustareader.core.data.settings.ReaderTheme
import net.flibustareader.core.designsystem.*
import net.flibustareader.core.designsystem.components.*

/** One sheet owns navigation; changing sections never opens a second modal. */
@Composable
internal fun ThemesSheet(
    vm: ReaderViewModel,
    settings: ReaderSettings,
    palette: ReaderPalette,
    brightness: Float,
    onBrightness: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    var section by rememberSaveable { mutableIntStateOf(0) }
    val scrollStates = List(3) { rememberScrollState() }
    val c = Flibusta.colors
    FlibustaSheet(onDismiss, containerColor = c.groupedBackground) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f)) {
            SheetHeader(stringResource(R.string.themes_title), trailing = {
                CircleButton(Icons.Rounded.Close, stringResource(R.string.reader_close), onDismiss)
            })
            SettingsTabs(section) { section = it }
            Column(Modifier.weight(1f).verticalScroll(scrollStates[section]).padding(bottom = 24.dp)) {
                when (section) {
                    0 -> ReaderAppearanceSettings(vm, settings, brightness, onBrightness)
                    1 -> ReaderTextSettings(vm, settings, palette)
                    2 -> ReaderPageSettings(vm, settings, palette)
                }
            }
        }
    }
}

@Composable
private fun SettingsTabs(selected: Int, onSelect: (Int) -> Unit) {
    val c = Flibusta.colors
    val labels = listOf(R.string.settings_tab_appearance, R.string.settings_text, R.string.settings_tab_page).map { stringResource(it) }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = FlibustaType.subheadline.copy(fontWeight = FontWeight.SemiBold)
    val widths = labels.map { with(density) { measurer.measure(it, style).size.width.toDp() } + 12.dp }
    val scroll = rememberScrollState()
    BoxWithConstraints(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()
        .clip(RoundedCornerShape(16.dp)).background(c.fill).padding(4.dp)) {
        val fits = widths.sumOf { it.value.toDouble() } <= maxWidth.value
        LaunchedEffect(selected, fits) {
            if (!fits) scroll.animateScrollTo(with(density) { widths.take(selected).sumOf { it.toPx().toDouble() }.toInt() })
        }
        Row((if (fits) Modifier.fillMaxWidth() else Modifier.horizontalScroll(scroll)).selectableGroup()) {
            labels.forEachIndexed { i, label ->
                Box((if (fits) Modifier.weight(widths[i].value) else Modifier.width(widths[i]))
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (i == selected) (if (c.isDark) c.selectedRow else c.card) else androidx.compose.ui.graphics.Color.Transparent)
                    .selectable(i == selected, role = Role.Tab) { onSelect(i) }
                    .heightIn(min = 48.dp).padding(horizontal = 4.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                    Text(label, style = FlibustaType.subheadline,
                        fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = c.label, textAlign = TextAlign.Center, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun ReaderAppearanceSettings(vm: ReaderViewModel, settings: ReaderSettings, brightness: Float, onBrightness: (Float) -> Unit) {
    val appearance by vm.appearance.collectAsStateWithLifecycle()
    // Resolve swatches from appearance, not from the low-contrast Quiet palette.
    val dark = ReaderPalettes.isDark(appearance ?: Appearance.Auto, androidx.compose.foundation.isSystemInDarkTheme())
    GroupHeader(stringResource(R.string.settings_themes))
    Column(Modifier.padding(horizontal = 16.dp).selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ReaderTheme.entries.chunked(3).forEach { themes ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                themes.forEach { theme ->
                    ThemeTile(theme, settings, ReaderPalettes.of(theme, dark), Modifier.weight(1f)) { vm.setTheme(theme) }
                }
            }
        }
    }
    SettingsNote(stringResource(R.string.settings_theme_note))
    GroupHeader(stringResource(R.string.settings_book_appearance))
    GroupedCard {
        SettingsChoice(stringResource(R.string.settings_appearance), appearance,
            listOf(null to stringResource(R.string.appearance_app), Appearance.Light to stringResource(R.string.appearance_light),
                Appearance.Dark to stringResource(R.string.appearance_dark)), vm::setAppearance)
    }
    GroupHeader(stringResource(R.string.settings_brightness))
    GroupedCard {
        var local by remember(brightness) { mutableFloatStateOf(if (brightness < 0) 0.5f else brightness) }
        SettingsSlider(stringResource(R.string.settings_brightness), local, 0f..1f, 0.01f,
            if (brightness < 0) stringResource(R.string.settings_device_brightness) else "${(local * 100).toInt()} %",
            onChange = { local = it; onBrightness(it) })
        SettingRow(stringResource(R.string.settings_device_brightness), onClick = { onBrightness(-1f) }) {
            if (brightness < 0) SettingsCheck()
        }
    }
}

@Composable
private fun ThemeTile(theme: ReaderTheme, settings: ReaderSettings, palette: ReaderPalette, modifier: Modifier, onClick: () -> Unit) {
    val selected = settings.theme == theme
    val style = settings.themeStyles.getValue(theme)
    val font = ReaderFont.of(style.font).composeFamily
    val shape = RoundedCornerShape(16.dp)
    Column(modifier.clip(shape).background(palette.background)
        .border(if (selected) 2.dp else 0.5.dp, if (selected) Flibusta.colors.label else Flibusta.colors.separator, shape)
        .selectable(selected, role = Role.RadioButton, onClick = onClick)
        .heightIn(min = 102.dp).padding(horizontal = 4.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Aa", fontFamily = font, fontSize = 30.sp, color = palette.text,
            fontWeight = if (style.boldText) FontWeight.Bold else FontWeight.Normal)
        Text(stringResource(when (theme) {
            ReaderTheme.Original -> R.string.theme_original
            ReaderTheme.Quiet -> R.string.theme_quiet
            ReaderTheme.Paper -> R.string.theme_paper
            ReaderTheme.Bold -> R.string.theme_bold
            ReaderTheme.Calm -> R.string.theme_calm
            ReaderTheme.Focus -> R.string.theme_focus
        }), style = FlibustaType.subheadline, color = palette.text, textAlign = TextAlign.Center)
    }
}
