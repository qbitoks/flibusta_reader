package app.folio.feature.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
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
import app.folio.core.data.settings.*
import app.folio.core.designsystem.*
import app.folio.core.designsystem.components.*
import kotlin.math.roundToInt

/** A tap on a line in the preview: scroll «Страница» to that line's group and flash it. */
internal class PageJump(val top: Boolean) {
    /** Shown once: coming back to «Страница» later doesn't scroll again. */
    var done = false
}

/** Page controls are content of the shared settings sheet, never a nested modal. Both lines share one layout. */
@Composable
internal fun ReaderPageSettings(editor: SettingsEditor, palette: ReaderPalette, jump: PageJump? = null) {
    val settings = editor.settings
    val footer = settings.footer
    val scroll = settings.pageTurn == PageTurn.Scroll
    val turns = buildList {
        add(SegmentOption(PageTurn.Slide, stringResource(R.string.page_turn_slide), icon = PageTurnIcons.Slide))
        if (PageCurl.supported) add(SegmentOption(PageTurn.Curl, stringResource(R.string.page_turn_curl), icon = PageTurnIcons.Curl))
        add(SegmentOption(PageTurn.Fade, stringResource(R.string.page_turn_fade_short), stringResource(R.string.page_turn_fade), PageTurnIcons.Fade))
        add(SegmentOption(PageTurn.Scroll, stringResource(R.string.page_turn_scroll), icon = PageTurnIcons.Scroll))
    }
    SettingsGroupHeader(stringResource(R.string.settings_page_turn), own = editor.isOwn(BookOverrides.PAGE_TURN))
    GroupedCard { SettingsSegments(settings.pageTurn, turns, stacked = true) { v -> editor.edit { it.copy(pageTurn = v) } } }

    LineGroup(jump?.takeIf { it.top }) {
    SettingsGroupHeader(stringResource(R.string.header_group), own = editor.isOwn(BookOverrides.HEADER_KEYS))
    GroupedCard {
        SettingsSegments(settings.header, listOf(
            SegmentOption(PageHeader.None, stringResource(R.string.header_none)),
            SegmentOption(PageHeader.Book, stringResource(R.string.header_book_short), stringResource(R.string.header_book)),
            SegmentOption(PageHeader.Chapter, stringResource(R.string.header_chapter_short), stringResource(R.string.header_chapter)),
        )) { v -> editor.edit { it.copy(header = v) } }
        if (settings.header != PageHeader.None) {
            LineStyle(settings.chromeStyles.header, palette, settings.style.font) { v -> editor.edit { it.copy(chromeStyles = it.chromeStyles.copy(header = v)) } }
            LineSettings(settings.headerLayout, top = true, editor, gap = true, border = !scroll || settings.headerUnder != FooterStyle.None, under = {
                if (scroll) UnderLine(settings.headerUnder) { v -> editor.edit { it.copy(headerUnder = v) } }
            }) { change -> editor.edit { it.copy(headerLayout = change(it.headerLayout)) } }
        }
    }
    }

    LineGroup(jump?.takeUnless { it.top }) {
    SettingsGroupHeader(stringResource(R.string.footer_group), own = editor.isOwn(BookOverrides.FOOTER_KEYS))
    GroupedCard {
        SettingsSegments(footer.page, listOf(
            SegmentOption(FooterPage.None, stringResource(R.string.header_none), stringResource(R.string.footer_page_none)),
            SegmentOption(FooterPage.Number, "42", stringResource(R.string.settings_page_number)),
            SegmentOption(FooterPage.NumberOfTotal, stringResource(R.string.reader_page_of, 42, 586), stringResource(R.string.settings_page_total)),
            SegmentOption(FooterPage.Percent, stringResource(R.string.reader_percent, 7), stringResource(R.string.footer_page_percent)),
        )) { v -> editor.edit { it.copy(footer = it.footer.copy(page = v)) } }
        // «Нет» drops only the page number: time or pages left alone stay configurable.
        LineStyle(settings.chromeStyles.footer, palette, settings.style.font) { v -> editor.edit { it.copy(chromeStyles = it.chromeStyles.copy(footer = v)) } }
        LineSettings(settings.footerLayout, top = false, editor, gap = !footer.isEmpty, border = !scroll || footer.style != FooterStyle.None, under = {
            if (scroll) UnderLine(footer.style) { v -> editor.edit { it.copy(footer = it.footer.copy(style = v)) } }
        }) { change ->
            editor.edit { it.copy(footerLayout = change(it.footerLayout)) }
        }
    }
    }
    Spacer(Modifier.height(16.dp))
    ResetButton(stringResource(if (editor.bookScope) R.string.settings_reset_book_page else R.string.settings_reset_page),
        editor.canResetPage, editor::resetPage)
}

/** A line's group; [jump] scrolls it into view and flashes it, as a tap on that line in the preview asks. */
@Composable
private fun LineGroup(jump: PageJump?, content: @Composable ColumnScope.() -> Unit) {
    val requester = remember { BringIntoViewRequester() }
    val flash = remember { Animatable(0f) }
    LaunchedEffect(jump) {
        if (jump == null || jump.done) return@LaunchedEffect
        jump.done = true
        withFrameNanos { }
        requester.bringIntoView()
        flash.snapTo(1f)
        flash.animateTo(0f, tween(900))
    }
    // Monochrome like the rest of the interface: a brief shade of the label colour over the group.
    val tint = Folio.colors.label
    Column(Modifier.bringIntoViewRequester(requester).drawWithContent {
        drawContent()
        if (flash.value > 0f) drawRoundRect(tint.copy(alpha = 0.08f * flash.value), cornerRadius = CornerRadius(16.dp.toPx()))
    }, content = content)
}

/**
 * Line styles as a segmented control: each segment names its style in that style, the selected one
 * raised on the page colour, so the row is its own sample without a strip of capsules.
 */
@Composable
private fun LineStyle(selected: ChromeStyle, palette: ReaderPalette, font: String, onSelect: (ChromeStyle) -> Unit) {
    val c = Folio.colors
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 10.dp).height(36.dp)
        .clip(CircleShape).background(c.fill).padding(3.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        ChromeStyle.entries.forEach { style ->
            val isSelected = style == selected
            val (short, long) = when (style) {
                ChromeStyle.Quiet -> R.string.settings_style_small_short to R.string.settings_style_small
                ChromeStyle.Classic -> R.string.settings_style_large_short to R.string.settings_style_large
                ChromeStyle.Book -> R.string.settings_style_book_short to R.string.settings_style_book
                ChromeStyle.Clear -> R.string.settings_style_contrast_short to R.string.settings_style_contrast
            }
            val description = stringResource(long)
            val text = readerChromeTextStyle(style, palette, font)
            Box(Modifier.weight(1f).fillMaxHeight()
                .then(if (isSelected) Modifier.shadow(2.dp, CircleShape) else Modifier)
                .clip(CircleShape).background(if (isSelected) palette.background else Color.Transparent)
                .selectable(isSelected, role = Role.RadioButton) { onSelect(style) }
                .semantics { contentDescription = description }
                .padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                // Unselected samples sit on the grey track, so they take the label colour, keeping only the face and size.
                BasicText(stringResource(short), Modifier.clearAndSetSemantics { },
                    style = if (isSelected) text else text.copy(color = c.secondaryLabel), maxLines = 1, softWrap = false,
                    autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = text.fontSize, stepSize = 0.5.sp))
            }
        }
    }
}

/**
 * «Свои параметры» of a line: off draws it as standard and keeps the values for later. «Отступ до текста»
 * shows only when [gap]: an empty line has nothing to keep apart.
 * [border] is false while scrolling text runs on under the line: then neither gap nor rule means anything.
 */
@Composable
private fun LineSettings(layout: LineLayout, top: Boolean, editor: SettingsEditor, gap: Boolean, border: Boolean, under: @Composable () -> Unit, change: ((LineLayout) -> LineLayout) -> Unit) {
    val footer = editor.settings.footer
    fun footerPart(part: (PageFooter) -> PageFooter) = editor.edit { it.copy(footer = part(it.footer)) }
    SettingsDivider()
    SettingsToggle(stringResource(R.string.line_custom), layout.customize) { on -> change { it.copy(customize = on) } }
    if (!layout.customize) return
    if (!top) {
        SettingsDivider()
        SettingsToggle(stringResource(R.string.footer_clock), footer.clock) { on -> footerPart { it.copy(clock = on) } }
        SettingsDivider()
        SettingsToggle(stringResource(R.string.footer_battery), footer.battery) { on -> footerPart { it.copy(battery = on) } }
        SettingsDivider()
        SettingsToggle(stringResource(R.string.footer_pages_left), footer.pagesLeft) { on -> footerPart { it.copy(pagesLeft = on) } }
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
    if (top) {
        SettingsDivider()
        SettingsToggle(stringResource(R.string.line_uppercase), layout.uppercase) { on -> change { it.copy(uppercase = on) } }
        SettingsDivider()
        SettingsToggle(stringResource(R.string.line_hide_chapter_start), layout.hideOnChapterStart) { on -> change { it.copy(hideOnChapterStart = on) } }
    }
    SettingsDivider()
    SettingsSlider(stringResource(if (top) R.string.line_edge_top else R.string.line_edge_bottom), layout.edge.toFloat(), LineLayout.EDGE.toRange(), 2f,
        signed(layout.edge)) { v -> change { it.copy(edge = v.roundToInt()) } }
    // In scroll mode «Под строкой при прокрутке» decides whether the line has a border with the text at all;
    // the gap and the rule follow it, below, so switching it never moves the rows above.
    under()
    if (!border) return
    if (gap) {
        SettingsDivider()
        SettingsSlider(stringResource(R.string.line_gap), layout.gap.toFloat(), LineLayout.GAP.toRange(), 2f, signed(layout.gap)) { v ->
            change { it.copy(gap = v.roundToInt()) }
        }
    }
    SettingsDivider()
    SettingsToggle(stringResource(if (top) R.string.line_rule_below else R.string.line_rule_above), layout.rule) { on -> change { it.copy(rule = on) } }
}

/** «Под строкой при прокрутке», before the gap and the rule it turns on; without «Свои параметры» the text runs on under the line. */
@Composable
private fun UnderLine(selected: FooterStyle, onSelect: (FooterStyle) -> Unit) {
    SettingsDivider()
    SegmentsTitle(stringResource(R.string.footer_style))
    SettingsSegments(selected, listOf(
        SegmentOption(FooterStyle.None, stringResource(R.string.footer_style_none)),
        SegmentOption(FooterStyle.Clip, stringResource(R.string.footer_style_clip)),
        SegmentOption(FooterStyle.Fade, stringResource(R.string.footer_style_fade)),
    ), onSelect = onSelect)
}

@Composable
private fun SegmentsTitle(text: String) = Text(text, style = FolioType.body, color = Folio.colors.label,
    modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 12.dp))

private fun IntRange.toRange() = first.toFloat()..last.toFloat()

/** «+4», «0», «−6»: a shift from the standard spacing. */
private fun signed(value: Int): String = when {
    value > 0 -> "+$value"
    value < 0 -> "−${-value}"
    else -> "0"
}
