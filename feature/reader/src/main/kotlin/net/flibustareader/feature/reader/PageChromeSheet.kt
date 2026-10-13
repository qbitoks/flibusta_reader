package net.flibustareader.feature.reader

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.flibustareader.core.data.settings.*
import net.flibustareader.core.designsystem.*
import net.flibustareader.core.designsystem.R as DesignR
import net.flibustareader.core.designsystem.components.*

/** Page controls are content of the shared settings sheet, never a nested modal. */
@Composable
internal fun ReaderPageSettings(vm: ReaderViewModel, settings: ReaderSettings, palette: ReaderPalette) {
    val overrides by vm.bookChromeStyles.collectAsStateWithLifecycle()
    val footer = settings.footer
    Spacer(Modifier.height(12.dp))
    GroupedCard {
        val turns = buildList {
            add(PageTurn.Slide to stringResource(R.string.page_turn_slide))
            if (PageCurl.supported) add(PageTurn.Curl to stringResource(R.string.page_turn_curl))
            add(PageTurn.Fade to stringResource(R.string.page_turn_fade))
            add(PageTurn.Scroll to stringResource(R.string.page_turn_scroll))
        }
        SettingsChoice(stringResource(R.string.settings_page_turn), settings.pageTurn, turns, vm::setPageTurn)
    }
    GroupHeader(stringResource(R.string.settings_page_preview))
    PageSettingsPreview(settings, palette)
    SettingsNote(stringResource(R.string.settings_preview_note))
    GroupHeader(stringResource(R.string.header_group))
    GroupedCard {
        SettingsChoice(stringResource(R.string.settings_header_content), settings.header,
            listOf(PageHeader.None to stringResource(R.string.header_none), PageHeader.Book to stringResource(R.string.header_book),
                PageHeader.Chapter to stringResource(R.string.header_chapter)), vm::setPageHeader)
        if (settings.header != PageHeader.None) {
            SettingsDivider()
            ChromeStyleChoice(overrides.header, palette, settings.style.font, vm::setHeaderChromeStyle)
        }
    }
    GroupHeader(stringResource(R.string.footer_group))
    GroupedCard {
        SettingsChoice(stringResource(R.string.settings_progress_format), footer.page,
            listOf(FooterPage.None to stringResource(R.string.footer_page_none), FooterPage.Number to stringResource(R.string.settings_page_number),
                FooterPage.NumberOfTotal to stringResource(R.string.settings_page_total), FooterPage.Percent to stringResource(R.string.footer_page_percent)),
            { vm.setPageFooter(footer.copy(page = it)) })
        SettingsDivider()
        SettingsToggle(stringResource(R.string.footer_pages_left), footer.pagesLeft) { vm.setPageFooter(footer.copy(pagesLeft = it)) }
        SettingsDivider()
        SettingsToggle(stringResource(R.string.footer_clock), footer.clock) { vm.setPageFooter(footer.copy(clock = it)) }
        SettingsDivider()
        SettingsToggle(stringResource(R.string.footer_battery), footer.battery) { vm.setPageFooter(footer.copy(battery = it)) }
        if (!footer.isEmpty) {
            SettingsDivider()
            ChromeStyleChoice(overrides.footer, palette, settings.style.font, vm::setFooterChromeStyle)
            if (settings.pageTurn == PageTurn.Scroll) {
                SettingsDivider()
                SettingsChoice(stringResource(R.string.footer_style), footer.style,
                    listOf(FooterStyle.Fade to stringResource(R.string.footer_style_fade), FooterStyle.Strip to stringResource(R.string.footer_style_strip)),
                    { vm.setPageFooter(footer.copy(style = it)) })
            }
        }
    }
    if (footer.isEmpty) SettingsNote(stringResource(R.string.settings_footer_hidden))
    SettingsNote(stringResource(R.string.settings_page_scope))
}

@Composable
private fun ChromeStyleChoice(override: ChromeStyle?, palette: ReaderPalette, font: String, onChange: (ChromeStyle?) -> Unit) {
    val options = listOf(null to stringResource(R.string.settings_common_style)) + ChromeStyle.entries.map { it to stringResource(when (it) {
        ChromeStyle.Quiet -> DesignR.string.chrome_quiet
        ChromeStyle.Classic -> DesignR.string.chrome_classic
        ChromeStyle.Book -> DesignR.string.chrome_book
        ChromeStyle.Clear -> DesignR.string.chrome_clear
    }) }
    SettingsChoice(stringResource(R.string.settings_line_style), override, options, onChange) {
        if (it == null) FlibustaType.body else readerChromeTextStyle(it, palette, font)
    }
}
