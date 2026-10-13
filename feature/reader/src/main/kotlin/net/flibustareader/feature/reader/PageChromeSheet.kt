package net.flibustareader.feature.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.flibustareader.core.data.settings.FooterPage
import net.flibustareader.core.data.settings.FooterStyle
import net.flibustareader.core.data.settings.PageHeader
import net.flibustareader.core.data.settings.ReaderSettings
import net.flibustareader.core.designsystem.Flibusta
import net.flibustareader.core.designsystem.FlibustaType
import net.flibustareader.core.designsystem.ReaderPalette
import net.flibustareader.core.designsystem.R as DesignR
import net.flibustareader.core.designsystem.components.ChromeStylePicker
import net.flibustareader.core.designsystem.components.CircleButton
import net.flibustareader.core.designsystem.components.CircleButtonStyle
import net.flibustareader.core.designsystem.components.FlibustaSheet
import net.flibustareader.core.designsystem.components.GroupHeader
import net.flibustareader.core.designsystem.components.GroupedCard
import net.flibustareader.core.designsystem.components.IosSwitch
import net.flibustareader.core.designsystem.components.PullDownMenu
import net.flibustareader.core.designsystem.components.SheetHeader

private fun PageHeader.label(): Int = when (this) {
    PageHeader.None -> R.string.header_none
    PageHeader.Book -> R.string.header_book
    PageHeader.Chapter -> R.string.header_chapter
}

/**
 * «Колонтитулы»: what the lines above and under the text show. Unlike «Настройка темы» there is
 * no draft: every change shows on the page behind the sheet at once.
 */
@Composable
internal fun PageChromeSheet(vm: ReaderViewModel, settings: ReaderSettings, palette: ReaderPalette, onDone: () -> Unit) {
    val c = Flibusta.colors
    val footer = settings.footer
    val overrides by vm.bookChromeStyles.collectAsStateWithLifecycle()
    FlibustaSheet(onDismiss = onDone, containerColor = c.groupedBackground) {
        SheetHeader(
            title = stringResource(R.string.page_chrome_title),
            trailing = { CircleButton(Icons.Rounded.Check, stringResource(R.string.themes_done), onDone, style = CircleButtonStyle.Confirm) },
        )
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            GroupHeader(stringResource(R.string.header_group))
            ChromeStylePicker(settings.chromeStyles.header, true, palette, settings.style.font, vm::setHeaderChromeStyle)
            GroupedCard {
                SettingRow(title = stringResource(DesignR.string.chrome_use_defaults), onClick = { vm.setHeaderChromeStyle(null) }) {
                    if (overrides.header == null) Icon(Icons.Rounded.Check, null, tint = c.label, modifier = Modifier.size(20.dp))
                }
            }
            if (overrides.header == null) Note(stringResource(DesignR.string.chrome_inherited))
            GroupedCard {
                PageHeader.entries.forEachIndexed { i, header ->
                    if (i > 0) Divider()
                    SettingRow(title = stringResource(header.label()), onClick = { vm.setPageHeader(header) }) {
                        if (settings.header == header) Icon(Icons.Rounded.Check, null, tint = c.label, modifier = Modifier.size(20.dp))
                    }
                }
            }
            Note(stringResource(R.string.header_note))
            GroupHeader(stringResource(R.string.footer_group))
            ChromeStylePicker(settings.chromeStyles.footer, false, palette, settings.style.font, vm::setFooterChromeStyle)
            GroupedCard {
                SettingRow(title = stringResource(DesignR.string.chrome_use_defaults), onClick = { vm.setFooterChromeStyle(null) }) {
                    if (overrides.footer == null) Icon(Icons.Rounded.Check, null, tint = c.label, modifier = Modifier.size(20.dp))
                }
            }
            if (overrides.footer == null) Note(stringResource(DesignR.string.chrome_inherited))
            GroupedCard {
                SettingRow(title = stringResource(R.string.footer_page)) {
                    val options = listOf(
                        FooterPage.None to stringResource(R.string.footer_page_none),
                        FooterPage.Number to "42",
                        FooterPage.NumberOfTotal to stringResource(R.string.reader_page_of, 42, 586),
                        FooterPage.Percent to stringResource(R.string.footer_page_percent),
                    )
                    ChoiceMenu(options, footer.page) { vm.setPageFooter(footer.copy(page = it)) }
                }
                Divider()
                SettingRow(title = stringResource(R.string.footer_pages_left)) {
                    IosSwitch(footer.pagesLeft, { vm.setPageFooter(footer.copy(pagesLeft = it)) })
                }
                Divider()
                SettingRow(title = stringResource(R.string.footer_clock)) {
                    IosSwitch(footer.clock, { vm.setPageFooter(footer.copy(clock = it)) })
                }
                Divider()
                SettingRow(title = stringResource(R.string.footer_battery)) {
                    IosSwitch(footer.battery, { vm.setPageFooter(footer.copy(battery = it)) })
                }
                Divider()
                SettingRow(title = stringResource(R.string.footer_style)) {
                    val options = listOf(
                        FooterStyle.Fade to stringResource(R.string.footer_style_fade),
                        FooterStyle.Strip to stringResource(R.string.footer_style_strip),
                    )
                    ChoiceMenu(options, footer.style) { vm.setPageFooter(footer.copy(style = it)) }
                }
            }
            Note(stringResource(R.string.footer_note))
            Spacer(Modifier.height(40.dp))
        }
    }
}

/** The current choice in grey with a chevron; a tap opens the options with a check by the current one. */
@Composable
private fun <T> ChoiceMenu(options: List<Pair<T, String>>, current: T, onSelect: (T) -> Unit) {
    val c = Flibusta.colors
    var open by remember { mutableStateOf(false) }
    Box {
        Row(Modifier.clickable(role = Role.Button) { open = true }, verticalAlignment = Alignment.CenterVertically) {
            Text(options.firstOrNull { it.first == current }?.second.orEmpty(), style = FlibustaType.body, color = c.secondaryLabel)
            Icon(Icons.Rounded.UnfoldMore, null, tint = c.tertiaryLabel, modifier = Modifier.padding(start = 4.dp).size(18.dp))
        }
        PullDownMenu(open, { open = false }, checkColumn = true) {
            options.forEach { (value, label) -> Item(label, { onSelect(value) }, checked = value == current) }
        }
    }
}

/** Grey explanation under a group. */
@Composable
private fun Note(text: String) {
    Text(text, style = FlibustaType.footnote, color = Flibusta.colors.secondaryLabel, modifier = Modifier.padding(start = 34.dp, end = 34.dp, top = 8.dp))
}

@Composable
private fun Divider() = HorizontalDivider(color = Flibusta.colors.separator, thickness = 0.5.dp, modifier = Modifier.padding(start = 18.dp))
