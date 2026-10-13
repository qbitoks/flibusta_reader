package net.flibustareader.feature.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.FormatLineSpacing
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SpaceBar
import androidx.compose.material.icons.rounded.TextRotationNone
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.CropPortrait
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material.icons.rounded.VerticalAlignCenter
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.flibustareader.core.data.settings.Appearance
import net.flibustareader.core.data.settings.PageTurn
import net.flibustareader.core.data.settings.ReaderSettings
import net.flibustareader.core.data.settings.ReaderTheme
import net.flibustareader.core.data.settings.ThemeStyle
import net.flibustareader.core.designsystem.ReaderFont
import net.flibustareader.core.designsystem.ReaderPalette
import net.flibustareader.core.designsystem.ReaderPalettes
import net.flibustareader.core.designsystem.Flibusta
import net.flibustareader.core.designsystem.FlibustaType
import net.flibustareader.core.designsystem.UiSans
import net.flibustareader.core.designsystem.components.CircleButton
import net.flibustareader.core.designsystem.components.CircleButtonStyle
import net.flibustareader.core.designsystem.components.GroupHeader
import net.flibustareader.core.designsystem.components.GroupedCard
import net.flibustareader.core.designsystem.components.IosSlider
import net.flibustareader.core.designsystem.components.IosSwitch
import net.flibustareader.core.designsystem.components.PullDownMenu
import net.flibustareader.core.designsystem.components.FlibustaSheet
import net.flibustareader.core.designsystem.components.SheetHeader
import net.flibustareader.core.designsystem.components.StepDots
import java.util.Locale
import kotlin.math.roundToInt

/** Font size steps of the A │ A buttons; the dots under them show the position (screenshot #36). */
private val fontSizes = floatArrayOf(13f, 14f, 15f, 16f, 17f, 18f, 19f, 20f, 22f, 24f, 26f, 28f, 31f, 34f, 38f, 42f)

private fun ReaderTheme.label(): Int = when (this) {
    ReaderTheme.Original -> R.string.theme_original
    ReaderTheme.Quiet -> R.string.theme_quiet
    ReaderTheme.Paper -> R.string.theme_paper
    ReaderTheme.Bold -> R.string.theme_bold
    ReaderTheme.Calm -> R.string.theme_calm
    ReaderTheme.Focus -> R.string.theme_focus
}

/**
 * «Темы и настройки» (guideline § 6.6): a floating panel 10 dp from the screen edges over a
 * dimmed page. «Настроить» opens the per-theme «Настройка темы» sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThemesSheet(
    vm: ReaderViewModel,
    settings: ReaderSettings,
    palette: ReaderPalette,
    brightness: Float,
    onBrightness: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    var customizing by remember { mutableStateOf(false) }
    var chrome by remember { mutableStateOf(false) }
    if (customizing) {
        CustomizeSheet(vm, settings, onDone = { customizing = false })
        return
    }
    if (chrome) {
        PageChromeSheet(vm, settings, palette, onDone = { chrome = false })
        return
    }
    val appearance by vm.appearance.collectAsStateWithLifecycle()
    val dark = palette.isDark
    val top = if (dark) Color(0xFF1C1C1E) else Color(0xFFE6E6E8)
    val bottom = if (dark) Color(0xFF2C2C2E) else Color(0xFFF6F6F6)
    val ink = if (dark) Color.White else Color.Black
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.Transparent,
        contentColor = ink,
        scrimColor = Color.Black.copy(alpha = 0.4f),
        dragHandle = null,
        shape = RoundedCornerShape(0.dp),
        tonalElevation = 0.dp,
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(start = 10.dp, end = 10.dp, bottom = 10.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp)),
        ) {
            Column(Modifier.fillMaxWidth().background(top).padding(horizontal = 18.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.themes_title), fontFamily = UiSans, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = ink, modifier = Modifier.weight(1f))
                    RoundButton(Icons.Rounded.Close, stringResource(R.string.reader_close), bottom, ink.copy(alpha = 0.7f), size = 42.dp, onClick = onDismiss)
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val index = fontSizes.indexOfFirst { it >= settings.fontSize }.let { if (it < 0) fontSizes.lastIndex else it }
                    Column(Modifier.weight(1.55f)) {
                        Row(Modifier.height(46.dp).fillMaxWidth().clip(CircleShape).background(bottom)) {
                            SizeButton("A", 15, stringResource(R.string.font_smaller), ink, Modifier.weight(1f), enabled = index > 0) {
                                vm.setFontSize(fontSizes[(index - 1).coerceAtLeast(0)])
                            }
                            Box(Modifier.width(1.dp).height(46.dp).padding(vertical = 10.dp).background(ink.copy(alpha = 0.2f)))
                            SizeButton("A", 22, stringResource(R.string.font_larger), ink, Modifier.weight(1f), enabled = index < fontSizes.lastIndex) {
                                vm.setFontSize(fontSizes[(index + 1).coerceAtMost(fontSizes.lastIndex)])
                            }
                        }
                        StepDots(fontSizes.size, index, Modifier.padding(start = 18.dp, top = 8.dp))
                    }
                    Row(Modifier.weight(1f).height(46.dp).clip(CircleShape).background(bottom), verticalAlignment = Alignment.CenterVertically) {
                        PageTurnButton(settings.pageTurn, ink, Modifier.weight(1f), vm::setPageTurn)
                        AppearanceButton(appearance, ink, Modifier.weight(1f), vm::setAppearance)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.WbSunny, null, tint = ink, modifier = Modifier.size(16.dp))
                    var local by remember(brightness) { mutableFloatStateOf(if (brightness < 0f) 0.5f else brightness) }
                    IosSlider(
                        value = local,
                        onValueChange = { local = it; onBrightness(it) },
                        modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                        contentDescription = stringResource(R.string.settings_brightness),
                    )
                    Icon(Icons.Rounded.WbSunny, null, tint = ink, modifier = Modifier.size(24.dp))
                }
            }
            Column(Modifier.fillMaxWidth().background(bottom).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ReaderTheme.entries.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { theme ->
                            ThemeTile(
                                theme = theme,
                                tilePalette = ReaderPalettes.of(theme, dark),
                                font = ReaderFont.of(settings.themeStyles.getValue(theme).font),
                                bold = settings.themeStyles.getValue(theme).boldText,
                                selected = settings.theme == theme,
                                selectionColor = ink,
                                modifier = Modifier.weight(1f),
                            ) { vm.setTheme(theme) }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PanelButton(Icons.Rounded.Settings, stringResource(R.string.themes_customize), top, ink, Modifier.weight(1f)) { customizing = true }
                    PanelButton(Icons.Rounded.VerticalAlignCenter, stringResource(R.string.page_chrome_button), top, ink, Modifier.weight(1f)) { chrome = true }
                }
            }
        }
    }
}

@Composable
private fun PanelButton(icon: ImageVector, text: String, container: Color, ink: Color, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.height(46.dp).clip(CircleShape).background(container).clickable(onClick = onClick).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = ink, modifier = Modifier.size(20.dp))
        Text("  " + text, fontFamily = UiSans, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = ink, maxLines = 1)
    }
}

@Composable
private fun SizeButton(label: String, size: Int, description: String, ink: Color, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    Box(modifier.height(46.dp).clickable(enabled = enabled, onClickLabel = description, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, fontFamily = UiSans, fontSize = size.sp, color = if (enabled) ink else ink.copy(alpha = 0.3f))
    }
}

/** «Скольжение / Загиб / Быстрое выцветание / Прокрутка» (screenshot #9); «Загиб» needs Android 13+. */
@Composable
private fun PageTurnButton(current: PageTurn, ink: Color, modifier: Modifier, onSelect: (PageTurn) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier.height(46.dp).clickable(role = Role.Button) { open = true }, contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.ViewCarousel, stringResource(R.string.settings_page_turn), tint = ink, modifier = Modifier.size(24.dp))
        PullDownMenu(open, { open = false }, checkColumn = true) {
            Item(stringResource(R.string.page_turn_slide), { onSelect(PageTurn.Slide) }, checked = current == PageTurn.Slide)
            if (PageCurl.supported) {
                Item(stringResource(R.string.page_turn_curl), { onSelect(PageTurn.Curl) }, checked = current == PageTurn.Curl)
            }
            Item(stringResource(R.string.page_turn_fade), { onSelect(PageTurn.Fade) }, checked = current == PageTurn.Fade)
            Item(stringResource(R.string.page_turn_scroll), { onSelect(PageTurn.Scroll) }, checked = current == PageTurn.Scroll)
        }
    }
}

/** This book's «Светлое / Тёмное / Как в приложении» (white spot 13); null follows the app. */
@Composable
private fun AppearanceButton(current: Appearance?, ink: Color, modifier: Modifier, onSelect: (Appearance?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val icon = when (current) {
        Appearance.Light -> Icons.Rounded.LightMode
        Appearance.Dark -> Icons.Rounded.DarkMode
        Appearance.Auto, null -> Icons.Rounded.BrightnessAuto
    }
    Box(modifier.height(46.dp).clickable(role = Role.Button) { open = true }, contentAlignment = Alignment.Center) {
        Icon(icon, stringResource(R.string.settings_appearance), tint = ink, modifier = Modifier.size(24.dp))
        PullDownMenu(open, { open = false }, checkColumn = true) {
            Item(stringResource(R.string.appearance_light), { onSelect(Appearance.Light) }, icon = Icons.Rounded.LightMode, checked = current == Appearance.Light)
            Item(stringResource(R.string.appearance_dark), { onSelect(Appearance.Dark) }, icon = Icons.Rounded.DarkMode, checked = current == Appearance.Dark)
            Item(stringResource(R.string.appearance_app), { onSelect(null) }, icon = Icons.Rounded.BrightnessAuto, checked = current == null)
        }
    }
}

/** A 104 × 92 tile with «Aa» and the name, both in the theme's own font (guideline § 6.6). */
@Composable
private fun ThemeTile(
    theme: ReaderTheme,
    tilePalette: ReaderPalette,
    font: ReaderFont,
    bold: Boolean,
    selected: Boolean,
    selectionColor: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .aspectRatio(1.13f)
            .clip(shape)
            .background(tilePalette.background)
            .border(
                if (selected) BorderStroke(3.dp, selectionColor) else BorderStroke(0.5.dp, tilePalette.secondary.copy(alpha = 0.35f)),
                shape,
            )
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Aa", fontFamily = font.composeFamily, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, fontSize = 34.sp, color = tilePalette.text)
        Text(stringResource(theme.label()), fontFamily = font.composeFamily, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, fontSize = 15.sp, color = tilePalette.text)
    }
}

/**
 * «Настройка темы» (guideline § 6.8, white spot 15): edits a draft of the current theme's style;
 * the page only changes after ✓, × discards. Spacing sliders exist only while «Настроить» is on.
 */
@Composable
private fun CustomizeSheet(vm: ReaderViewModel, settings: ReaderSettings, onDone: () -> Unit) {
    val theme = settings.theme
    var draft by remember(theme) { mutableStateOf(settings.themeStyle) }
    var fontsOpen by remember { mutableStateOf(false) }
    val c = Flibusta.colors
    val palette = ReaderPalettes.of(theme, c.isDark)
    FlibustaSheet(onDismiss = onDone, containerColor = c.groupedBackground) {
        SheetHeader(
            title = stringResource(R.string.themes_customize_title),
            leading = { CircleButton(Icons.Rounded.Close, stringResource(R.string.reader_close), onDone, style = CircleButtonStyle.Light) },
            trailing = {
                CircleButton(Icons.Rounded.Check, stringResource(R.string.themes_done), {
                    vm.saveThemeStyle(theme, draft)
                    onDone()
                }, style = CircleButtonStyle.Confirm)
            },
        )
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Preview(draft, settings.fontSize, palette)
            GroupHeader(stringResource(R.string.settings_text))
            GroupedCard {
                SettingRow(
                    leading = { Text("Aa", fontFamily = ReaderFont.of(draft.font).composeFamily, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = c.label) },
                    title = stringResource(R.string.settings_font),
                    onClick = { fontsOpen = !fontsOpen },
                ) {
                    Text(ReaderFont.of(draft.font).displayName, fontFamily = ReaderFont.of(draft.font).composeFamily, fontSize = 17.sp, color = c.secondaryLabel)
                    Icon(
                        if (fontsOpen) Icons.Rounded.KeyboardArrowDown else Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        null, tint = c.tertiaryLabel, modifier = Modifier.padding(start = 4.dp).size(22.dp),
                    )
                }
                AnimatedVisibility(fontsOpen) {
                    Column {
                        ReaderFont.entries.forEach { font ->
                            Divider()
                            Row(
                                Modifier.fillMaxWidth().clickable { draft = draft.copy(font = font.key) }.padding(start = 66.dp, end = 18.dp, top = 12.dp, bottom = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(font.displayName, fontFamily = font.composeFamily, fontSize = 18.sp, color = c.label, modifier = Modifier.weight(1f))
                                if (draft.font == font.key) Icon(Icons.Rounded.Check, null, tint = c.label, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
                Divider()
                SettingRow(
                    leading = { Text("B", fontFamily = UiSans, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.label) },
                    title = stringResource(R.string.settings_bold_text),
                ) { IosSwitch(draft.boldText, { draft = draft.copy(boldText = it) }) }
            }
            GroupHeader(stringResource(R.string.settings_layout))
            GroupedCard {
                SettingRow(title = stringResource(R.string.themes_customize)) {
                    IosSwitch(draft.customize, { draft = draft.copy(customize = it) })
                }
                if (draft.customize) {
                    SliderSetting(R.string.settings_line_spacing, Icons.Rounded.FormatLineSpacing, draft.lineSpacing, 0.8f..2.5f, 0.05f,
                        String.format(Locale.getDefault(), "%.2f", draft.lineSpacing)) { draft = draft.copy(lineSpacing = it) }
                    SliderSetting(R.string.settings_character_spacing, Icons.Rounded.TextRotationNone, draft.letterSpacingPct.toFloat(), -10f..10f, 1f,
                        "${draft.letterSpacingPct} %") { draft = draft.copy(letterSpacingPct = it.roundToInt()) }
                    SliderSetting(R.string.settings_word_spacing, Icons.Rounded.SpaceBar, draft.wordSpacingPct.toFloat(), -20f..20f, 1f,
                        "${draft.wordSpacingPct} %") { draft = draft.copy(wordSpacingPct = it.roundToInt()) }
                    SliderSetting(R.string.settings_margins, Icons.Rounded.CropPortrait, draft.marginsPct.toFloat(), -10f..10f, 1f,
                        "${draft.marginsPct} %", last = true) { draft = draft.copy(marginsPct = it.roundToInt()) }
                }
            }
            if (draft.customize) {
                Spacer(Modifier.height(16.dp))
                GroupedCard {
                    SettingRow(title = stringResource(R.string.settings_justify)) {
                        IosSwitch(draft.justify, { draft = draft.copy(justify = it) })
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            val isDefault = draft == ThemeStyle.defaultFor(theme)
            GroupedCard {
                Box(
                    Modifier.fillMaxWidth().height(52.dp).clickable(enabled = !isDefault) { draft = ThemeStyle.defaultFor(theme) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.themes_reset), style = FlibustaType.body, color = if (isDefault) c.tertiaryLabel else c.destructive)
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

/** «Aa» and a few lines of sample text in the draft style, fading out at the bottom. */
@Composable
private fun Preview(draft: ThemeStyle, fontSize: Float, palette: ReaderPalette) {
    val style = draft.toReaderStyle(fontSize)
    val family = ReaderFont.of(draft.font).composeFamily
    val weight = if (draft.boldText) FontWeight.Bold else FontWeight.Normal
    Box(Modifier.fillMaxWidth().height(260.dp).background(palette.background)) {
        Column(Modifier.padding(horizontal = (style.margin * 0.9f).dp, vertical = 18.dp)) {
            Text("Aa", fontFamily = family, fontWeight = weight, fontSize = 34.sp, color = palette.text)
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.themes_preview_text),
                fontFamily = family,
                fontWeight = weight,
                fontSize = 21.sp,
                lineHeight = (21 * style.lineSpacing * 1.2f).sp,
                letterSpacing = style.letterSpacing.em,
                color = palette.text,
            )
        }
        Box(
            Modifier.fillMaxWidth().height(110.dp).align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(palette.background.copy(alpha = 0f), palette.background))),
        )
    }
}

@Composable
internal fun SettingRow(
    title: String,
    leading: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(54.dp).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Box(Modifier.width(36.dp)) { leading() }
            Spacer(Modifier.width(12.dp))
        }
        Text(title, style = FlibustaType.body, color = Flibusta.colors.label, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun SliderSetting(
    label: Int,
    icon: ImageVector,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    shown: String,
    last: Boolean = false,
    onChange: (Float) -> Unit,
) {
    val c = Flibusta.colors
    Divider()
    Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = if (last) 14.dp else 8.dp)) {
        Text(stringResource(label).uppercase(), style = FlibustaType.captionCaps, color = c.secondaryLabel)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = c.label, modifier = Modifier.size(24.dp))
            IosSlider(value = value, onValueChange = onChange, valueRange = range, steps = step, modifier = Modifier.weight(1f).padding(horizontal = 14.dp))
            Text(shown, style = FlibustaType.body, color = c.secondaryLabel, modifier = Modifier.width(62.dp))
        }
    }
}

@Composable
private fun Divider() = HorizontalDivider(color = Flibusta.colors.separator, thickness = 0.5.dp, modifier = Modifier.padding(start = 18.dp))
