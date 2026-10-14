package app.folio.feature.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.folio.core.data.settings.*
import app.folio.core.designsystem.*
import app.folio.core.designsystem.components.*
import kotlin.math.roundToInt

/** Page controls are content of the shared settings sheet, never a nested modal. Both lines share one layout. */
@Composable
internal fun ReaderPageSettings(editor: SettingsEditor, source: PreviewSource) {
    val settings = editor.settings
    val footer = settings.shownFooter
    val turns = buildList {
        add(SegmentOption(PageTurn.Slide, stringResource(R.string.page_turn_slide), icon = PageTurnIcons.Slide))
        if (PageCurl.supported) add(SegmentOption(PageTurn.Curl, stringResource(R.string.page_turn_curl), icon = PageTurnIcons.Curl))
        add(SegmentOption(PageTurn.Fade, stringResource(R.string.page_turn_fade_short), stringResource(R.string.page_turn_fade), PageTurnIcons.Fade))
        add(SegmentOption(PageTurn.Scroll, stringResource(R.string.page_turn_scroll), icon = PageTurnIcons.Scroll))
    }
    SettingsGroupHeader(stringResource(R.string.settings_page_turn), own = editor.isOwn(BookOverrides.PAGE_TURN))
    GroupedCard { SettingsSegments(settings.pageTurn, turns, stacked = true) { v -> editor.edit { it.copy(pageTurn = v) } } }

    Column {
    SettingsGroupHeader(stringResource(R.string.header_group), own = editor.isOwn(BookOverrides.HEADER_KEYS))
    GroupedCard {
        SettingsSegments(settings.header, listOf(
            SegmentOption(PageHeader.None, stringResource(R.string.header_none)),
            SegmentOption(PageHeader.Book, stringResource(R.string.header_book_short), stringResource(R.string.header_book)),
            SegmentOption(PageHeader.Chapter, stringResource(R.string.header_chapter_short), stringResource(R.string.header_chapter)),
        )) { v -> editor.edit { it.copy(header = v) } }
        if (settings.header != PageHeader.None) {
            LineSettings(settings.shownHeaderLayout, top = true, editor, gap = true) { change -> editor.edit { it.editHeaderLine(change) } }
        } else {
            Text(stringResource(R.string.header_hidden), style = FolioType.footnote,
                color = Folio.colors.secondaryLabel,
                modifier = Modifier.padding(horizontal = 18.dp).padding(bottom = 12.dp))
        }
    }
    }

    Column {
    SettingsGroupHeader(stringResource(R.string.footer_group), own = editor.isOwn(BookOverrides.FOOTER_KEYS))
    GroupedCard {
        FooterContentCapsules(editor, source)
        if (footer.isEmpty) {
            Text(stringResource(R.string.footer_hidden), style = FolioType.footnote,
                color = Folio.colors.secondaryLabel,
                modifier = Modifier.padding(horizontal = 18.dp).padding(bottom = 12.dp))
        } else {
            LineSettings(settings.shownFooterLayout, top = false, editor, gap = true) { change ->
                editor.edit { it.editFooterLine(change) }
            }
        }
    }
    }
    Spacer(Modifier.height(16.dp))
    ResetButton(stringResource(if (editor.bookScope) R.string.settings_reset_book_page else R.string.settings_reset_page),
        editor.canResetPage, editor::resetPage)
}

/**
 * Disclosure state only controls the fields, never the applied settings. «Отступ до текста»
 * shows only when [gap]: an empty line has nothing to keep apart.
 */
@Composable
private fun LineSettings(layout: LineLayout, top: Boolean, editor: SettingsEditor, gap: Boolean, change: ((LineLayout) -> LineLayout) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val state = stringResource(if (expanded) R.string.line_details_expanded else R.string.line_details_collapsed)
    SettingsDivider()
    Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { expanded = !expanded }
        .semantics { stateDescription = state }
        .heightIn(min = 54.dp).padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.line_details), style = FolioType.body, color = Folio.colors.label, modifier = Modifier.weight(1f))
        Icon(if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
            null, tint = Folio.colors.secondaryLabel, modifier = Modifier.size(22.dp))
    }
    if (!expanded) return
    if (top) {
        SettingsDivider()
        SettingsToggle(stringResource(R.string.line_uppercase), layout.uppercase) { on -> change { it.copy(uppercase = on) } }
        SettingsDivider()
        SettingsToggle(stringResource(R.string.line_hide_chapter_start), layout.hideOnChapterStart) { on -> change { it.copy(hideOnChapterStart = on) } }
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
    val scroll = editor.settings.pageTurn == PageTurn.Scroll
    SegmentsTitle(stringResource(if (scroll) R.string.line_horizontal else R.string.line_position))
    val aligns = if (top) listOf(
        SegmentOption(LineAlign.Start, stringResource(R.string.line_align_start)),
        SegmentOption(LineAlign.Center, stringResource(R.string.line_align_center)),
        SegmentOption(LineAlign.End, stringResource(R.string.line_align_end)),
    ) else listOf(
        SegmentOption(LineAlign.Spread, stringResource(R.string.line_align_spread)),
        SegmentOption(LineAlign.Center, stringResource(R.string.line_align_center)),
    )
    SettingsSegments(layout.align, aligns) { v -> change { it.copy(align = v) } }
    if (scroll && gap) {
        SettingsDivider()
        SegmentsTitle(stringResource(R.string.line_vertical))
        SettingsSegments(layout.vertical, listOf(
            SegmentOption(LineVertical.Edge, stringResource(R.string.line_vertical_edge)),
            SegmentOption(LineVertical.Center, stringResource(R.string.line_vertical_center)),
            SegmentOption(LineVertical.Text, stringResource(R.string.line_vertical_text)),
        )) { v -> change { it.copy(vertical = v) } }
        SettingsDivider()
        SettingsSlider(stringResource(R.string.line_block_height), layout.requestedBlockHeight.toFloat(),
            LineLayout.BLOCK_HEIGHT.toRange(), 2f, "${layout.requestedBlockHeight} dp") { v ->
            change { it.copy(blockHeight = v.roundToInt()) }
        }
        Text(stringResource(R.string.line_block_minimum), style = FolioType.footnote,
            color = Folio.colors.secondaryLabel, modifier = Modifier.padding(horizontal = 18.dp).padding(bottom = 12.dp))
        SettingsDivider()
        SettingsToggle(stringResource(R.string.line_rule), layout.rule) { on -> change { it.copy(rule = on) } }
        SettingsDivider()
        SettingsSlider(stringResource(R.string.line_text_gap), layout.distanceToText.toFloat(),
            LineLayout.TEXT_GAP.toRange(), 2f, "${layout.distanceToText} dp") { v ->
            change { it.copy(textGap = v.roundToInt()) }
        }
        SettingsDivider()
        SegmentsTitle(stringResource(R.string.scroll_boundary))
        val boundary = if (top) editor.settings.scrollHeaderBoundary else editor.settings.scrollFooterBoundary
        SettingsSegments(boundary, listOf(
            SegmentOption(FooterStyle.Clip, stringResource(R.string.scroll_boundary_clear)),
            SegmentOption(FooterStyle.Fade, stringResource(R.string.scroll_boundary_smooth)),
        )) { v -> editor.edit { it.editLineBoundary(top, v) } }
    } else if (!scroll) {
        SettingsDivider()
        SettingsSlider(stringResource(if (top) R.string.line_edge_top else R.string.line_edge_bottom), layout.edge.toFloat(), LineLayout.EDGE.toRange(), 2f,
            signed(layout.edge)) { v -> change { it.copy(edge = v.roundToInt()) } }
        SettingsDivider()
        SettingsToggle(stringResource(R.string.line_rule), layout.rule) { on -> change { it.copy(rule = on) } }
        if (gap) {
            SettingsDivider()
            SettingsSlider(stringResource(R.string.line_gap), layout.gap.toFloat(), LineLayout.GAP.toRange(), 2f, signed(layout.gap)) { v ->
                change { it.copy(gap = v.roundToInt()) }
            }
        }
    }
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
