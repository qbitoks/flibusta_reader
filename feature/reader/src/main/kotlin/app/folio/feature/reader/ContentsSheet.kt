package app.folio.feature.reader

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.RemoveCircle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folio.core.designsystem.BookCover
import app.folio.core.designsystem.ReaderPalette
import app.folio.core.designsystem.labelColor
import app.folio.core.designsystem.UiSans
import app.folio.core.designsystem.components.CircleButton
import app.folio.core.designsystem.components.CircleButtonStyle
import app.folio.core.designsystem.components.FolioSheet
import app.folio.core.designsystem.components.SegmentedControl
import app.folio.core.designsystem.components.SheetHeader
import app.folio.core.model.Position
import java.text.DateFormat
import java.util.Date
import androidx.compose.ui.text.font.FontFamily
import app.folio.core.data.db.HighlightEntity
import app.folio.core.designsystem.selectedRowColor
import app.folio.core.designsystem.sheetColor

internal fun ReaderPalette.sheet(): Color = sheetColor()
private fun ReaderPalette.selectedRow(): Color = selectedRowColor()
private fun ReaderPalette.rule(): Color = secondary.copy(alpha = 0.25f)

/**
 * Contents (guideline § 6.4): a header with the cover, title and «Страница X из Y»; chapters
 * named as in the book with their page numbers; the current chapter on a highlight.
 */
@Composable
internal fun ContentsSheet(vm: ReaderViewModel, palette: ReaderPalette, onDismiss: () -> Unit) {
    val state = vm.state
    FolioSheet(onDismiss = onDismiss, containerColor = palette.sheet()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 18.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            BookCover(state.title, null, vm.coverFile, Modifier.width(46.dp), elevation = 3.dp, titleSize = 6.sp)
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(state.title, color = palette.labelColor(), fontFamily = UiSans, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (state.globalPage != null && state.totalPages != null) {
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = palette.secondary)) { append(stringResource(R.string.reader_page_word) + " ") }
                            append(stringResource(R.string.reader_page_of, state.globalPage, state.totalPages))
                        },
                        color = palette.labelColor().copy(alpha = 0.85f), fontFamily = UiSans, fontSize = 15.sp,
                    )
                }
            }
            CircleButton(Icons.Rounded.Close, stringResource(R.string.reader_close), onDismiss)
        }
        HorizontalDivider(color = palette.rule(), thickness = 0.5.dp)
        val rows = vm.tocRows
        val currentIndex = state.currentTocIndex
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = ((currentIndex ?: 0) - 3).coerceAtLeast(0))
        LazyColumn(state = listState, modifier = Modifier.fillMaxHeight()) {
            items(rows.size) { i ->
                val row = rows[i]
                val selected = i == currentIndex
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 7.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(if (selected) palette.selectedRow() else Color.Transparent)
                        .clickable {
                            vm.goTo(row.entry.position)
                            onDismiss()
                        }
                        .heightIn(min = 50.dp)
                        .padding(start = 17.dp + (row.depth.coerceAtMost(3) * 16).dp, end = 17.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        row.entry.title,
                        color = palette.labelColor().copy(alpha = 0.85f),
                        fontFamily = UiSans,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    vm.pageNumberOf(row.entry.position)?.let {
                        Text(it.toString(), color = palette.secondary, fontFamily = UiSans, fontSize = 15.sp, modifier = Modifier.padding(start = 12.dp))
                    }
                }
                if (!selected && i + 1 != currentIndex) {
                    HorizontalDivider(color = palette.rule(), thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 24.dp))
                }
            }
        }
    }
}

/**
 * «Закладки и фрагменты» (white spot 4): segmented «Закладки / Выделенное»; a bookmark row has
 * the chapter title, the date and the page. The left button switches to removing items.
 */
@Composable
internal fun FragmentsSheet(vm: ReaderViewModel, palette: ReaderPalette, bookFont: FontFamily, onDismiss: () -> Unit) {
    val bookmarks by vm.bookmarks.collectAsStateWithLifecycle()
    val highlights by vm.highlights.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf(false) }
    FolioSheet(onDismiss = onDismiss, containerColor = palette.sheet()) {
        SheetHeader(
            title = vm.state.title,
            leading = { CircleButton(Icons.Rounded.Checklist, null, { editing = !editing }) },
            trailing = { CircleButton(Icons.Rounded.Check, stringResource(R.string.themes_done), onDismiss, style = CircleButtonStyle.Confirm) },
        )
        Box(Modifier.padding(horizontal = 20.dp)) {
            SegmentedControl(
                listOf(stringResource(R.string.reader_bookmarks), stringResource(R.string.reader_highlights)),
                selected = tab,
                onSelect = { tab = it },
            )
        }
        Spacer(Modifier.height(8.dp))
        if (tab == 1 && highlights.isNotEmpty()) {
            LazyColumn(Modifier.fillMaxHeight()) {
                items(highlights, key = { it.id }) { h ->
                    HighlightRow(vm, h, palette, bookFont, editing, onOpen = {
                        vm.spanOf(h)?.let { vm.goTo(it.start) }
                        onDismiss()
                    })
                    HorizontalDivider(color = palette.rule(), thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 24.dp))
                }
            }
            return@FolioSheet
        }
        if (tab == 1 || bookmarks.isEmpty()) {
            Text(
                stringResource(if (tab == 1) R.string.reader_no_highlights else R.string.reader_no_bookmarks),
                color = palette.secondary,
                fontFamily = UiSans,
                fontSize = 17.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 60.dp).padding(horizontal = 32.dp),
            )
            Spacer(Modifier.fillMaxHeight())
            return@FolioSheet
        }
        LazyColumn(Modifier.fillMaxHeight()) {
            items(bookmarks, key = { it.id }) { bm ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !editing) {
                            Position.decode(bm.position)?.let { vm.goTo(it) }
                            onDismiss()
                        }
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (editing) {
                        Icon(
                            Icons.Rounded.RemoveCircle, stringResource(R.string.reader_delete), tint = Color(0xFFFF3B30),
                            modifier = Modifier.padding(end = 14.dp).size(24.dp).clip(RoundedCornerShape(12.dp)).clickable { vm.deleteBookmark(bm.id) },
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(bm.chapterTitle ?: stringResource(R.string.reader_untitled), color = palette.labelColor(), fontFamily = UiSans, fontSize = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(relativeDate(bm.createdAt), color = palette.secondary, fontFamily = UiSans, fontSize = 15.sp)
                    }
                    Position.decode(bm.position)?.let(vm::pageNumberOf)?.let {
                        Text(it.toString(), color = palette.secondary, fontFamily = UiSans, fontSize = 18.sp)
                    }
                }
                HorizontalDivider(color = palette.rule(), thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 24.dp))
            }
        }
    }
}

/** «Выделенное» row (#27): the passage in the book's font with the highlight on yellow, the note, the date. */
@Composable
private fun HighlightRow(
    vm: ReaderViewModel,
    h: HighlightEntity,
    palette: ReaderPalette,
    bookFont: FontFamily,
    editing: Boolean,
    onOpen: () -> Unit,
) {
    val span = vm.spanOf(h)
    val (before, after) = remember(h.id) { span?.let(vm::contextOf) ?: ("" to "") }
    val passage = buildAnnotatedString {
        append(before)
        val color = HighlightColor.of(h.color)
        val style = if (color == HighlightColor.Underline) {
            SpanStyle(textDecoration = TextDecoration.Underline, color = color.base)
        } else {
            SpanStyle(background = color.dot.copy(alpha = if (palette.isDark) 0.5f else 0.85f))
        }
        withStyle(style) { append(h.text) }
        append(after)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = !editing, onClick = onOpen)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (editing) {
            Icon(
                Icons.Rounded.RemoveCircle, stringResource(R.string.reader_delete), tint = Color(0xFFFF3B30),
                modifier = Modifier.padding(end = 14.dp, top = 2.dp).size(24.dp).clip(RoundedCornerShape(12.dp)).clickable { vm.deleteHighlight(h.id) },
            )
        }
        Column(Modifier.weight(1f)) {
            Text(passage, color = palette.labelColor(), fontFamily = bookFont, fontSize = 18.sp, lineHeight = 25.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            h.note?.let {
                Text(it, color = palette.labelColor(), fontFamily = UiSans, fontSize = 15.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
            }
            Text(relativeDate(h.createdAt), color = palette.secondary, fontFamily = UiSans, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
        }
        span?.start?.let(vm::pageNumberOf)?.let {
            Text(it.toString(), color = palette.secondary, fontFamily = UiSans, fontSize = 18.sp, modifier = Modifier.padding(start = 12.dp))
        }
    }
}

@Composable
private fun relativeDate(time: Long): String = when {
    DateUtils.isToday(time) -> stringResource(R.string.reader_today)
    DateUtils.isToday(time + DateUtils.DAY_IN_MILLIS) -> stringResource(R.string.reader_yesterday)
    else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(time))
}
