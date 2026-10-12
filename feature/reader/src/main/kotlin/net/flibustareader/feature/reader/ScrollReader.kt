package net.flibustareader.feature.reader

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import net.flibustareader.core.designsystem.ReaderPalette
import net.flibustareader.core.layout.ChapterPages
import net.flibustareader.core.layout.Mark
import net.flibustareader.core.layout.Page
import net.flibustareader.core.layout.PageItem
import kotlinx.coroutines.flow.distinctUntilChanged

/** Height actually used by a page's content, so stacked pages read as continuous text. */
internal fun Page.contentHeight(): Float = items.maxOfOrNull {
    when (it) {
        is PageItem.Text -> it.top + it.height
        is PageItem.Image -> it.top + it.height
    }
} ?: 0f

/**
 * Apple Books' "Scroll" page turn: the same laid-out pages, stacked vertically at their
 * content height. Chapters are appended and prepended as the reader approaches them.
 */
@Composable
internal fun ScrollReader(
    vm: ReaderViewModel,
    palette: ReaderPalette,
    padding: PaddingValues,
    onTapPage: (Page, Offset) -> Unit,
    onScrollStart: () -> Unit,
    marks: List<Mark> = emptyList(),
) {
    key(vm.state.layoutGeneration) {
        val start = remember { vm.state.current ?: PageRef(0, 0) }
        var chapters by remember { mutableStateOf<List<ChapterPages>>(emptyList()) }
        var loading by remember { mutableStateOf(false) }
        val listState = rememberLazyListState()
        val density = LocalDensity.current
        val marginPx = with(density) { padding.calculateLeftPadding(LayoutDirection.Ltr).toPx() }
        val chapterGap = 48.dp
        val pages = remember(chapters) { chapters.flatMap { it.pages } }

        LaunchedEffect(Unit) {
            val first = vm.chapterPages(start.chapter) ?: return@LaunchedEffect
            chapters = listOf(first)
            listState.scrollToItem(start.index.coerceIn(0, first.pages.lastIndex))
        }

        LaunchedEffect(listState, pages) {
            if (pages.isEmpty()) return@LaunchedEffect
            snapshotFlow { listState.firstVisibleItemIndex to (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) }
                .distinctUntilChanged()
                .collect { (first, last) ->
                    pages.getOrNull(first)?.let(vm::onScrolledTo)
                    if (loading) return@collect
                    val lastChapter = chapters.last().chapter
                    val firstChapter = chapters.first().chapter
                    if (last >= pages.size - 3 && lastChapter + 1 < vm.chapterCount) {
                        loading = true
                        vm.chapterPages(lastChapter + 1)?.let { chapters = chapters + it }
                        loading = false
                    } else if (first <= 2 && firstChapter > 0) {
                        loading = true
                        vm.chapterPages(firstChapter - 1)?.let { chapters = listOf(it) + chapters }
                        loading = false
                    }
                }
        }

        LaunchedEffect(listState) {
            snapshotFlow { listState.isScrollInProgress }.collect { if (it) onScrollStart() }
        }

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding()),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(pages, key = { "${it.chapter}:${it.index}" }) { page ->
                val isChapterEnd = chapters.firstOrNull { it.chapter == page.chapter }?.pages?.lastIndex == page.index
                val height = with(density) { page.contentHeight().toDp() } + if (isChapterEnd) chapterGap else 0.dp
                PageView(
                    page = page,
                    ink = vm.ink,
                    palette = palette,
                    highlight = vm.state.highlight,
                    imageFile = vm::imageFile,
                    marks = marks,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(height)
                        .pointerInput(page) { detectTapGestures { onTapPage(page, it - Offset(marginPx, 0f)) } }
                        .padding(start = padding.calculateLeftPadding(LayoutDirection.Ltr), end = padding.calculateRightPadding(LayoutDirection.Ltr)),
                )
            }
        }
    }
}
