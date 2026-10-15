package app.folio.feature.reader

import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.rounded.TextDecrease
import androidx.compose.material.icons.rounded.TextIncrease
import androidx.compose.material.icons.rounded.UnfoldLess
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.VisibilityOff

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
internal fun ReaderPageTurnSettings(editor: SettingsEditor) {
    val settings = editor.settings
    val turns = buildList {
        // Stored as Fade for saved settings: the old «Быстрое выцветание» became «Без анимации».
        add(SegmentOption(PageTurn.Fade, stringResource(R.string.page_turn_none), icon = PageTurnIcons.Instant))
        add(SegmentOption(PageTurn.Slide, stringResource(R.string.page_turn_slide), icon = PageTurnIcons.Slide))
        if (PageCurl.supported) add(SegmentOption(PageTurn.Curl, stringResource(R.string.page_turn_curl), icon = PageTurnIcons.Curl))
        add(SegmentOption(PageTurn.Scroll, stringResource(R.string.page_turn_scroll), icon = PageTurnIcons.Scroll))
    }
    GroupedCard { SettingsSegments(settings.pageTurn, turns, stacked = true) { v -> editor.edit { it.copy(pageTurn = v) } } }
}

@Composable
internal fun ReaderPageSettings(editor: SettingsEditor, source: PreviewSource) {
    val settings = editor.settings
    val footer = settings.shownFooter
    Column {
    SettingsGroupHeader(stringResource(R.string.header_group), own = editor.isOwn(BookOverrides.HEADER_KEYS))
    GroupedCard {
        SettingsSegments(settings.header, listOf(
            SegmentOption(PageHeader.None, stringResource(R.string.header_none), icon = Icons.Rounded.VisibilityOff),
            SegmentOption(PageHeader.Book, stringResource(R.string.header_book_short), stringResource(R.string.header_book), Icons.AutoMirrored.Rounded.MenuBook),
            SegmentOption(PageHeader.Chapter, stringResource(R.string.header_chapter_short), stringResource(R.string.header_chapter), Icons.Rounded.Bookmark),
        )) { v -> editor.edit { it.copy(header = v) } }
        if (settings.header != PageHeader.None) {
            LineSettings(settings.shownHeaderLayout, top = true, editor, gap = true) { change -> editor.edit { it.editHeaderLine(change) } }
        } else {
            HiddenLineHint(stringResource(R.string.header_hidden))
        }
    }
    }

    Column {
    SettingsGroupHeader(stringResource(R.string.footer_group), own = editor.isOwn(BookOverrides.FOOTER_KEYS))
    GroupedCard {
        FooterContentCapsules(editor, source)
        if (footer.isEmpty) {
            HiddenLineHint(stringResource(R.string.footer_hidden))
        } else {
            LineSettings(settings.shownFooterLayout, top = false, editor, gap = true) { change ->
                editor.edit { it.editFooterLine(change) }
            }
        }
    }
    }
}

/**
 * Disclosure state only controls the fields, never the applied settings. «Отступ до текста»
 * shows only when [gap]: an empty line has nothing to keep apart.
 */
@Composable
private fun LineSettings(layout: LineLayout, top: Boolean, editor: SettingsEditor, gap: Boolean, change: ((LineLayout) -> LineLayout) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    SettingsDivider()
    SettingsDetailsRow(expanded) { expanded = !expanded }
    SettingsDetails(expanded) {
    val scroll = editor.settings.pageTurn == PageTurn.Scroll
    SettingsSubheader(stringResource(R.string.line_group_look))
    SettingsSlider(stringResource(R.string.line_size), layout.sizePct.toFloat(), LineLayout.SIZE.toRange(), 5f, "${layout.sizePct} %",
        Icons.Rounded.TextDecrease, Icons.Rounded.TextIncrease) { v -> change { it.copy(sizePct = v.roundToInt()) } }
    SettingsSlider(stringResource(R.string.line_opacity), layout.opacityPct.toFloat(), LineLayout.OPACITY.toRange(), 5f, "${layout.opacityPct} %",
        Icons.Outlined.WaterDrop, Icons.Rounded.WaterDrop) { v -> change { it.copy(opacityPct = v.roundToInt()) } }
    if (top) SettingsToggle(stringResource(R.string.line_uppercase), layout.uppercase) { on -> change { it.copy(uppercase = on) } }

    SettingsDivider()
    SettingsSubheader(stringResource(R.string.line_group_place))
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
        SegmentsTitle(stringResource(R.string.line_vertical))
        SettingsSegments(layout.vertical, listOf(
            SegmentOption(LineVertical.Edge, stringResource(R.string.line_vertical_edge)),
            SegmentOption(LineVertical.Center, stringResource(R.string.line_vertical_center)),
            SegmentOption(LineVertical.Text, stringResource(R.string.line_vertical_text)),
        )) { v -> change { it.copy(vertical = v) } }
        SettingsSlider(stringResource(R.string.line_block_height), layout.requestedBlockHeight.toFloat(),
            LineLayout.BLOCK_HEIGHT.toRange(), 2f, "${layout.requestedBlockHeight}", Icons.Rounded.UnfoldLess, Icons.Rounded.UnfoldMore) { v ->
            change { it.copy(blockHeight = v.roundToInt()) }
        }
        Text(stringResource(R.string.line_block_minimum), style = FolioType.caption,
            color = Folio.colors.tertiaryLabel, modifier = Modifier.padding(horizontal = 18.dp).padding(bottom = 10.dp))
    } else if (!scroll) {
        SettingsSlider(stringResource(if (top) R.string.line_edge_top else R.string.line_edge_bottom), layout.edge.toFloat(), LineLayout.EDGE.toRange(), 2f,
            signed(layout.edge), Icons.Rounded.UnfoldLess, Icons.Rounded.UnfoldMore) { v -> change { it.copy(edge = v.roundToInt()) } }
    }

    SettingsDivider()
    SettingsSubheader(stringResource(R.string.line_group_separation))
    SettingsToggle(stringResource(R.string.line_rule), layout.rule) { on -> change { it.copy(rule = on) } }
    if (scroll && gap) {
        SettingsSlider(stringResource(R.string.line_text_gap), layout.distanceToText.toFloat(),
            LineLayout.TEXT_GAP.toRange(), 2f, "${layout.distanceToText}", Icons.Rounded.UnfoldLess, Icons.Rounded.UnfoldMore) { v ->
            change { it.copy(textGap = v.roundToInt()) }
        }
        SegmentsTitle(stringResource(R.string.scroll_boundary))
        val boundary = if (top) editor.settings.scrollHeaderBoundary else editor.settings.scrollFooterBoundary
        SettingsSegments(boundary, listOf(
            SegmentOption(FooterStyle.Clip, stringResource(R.string.scroll_boundary_clear)),
            SegmentOption(FooterStyle.Fade, stringResource(R.string.scroll_boundary_smooth)),
        )) { v -> editor.edit { it.editLineBoundary(top, v) } }
    } else if (!scroll && gap) {
        SettingsSlider(stringResource(R.string.line_gap), layout.gap.toFloat(), LineLayout.GAP.toRange(), 2f, signed(layout.gap),
            Icons.Rounded.UnfoldLess, Icons.Rounded.UnfoldMore) { v -> change { it.copy(gap = v.roundToInt()) } }
    }

    if (top) {
        SettingsDivider()
        SettingsSubheader(stringResource(R.string.line_group_behavior))
        SettingsToggle(stringResource(R.string.line_hide_chapter_start), layout.hideOnChapterStart) { on -> change { it.copy(hideOnChapterStart = on) } }
    }
    }
}


private fun IntRange.toRange() = first.toFloat()..last.toFloat()

/** «+4», «0», «−6»: a shift from the standard value (spacing, tone, contrast). */
internal fun signed(value: Int): String = when {
    value > 0 -> "+$value"
    value < 0 -> "−${-value}"
    else -> "0"
}

/** Quiet note in place of hidden line details; saved formatting returns when the line is shown again. */
@Composable
private fun HiddenLineHint(text: String) {
    Row(Modifier.padding(horizontal = 18.dp).padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Rounded.VisibilityOff, null, tint = Folio.colors.tertiaryLabel, modifier = Modifier.size(16.dp))
        Text(text, style = FolioType.footnote, color = Folio.colors.tertiaryLabel)
    }
}
