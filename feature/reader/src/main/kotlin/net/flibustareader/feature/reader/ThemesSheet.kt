package net.flibustareader.feature.reader

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Surface
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
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
    manualBrightness: Float,
    onBrightness: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    var section by rememberSaveable { mutableIntStateOf(0) }
    val scrollStates = List(3) { rememberScrollState() }
    val c = Flibusta.colors
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(
        usePlatformDefaultWidth = false, dismissOnClickOutside = false, decorFitsSystemWindows = false,
    )) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        DisposableEffect(window, brightness) {
            window?.let { it.attributes = it.attributes.apply { screenBrightness = if (brightness < 0) -1f else brightness.coerceIn(0.02f, 1f) } }
            onDispose { }
        }
        Surface(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
            shape = RoundedCornerShape(topStart = 23.dp, topEnd = 23.dp), color = c.groupedBackground) {
        Column(Modifier.fillMaxSize()) {
            SheetHeader(stringResource(R.string.themes_title), trailing = {
                CircleButton(Icons.Rounded.Close, stringResource(R.string.reader_close), onDismiss)
            })
            BoxWithConstraints(Modifier.weight(1f)) {
                val wide = maxWidth > maxHeight
                val previewHeight = (maxHeight * 0.32f).coerceIn(100.dp, 240.dp)
                @Composable fun Editor(modifier: Modifier) {
                    Column(modifier) {
                        SettingsTabs(section) { section = it }
                        Column(Modifier.weight(1f).verticalScroll(scrollStates[section]).padding(bottom = 24.dp)) {
                            when (section) {
                                0 -> ReaderAppearanceSettings(vm, settings, brightness, manualBrightness, onBrightness)
                                1 -> ReaderTextSettings(vm, settings)
                                2 -> ReaderPageSettings(vm, settings, palette)
                            }
                        }
                    }
                }
                if (wide) {
                    Row(Modifier.fillMaxSize()) {
                        PageSettingsPreview(vm, settings, palette, Modifier.weight(0.44f).fillMaxHeight().padding(bottom = 16.dp))
                        Editor(Modifier.weight(0.56f).fillMaxHeight())
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        PageSettingsPreview(vm, settings, palette, Modifier.fillMaxWidth().height(previewHeight))
                        Editor(Modifier.weight(1f))
                    }
                }
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
        .clip(CircleShape).background(c.fill).padding(3.dp)) {
        val fits = widths.sumOf { it.value.toDouble() } <= maxWidth.value
        LaunchedEffect(selected, fits) {
            if (!fits) scroll.animateScrollTo(with(density) { widths.take(selected).sumOf { it.toPx().toDouble() }.toInt() })
        }
        Row((if (fits) Modifier.fillMaxWidth() else Modifier.horizontalScroll(scroll)).selectableGroup()) {
            labels.forEachIndexed { i, label ->
                Box((if (fits) Modifier.weight(widths[i].value) else Modifier.width(widths[i]))
                    .clip(CircleShape)
                    .background(if (i == selected) (if (c.isDark) c.selectedRow else c.card) else androidx.compose.ui.graphics.Color.Transparent)
                    .selectable(i == selected, role = Role.Tab) { onSelect(i) }
                    .heightIn(min = 36.dp).padding(horizontal = 4.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                    Text(label, style = FlibustaType.subheadline,
                        fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = c.label, textAlign = TextAlign.Center, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun ReaderAppearanceSettings(vm: ReaderViewModel, settings: ReaderSettings, brightness: Float, manualBrightness: Float, onBrightness: (Float) -> Unit) {
    val appearance by vm.appearance.collectAsStateWithLifecycle()
    // Resolve swatches from appearance, not from the low-contrast Quiet palette.
    val dark = ReaderPalettes.isDark(appearance ?: Appearance.Auto, androidx.compose.foundation.isSystemInDarkTheme())
    ValueGroupHeader(stringResource(R.string.settings_themes), themeName(settings.theme))
    GroupedCard {
        Row(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ReaderTheme.entries.forEach { theme ->
                ThemeSwatch(theme, settings, ReaderPalettes.of(theme, dark), Modifier.weight(1f)) { vm.setTheme(theme) }
            }
        }
        BookAppearanceChoices(appearance, vm::setAppearance)
    }
    GroupHeader(stringResource(R.string.settings_brightness))
    GroupedCard {
        SettingsToggle(stringResource(R.string.settings_device_brightness), brightness < 0) { onBrightness(if (it) -1f else manualBrightness) }
        SettingsDivider()
        BrightnessSlider(brightness, manualBrightness, onBrightness)
    }
    if (brightness < 0) SettingsNote(stringResource(R.string.settings_device_brightness_note))
    SettingsNote(stringResource(R.string.settings_appearance_scope))
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
