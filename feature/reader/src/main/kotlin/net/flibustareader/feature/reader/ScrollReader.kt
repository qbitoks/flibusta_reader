package net.flibustareader.feature.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import net.flibustareader.core.designsystem.ReaderPalette
import net.flibustareader.core.layout.ChapterPages
import net.flibustareader.core.layout.Mark
import net.flibustareader.core.layout.Page
import net.flibustareader.core.layout.PageItem
import net.flibustareader.core.layout.PageRenderer
import net.flibustareader.core.layout.TextRange
import net.flibustareader.core.model.Position
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

/** Height actually used by a page's content, so stacked pages read as continuous text. */
internal fun Page.contentHeight(): Float = items.maxOfOrNull {
    when (it) {
        is PageItem.Text -> it.top + it.height
        is PageItem.Image -> it.top + it.height
    }
} ?: 0f

/**
 * Start of the first line at least half visible below [y] in this page's content box: the line a
 * reader sees at the top. Below the last item, its last line.
 */
internal fun Page.positionAt(y: Float): Position {
    var last = start
    for (item in items) {
        when (item) {
            is PageItem.Text -> {
                val layout = item.layout
                val base = layout.getLineTop(item.firstLine)
                val local = base + (y - item.top).coerceAtLeast(0f)
                var line = layout.getLineForVertical(local.toInt()).coerceIn(item.firstLine, item.endLine - 1)
                if (local - layout.getLineTop(line) > (layout.getLineBottom(line) - layout.getLineTop(line)) / 2f) line++
                if (line < item.endLine) return Position(chapter, item.block, layout.getLineStart(line))
                last = Position(chapter, item.block, layout.getLineStart(item.endLine - 1))
            }
            is PageItem.Image -> {
                if (y < item.top + item.height / 2f) return Position(chapter, item.block, 0)
                last = Position(chapter, item.block, 0)
            }
        }
    }
    return last
}

/** Top of the line holding [position] in this page's content box. */
internal fun Page.topOf(position: Position): Float =
    PageRenderer.caret(this, position, atEnd = false)?.top
        ?: items.firstOrNull { it.block >= position.block }?.top
        ?: 0f

/** Key of a page's item in the scroll list. */
internal val Page.key: String get() = "$chapter:$index"

/** Where the scroll list's pages are on screen, for overlays above it (selection, highlight palette). */
internal class ScrollLocator {
    var list: LazyListState? = null
    var left = 0f

    /** Top-left of [page]'s content box in the reader's coordinates; null while it is off screen. */
    fun originOf(page: Page): Offset? {
        val list = list ?: return null
        val info = list.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.key == page.key } ?: return null
        return Offset(left, (item.offset + info.beforeContentPadding).toFloat() + page.lead)
    }
}

/**
 * Apple Books' "Scroll" page turn: the same laid-out pages stacked at their content height, each
 * with the spacing its page break swallowed ([Page.lead]), so the text reads as one ribbon. The
 * list holds a few chapters around the screen ([planScrollWindow]), follows jumps from
 * [ReaderViewModel.scrollJump] and reports the top line as the reading position.
 *
 * Tap, long press and the drag after it arrive in the page's content-box coordinates.
 */
@Composable
internal fun ScrollReader(
    vm: ReaderViewModel,
    generation: Int,
    palette: ReaderPalette,
    padding: PaddingValues,
    highlight: TextRange?,
    marks: List<Mark>,
    locator: ScrollLocator,
    onTap: (Page, Offset) -> Unit,
    onLongPress: (Page, Offset) -> Boolean,
    onLongPressDrag: (Page, Offset) -> Unit,
    onScrollStart: () -> Unit,
    pageOverlay: @Composable (Page) -> Unit = {},
) {
    key(generation) {
        val listState = rememberLazyListState()
        var chapters by remember { mutableStateOf<List<ChapterPages>>(emptyList()) }
        val pages by remember { derivedStateOf { chapters.flatMap { it.pages } } }
        /** False while a jump rebuilds the list, so the half-built list is not reported as the position. */
        var ready by remember { mutableStateOf(false) }
        val density = LocalDensity.current
        val marginPx = with(density) { padding.calculateLeftPadding(LayoutDirection.Ltr).toPx() }
        val chapterGap = with(density) { 48.dp.toPx() }
        val latestOnTap by rememberUpdatedState(onTap)
        val latestOnLongPress by rememberUpdatedState(onLongPress)
        val latestOnLongPressDrag by rememberUpdatedState(onLongPressDrag)
        val latestOnScrollStart by rememberUpdatedState(onScrollStart)
        val initialJump = remember { vm.scrollJump }
        /** Id of the last jump this list has shown; reports carry it so stale ones are dropped. */
        var shownJump by remember { mutableIntStateOf(initialJump?.id ?: 0) }

        SideEffect {
            locator.list = listState
            locator.left = marginPx
        }

        /** Puts [target]'s line at the top of the text; a chapter the list does not hold replaces the list. */
        suspend fun show(target: Position) {
            val count = vm.chapterCount
            if (count == 0) return
            val c = target.chapter.coerceIn(0, count - 1)
            ready = false
            try {
                val held = chapters.firstOrNull { it.chapter == c } ?: (vm.chapterPages(c) ?: return).also { loaded ->
                    chapters = listOf(loaded)
                    // Scrolling before the list has the new items would clamp the index to the old ones.
                    snapshotFlow { listState.layoutInfo }.first { info ->
                        info.totalItemsCount == loaded.pages.size && info.visibleItemsInfo.all { (it.key as String).startsWith("$c:") }
                    }
                }
                val index = held.pageIndexOf(target)
                val page = held.pages[index]
                val before = chapters.takeWhile { it.chapter != c }.sumOf { it.pages.size }
                listState.scrollToItem(before + index, (page.lead + page.topOf(target)).roundToInt())
            } finally {
                ready = true
            }
        }

        LaunchedEffect(Unit) {
            show(vm.readingPosition)
            snapshotFlow { vm.scrollJump }
                .filterNotNull()
                .collectLatest {
                    if (it !== initialJump) show(it.position)
                    shownJump = it.id
                }
        }

        // Pages by the keys the list measured, not by index: right after chapters are added in
        // front, indices and the page list disagree for a frame.
        fun pageOf(key: Any?): Page? {
            val (c, i) = (key as? String)?.split(':')?.mapNotNull(String::toIntOrNull)?.takeIf { it.size == 2 } ?: return null
            return chapters.firstOrNull { it.chapter == c }?.pages?.getOrNull(i)
        }

        // The line at the top of the text is the reading position.
        LaunchedEffect(listState) {
            snapshotFlow {
                if (!ready) return@snapshotFlow null
                val first = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == listState.firstVisibleItemIndex }
                pageOf(first?.key)?.let { it to listState.firstVisibleItemScrollOffset }
            }
                .filterNotNull()
                .distinctUntilChanged()
                .collect { (page, offset) -> vm.onScrolledTo(page, page.positionAt(offset - page.lead), shownJump) }
        }

        // Neighbouring chapters are laid out ahead of the reader; far ones are let go.
        LaunchedEffect(listState) {
            snapshotFlow {
                val visible = listState.layoutInfo.visibleItemsInfo
                val loaded = chapters
                if (!ready || loaded.isEmpty() || visible.isEmpty()) return@snapshotFlow null
                val first = pageOf(visible.first().key)?.chapter ?: return@snapshotFlow null
                val last = pageOf(visible.last().key)?.chapter ?: return@snapshotFlow null
                planScrollWindow(first, last, loaded.first().chapter..loaded.last().chapter, vm.chapterCount)
            }
                .filterNotNull()
                .distinctUntilChanged()
                .collectLatest { plan ->
                    plan.load?.let { load ->
                        val added = vm.chapterPages(load) ?: return@collectLatest
                        val now = chapters
                        if (now.isEmpty() || !ready) return@collectLatest
                        chapters = when (load) {
                            now.last().chapter + 1 -> now + added
                            now.first().chapter - 1 -> listOf(added) + now
                            else -> now
                        }
                    }
                    val kept = chapters.filter { it.chapter in plan.keep }
                    if (kept.size != chapters.size && kept.isNotEmpty()) chapters = kept
                }
        }

        // Only a finger starts a scroll; jumps move the list without hiding the controls.
        LaunchedEffect(listState) {
            listState.interactionSource.interactions.collect { if (it is DragInteraction.Start) latestOnScrollStart() }
        }

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding()),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(pages, key = { it.key }) { page ->
                val isChapterEnd = chapters.firstOrNull { it.chapter == page.chapter }?.pages?.lastIndex == page.index
                val height = page.lead + page.contentHeight() + if (isChapterEnd) chapterGap else 0f
                val origin = Offset(marginPx, page.lead)
                val lead = with(density) { page.lead.toDp() }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(with(density) { height.toDp() })
                        // Own layer: scrolling moves the recorded page instead of drawing its text again.
                        .graphicsLayer()
                        .pointerInput(page) {
                            pageGestures(
                                origin = origin,
                                onTap = { latestOnTap(page, it) },
                                onLongPress = { latestOnLongPress(page, it) },
                                onDrag = { latestOnLongPressDrag(page, it) },
                            )
                        },
                ) {
                    PageView(
                        page = page,
                        ink = vm.ink,
                        palette = palette,
                        highlight = highlight,
                        imageFile = vm::imageFile,
                        marks = marks,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                start = padding.calculateLeftPadding(LayoutDirection.Ltr),
                                end = padding.calculateRightPadding(LayoutDirection.Ltr),
                                top = lead,
                            ),
                    )
                    Box(Modifier.padding(start = padding.calculateLeftPadding(LayoutDirection.Ltr), top = lead)) { pageOverlay(page) }
                }
            }
        }
    }
}

/**
 * Tap, or a long press that may go on as a drag, without taking anything the list needs: a finger
 * that moves before the long-press timeout scrolls, and only a selection drag is consumed.
 */
private suspend fun PointerInputScope.pageGestures(
    origin: Offset,
    onTap: (Offset) -> Unit,
    onLongPress: (Offset) -> Boolean,
    onDrag: (Offset) -> Unit,
) = awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false)
    var held = false
    val up = try {
        withTimeout(viewConfiguration.longPressTimeoutMillis) { waitForUpOrCancellation() }
    } catch (_: PointerEventTimeoutCancellationException) {
        held = true
        null
    }
    if (up != null) {
        onTap(down.position - origin)
        return@awaitEachGesture
    }
    if (!held || !onLongPress(down.position - origin)) return@awaitEachGesture
    while (true) {
        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
        change.consume()
        if (!change.pressed) break
        if (change.positionChanged()) onDrag(change.position - origin)
    }
}
