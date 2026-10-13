package net.flibustareader.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Gradient
import androidx.compose.material.icons.rounded.SwipeLeft
import androidx.compose.material.icons.rounded.SwipeVertical
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.flibustareader.core.data.settings.*
import net.flibustareader.core.designsystem.*
import net.flibustareader.core.designsystem.components.*

/** Page controls are content of the shared settings sheet, never a nested modal. */
@Composable
internal fun ReaderPageSettings(vm: ReaderViewModel, settings: ReaderSettings, palette: ReaderPalette) {
    val overrides by vm.bookChromeStyles.collectAsStateWithLifecycle()
    val defaults by vm.defaultChromeStyles.collectAsStateWithLifecycle()
    val footer = settings.footer
    val turns = buildList {
        add(SegmentOption(PageTurn.Slide, "", stringResource(R.string.page_turn_slide), Icons.Rounded.SwipeLeft))
        if (PageCurl.supported) add(SegmentOption(PageTurn.Curl, "", stringResource(R.string.page_turn_curl), Icons.Rounded.AutoStories))
        add(SegmentOption(PageTurn.Fade, "", stringResource(R.string.page_turn_fade), Icons.Rounded.Gradient))
        add(SegmentOption(PageTurn.Scroll, "", stringResource(R.string.page_turn_scroll), Icons.Rounded.SwipeVertical))
    }
    ValueGroupHeader(stringResource(R.string.settings_page_turn), turns.firstOrNull { it.value == settings.pageTurn }?.description)
    GroupedCard { SettingsSegments(settings.pageTurn, turns, vm::setPageTurn) }

    GroupHeader(stringResource(R.string.header_group))
    GroupedCard {
        SettingsSegments(settings.header, listOf(
            SegmentOption(PageHeader.None, stringResource(R.string.header_none)),
            SegmentOption(PageHeader.Book, stringResource(R.string.header_book_short), stringResource(R.string.header_book)),
            SegmentOption(PageHeader.Chapter, stringResource(R.string.header_chapter_short), stringResource(R.string.header_chapter)),
        ), vm::setPageHeader)
        if (settings.header != PageHeader.None) {
            SettingsDivider()
            ChromeStyleChips(overrides.header, defaults.header, palette, settings.style.font, vm::setHeaderChromeStyle)
        }
    }

    GroupHeader(stringResource(R.string.footer_group))
    GroupedCard {
        SettingsSegments(footer.page, listOf(
            SegmentOption(FooterPage.None, stringResource(R.string.header_none), stringResource(R.string.footer_page_none)),
            SegmentOption(FooterPage.Number, "42", stringResource(R.string.settings_page_number)),
            SegmentOption(FooterPage.NumberOfTotal, stringResource(R.string.reader_page_of, 42, 586), stringResource(R.string.settings_page_total)),
            SegmentOption(FooterPage.Percent, stringResource(R.string.reader_percent, 7), stringResource(R.string.footer_page_percent)),
        ), { vm.setPageFooter(footer.copy(page = it)) })
        SettingsDivider()
        SettingsToggle(stringResource(R.string.footer_pages_left), footer.pagesLeft) { vm.setPageFooter(footer.copy(pagesLeft = it)) }
        SettingsDivider()
        SettingsToggle(stringResource(R.string.footer_clock), footer.clock) { vm.setPageFooter(footer.copy(clock = it)) }
        SettingsDivider()
        SettingsToggle(stringResource(R.string.footer_battery), footer.battery) { vm.setPageFooter(footer.copy(battery = it)) }
        if (!footer.isEmpty) {
            SettingsDivider()
            ChromeStyleChips(overrides.footer, defaults.footer, palette, settings.style.font, vm::setFooterChromeStyle)
            if (settings.pageTurn == PageTurn.Scroll) {
                SettingsDivider()
                Text(stringResource(R.string.footer_style), style = FlibustaType.subheadline,
                    color = Flibusta.colors.secondaryLabel, modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 12.dp))
                SettingsSegments(footer.style, listOf(
                    SegmentOption(FooterStyle.Fade, stringResource(R.string.footer_style_fade)),
                    SegmentOption(FooterStyle.Strip, stringResource(R.string.footer_style_strip)),
                ), { vm.setPageFooter(footer.copy(style = it)) })
            }
        }
    }
    SettingsNote(stringResource(R.string.settings_page_scope))
}

/**
 * Line styles as capsules on the page colour; each name is drawn in its own style, so the chip is the sample.
 * The default chip shows the inherited style; picking it removes only this book's override.
 */
@Composable
private fun ChromeStyleChips(override: ChromeStyle?, default: ChromeStyle, palette: ReaderPalette, font: String, onChange: (ChromeStyle?) -> Unit) {
    val c = Flibusta.colors
    val options = listOf<ChromeStyle?>(null) + ChromeStyle.entries
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup()
        .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        options.forEach { style ->
            val selected = style == override
            val (short, long) = when (style) {
                null -> R.string.settings_style_default_short to R.string.settings_common_style
                ChromeStyle.Quiet -> R.string.settings_style_small_short to R.string.settings_style_small
                ChromeStyle.Classic -> R.string.settings_style_large_short to R.string.settings_style_large
                ChromeStyle.Book -> R.string.settings_style_book_short to R.string.settings_style_book
                ChromeStyle.Clear -> R.string.settings_style_contrast_short to R.string.settings_style_contrast
            }
            val description = stringResource(long)
            Box(Modifier.clip(CircleShape).background(palette.background)
                .border(if (selected) 2.dp else 0.5.dp, if (selected) c.label else c.separator, CircleShape)
                .selectable(selected, role = Role.RadioButton) { onChange(style) }
                .semantics { contentDescription = description }
                .heightIn(min = 40.dp).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(short), style = readerChromeTextStyle(style ?: default, palette, font), maxLines = 1,
                    modifier = Modifier.clearAndSetSemantics { })
            }
        }
    }
}
