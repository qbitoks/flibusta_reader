package net.flibustareader.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import net.flibustareader.core.data.settings.*
import net.flibustareader.core.designsystem.*
import net.flibustareader.core.designsystem.components.*
import kotlin.math.roundToInt

/** Page controls are content of the shared settings sheet, never a nested modal. Both lines share one layout. */
@Composable
internal fun ReaderPageSettings(editor: SettingsEditor, palette: ReaderPalette) {
    val settings = editor.settings
    val footer = settings.footer
    val turns = buildList {
        add(SegmentOption(PageTurn.Slide, stringResource(R.string.page_turn_slide), icon = PageTurnIcons.Slide))
        if (PageCurl.supported) add(SegmentOption(PageTurn.Curl, stringResource(R.string.page_turn_curl), icon = PageTurnIcons.Curl))
        add(SegmentOption(PageTurn.Fade, stringResource(R.string.page_turn_fade_short), stringResource(R.string.page_turn_fade), PageTurnIcons.Fade))
        add(SegmentOption(PageTurn.Scroll, stringResource(R.string.page_turn_scroll), icon = PageTurnIcons.Scroll))
    }
    SettingsGroupHeader(stringResource(R.string.settings_page_turn), own = editor.isOwn(BookOverrides.PAGE_TURN))
    GroupedCard { SettingsSegments(settings.pageTurn, turns, stacked = true) { v -> editor.edit { it.copy(pageTurn = v) } } }

    SettingsGroupHeader(stringResource(R.string.header_group), own = editor.isOwn(BookOverrides.HEADER_KEYS))
    GroupedCard {
        SettingsSegments(settings.header, listOf(
            SegmentOption(PageHeader.None, stringResource(R.string.header_none)),
            SegmentOption(PageHeader.Book, stringResource(R.string.header_book_short), stringResource(R.string.header_book)),
            SegmentOption(PageHeader.Chapter, stringResource(R.string.header_chapter_short), stringResource(R.string.header_chapter)),
        )) { v -> editor.edit { it.copy(header = v) } }
        if (settings.header != PageHeader.None) {
            LineStyle(settings.chromeStyles.header, palette, settings.style.font) { v -> editor.edit { it.copy(chromeStyles = it.chromeStyles.copy(header = v)) } }
            LineSettings(settings.headerLayout, top = true, scroll = false, editor) { change -> editor.edit { it.copy(headerLayout = change(it.headerLayout)) } }
        }
    }

    SettingsGroupHeader(stringResource(R.string.footer_group), own = editor.isOwn(BookOverrides.FOOTER_KEYS))
    GroupedCard {
        SettingsSegments(footer.page, listOf(
            SegmentOption(FooterPage.None, stringResource(R.string.header_none), stringResource(R.string.footer_page_none)),
            SegmentOption(FooterPage.Number, "42", stringResource(R.string.settings_page_number)),
            SegmentOption(FooterPage.NumberOfTotal, stringResource(R.string.reader_page_of, 42, 586), stringResource(R.string.settings_page_total)),
            SegmentOption(FooterPage.Percent, stringResource(R.string.reader_percent, 7), stringResource(R.string.footer_page_percent)),
        )) { v -> editor.edit { it.copy(footer = it.footer.copy(page = v)) } }
        if (footer.page != FooterPage.None) {
            LineStyle(settings.chromeStyles.footer, palette, settings.style.font) { v -> editor.edit { it.copy(chromeStyles = it.chromeStyles.copy(footer = v)) } }
            LineSettings(settings.footerLayout, top = false, scroll = settings.pageTurn == PageTurn.Scroll, editor) { change ->
                editor.edit { it.copy(footerLayout = change(it.footerLayout)) }
            }
        }
    }
}

/**
 * Line styles as capsules on the page colour; each name is drawn in its own style, so the chip is the sample.
 */
@Composable
private fun LineStyle(selected: ChromeStyle, palette: ReaderPalette, font: String, onSelect: (ChromeStyle) -> Unit) {
    val c = Flibusta.colors
    SettingsDivider()
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup()
        .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        ChromeStyle.entries.forEach { style ->
            val isSelected = style == selected
            val (short, long) = when (style) {
                ChromeStyle.Quiet -> R.string.settings_style_small_short to R.string.settings_style_small
                ChromeStyle.Classic -> R.string.settings_style_large_short to R.string.settings_style_large
                ChromeStyle.Book -> R.string.settings_style_book_short to R.string.settings_style_book
                ChromeStyle.Clear -> R.string.settings_style_contrast_short to R.string.settings_style_contrast
            }
            val description = stringResource(long)
            Box(Modifier.clip(CircleShape).background(palette.background)
                .border(if (isSelected) 2.dp else 0.5.dp, if (isSelected) c.label else c.separator, CircleShape)
                .selectable(isSelected, role = Role.RadioButton) { onSelect(style) }
                .semantics { contentDescription = description }
                .heightIn(min = 40.dp).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(short), style = readerChromeTextStyle(style, palette, font), maxLines = 1,
                    modifier = Modifier.clearAndSetSemantics { })
            }
        }
    }
}

/** «Свои параметры» of a line: off draws it as standard and keeps the values for later. */
@Composable
private fun LineSettings(layout: LineLayout, top: Boolean, scroll: Boolean, editor: SettingsEditor, change: ((LineLayout) -> LineLayout) -> Unit) {
    val footer = editor.settings.footer
    fun footerPart(part: (PageFooter) -> PageFooter) = editor.edit { it.copy(footer = part(it.footer)) }
    SettingsDivider()
    SettingsToggle(stringResource(R.string.line_custom), layout.customize) { on -> change { it.copy(customize = on) } }
    if (!layout.customize) return
    if (!top) {
        SettingsDivider()
        SettingsToggle(stringResource(R.string.footer_pages_left), footer.pagesLeft) { on -> footerPart { it.copy(pagesLeft = on) } }
        SettingsDivider()
        SettingsToggle(stringResource(R.string.footer_clock), footer.clock) { on -> footerPart { it.copy(clock = on) } }
        SettingsDivider()
        SettingsToggle(stringResource(R.string.footer_battery), footer.battery) { on -> footerPart { it.copy(battery = on) } }
    }
    SettingsDivider()
    SettingsSlider(stringResource(R.string.line_size), layout.sizePct.toFloat(), LineLayout.SIZE.toRange(), 5f, "${layout.sizePct} %") { v ->
        change { it.copy(sizePct = v.roundToInt()) }
    }
    SettingsDivider()
    SettingsSlider(stringResource(R.string.line_opacity), layout.opacityPct.toFloat(), LineLayout.OPACITY.toRange(), 5f, "${layout.opacityPct} %") { v ->
        change { it.copy(opacityPct = v.roundToInt()) }
    }
    SettingsDivider()
    SegmentsTitle(stringResource(R.string.line_position))
    val aligns = if (top) listOf(
        SegmentOption(LineAlign.Start, stringResource(R.string.line_align_start)),
        SegmentOption(LineAlign.Center, stringResource(R.string.line_align_center)),
        SegmentOption(LineAlign.End, stringResource(R.string.line_align_end)),
    ) else listOf(
        SegmentOption(LineAlign.Spread, stringResource(R.string.line_align_spread)),
        SegmentOption(LineAlign.Center, stringResource(R.string.line_align_center)),
    )
    SettingsSegments(layout.align, aligns) { v -> change { it.copy(align = v) } }
    SettingsDivider()
    SettingsSlider(stringResource(if (top) R.string.line_edge_top else R.string.line_edge_bottom), layout.edge.toFloat(), LineLayout.EDGE.toRange(), 2f,
        signed(layout.edge)) { v -> change { it.copy(edge = v.roundToInt()) } }
    SettingsDivider()
    SettingsSlider(stringResource(R.string.line_gap), layout.gap.toFloat(), LineLayout.GAP.toRange(), 2f, signed(layout.gap)) { v ->
        change { it.copy(gap = v.roundToInt()) }
    }
    if (top) {
        SettingsDivider()
        SettingsToggle(stringResource(R.string.line_uppercase), layout.uppercase) { on -> change { it.copy(uppercase = on) } }
    }
    SettingsDivider()
    SettingsToggle(stringResource(if (top) R.string.line_rule_below else R.string.line_rule_above), layout.rule) { on -> change { it.copy(rule = on) } }
    if (top) {
        SettingsDivider()
        SettingsToggle(stringResource(R.string.line_hide_chapter_start), layout.hideOnChapterStart) { on -> change { it.copy(hideOnChapterStart = on) } }
    }
    if (scroll) {
        SettingsDivider()
        SegmentsTitle(stringResource(R.string.footer_style))
        SettingsSegments(footer.style, listOf(
            SegmentOption(FooterStyle.Fade, stringResource(R.string.footer_style_fade)),
            SegmentOption(FooterStyle.Strip, stringResource(R.string.footer_style_strip)),
        )) { v -> footerPart { it.copy(style = v) } }
    }
}

@Composable
private fun SegmentsTitle(text: String) = Text(text, style = FlibustaType.body, color = Flibusta.colors.label,
    modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 12.dp))

private fun IntRange.toRange() = first.toFloat()..last.toFloat()

/** «+4», «0», «−6»: a shift from the standard spacing. */
private fun signed(value: Int): String = when {
    value > 0 -> "+$value"
    value < 0 -> "−${-value}"
    else -> "0"
}
