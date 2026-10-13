package net.flibustareader.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import net.flibustareader.core.data.db.HighlightEntity
import net.flibustareader.core.data.settings.FooterStyle
import net.flibustareader.core.data.settings.PageHeader
import net.flibustareader.core.data.settings.PageTurn
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import net.flibustareader.core.designsystem.ReaderPalette
import net.flibustareader.core.layout.PageRenderer
import net.flibustareader.core.layout.Mark
import net.flibustareader.core.layout.MarkStyle
import net.flibustareader.core.data.settings.ReaderSettings

internal data class ReaderLayout(
    val padding: PaddingValues,
    val topInset: Dp,
    val bottomInset: Dp,
    val height: Int,
    val origin: Offset,
) {
    val headerY: Dp get() = topInset + 14.dp
    val footerY: Dp get() = bottomInset + 16.dp
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReaderSurface(
    vm: ReaderViewModel,
    settings: ReaderSettings,
    palette: ReaderPalette,
    controls: ReaderControls,
    fragments: Int,
    highlights: List<HighlightEntity>,
    onClose: () -> Unit,
) {
    val state = vm.state
    val context = LocalContext.current
    val locator = remember { ScrollLocator() }

    val density = LocalDensity.current
    // Insets ignoring visibility: showing or hiding the bars must never re-paginate the book.
    val topInset = with(density) { WindowInsets.statusBarsIgnoringVisibility.getTop(density).toDp() }
    val bottomInset = with(density) { WindowInsets.navigationBarsIgnoringVisibility.getBottom(density).toDp() }
    val header = settings.header
    val footer = settings.footer
    val margin = settings.style.margin.dp
    // Without a line above, the text starts right under the status bar area; without anything
    // below, it runs to the bottom. Changing either re-paginates, as a font change does.
    val padding = PaddingValues(
        start = margin,
        end = margin,
        top = topInset + if (header == PageHeader.None) 12.dp else 56.dp,
        bottom = bottomInset + if (footer.isEmpty) 20.dp else 60.dp,
    )

    // «↶» shows by itself for a moment after a jump, until the first page turn; otherwise only with the controls.
    var backFlash by remember { mutableStateOf(false) }
    LaunchedEffect(vm.landedJumps) {
        if (vm.landedJumps == 0) return@LaunchedEffect
        val landed = vm.state.current
        backFlash = true
        withTimeoutOrNull(3000) { snapshotFlow { vm.state.current }.first { it != landed } }
        backFlash = false
    }

    val clock by rememberClock(footer.clock)
    val battery by rememberBattery(footer.battery)
    val footerWords = FooterWords(
        pageOf = { n, total -> context.getString(R.string.reader_page_of, n, total) },
        percent = { context.getString(R.string.reader_percent, it) },
        pagesLeft = { context.resources.getQuantityString(R.plurals.footer_left_in_chapter, it, it) },
        chapterEnd = stringResource(R.string.reader_chapter_end),
    )
    BoxWithConstraints(Modifier.fillMaxSize().background(palette.background)) {
        val widthPx = constraints.maxWidth
        val heightPx = constraints.maxHeight
        val contentLeft = with(density) { padding.calculateLeftPadding(LayoutDirection.Ltr).toPx() }
        val contentTop = with(density) { padding.calculateTopPadding().toPx() }
        LaunchedEffect(widthPx, heightPx, padding, density) {
            val w = widthPx - with(density) { (padding.calculateLeftPadding(LayoutDirection.Ltr) + padding.calculateRightPadding(LayoutDirection.Ltr)).toPx() }
            val h = heightPx - with(density) { (padding.calculateTopPadding() + padding.calculateBottomPadding()).toPx() }
            vm.onViewport(Viewport(w.toInt(), h.toInt(), density.density, density.fontScale))
        }
        val scroll = settings.pageTurn == PageTurn.Scroll
        val guideOn = settings.guideLine && !scroll
        val marks = controls.chromeVisible || backFlash
        val guideLines = remember(state.page, guideOn) { state.page?.takeIf { guideOn }?.let(PageRenderer::lines).orEmpty() }
        LaunchedEffect(state.page) {
            controls.guideIndex = if (controls.guideToLast) guideLines.lastIndex.coerceAtLeast(0) else 0
            controls.guideToLast = false
        }
        val guidePlate: Rect? = if (!guideOn) null else guideLines.getOrNull(controls.guideIndex.coerceAtMost(guideLines.lastIndex))?.let { line ->
            val padX = with(density) { 14.dp.toPx() }
            val padY = with(density) { 3.dp.toPx() }
            Rect(contentLeft + line.left - padX, contentTop + line.top - padY, contentLeft + line.right + padX, contentTop + line.bottom + padY)
        }
        // Saved highlights (yellow) and the live selection (blue) drawn under the text.
        val cornerPx = with(density) { 4.dp.toPx() }
        val underlinePx = with(density) { 2.dp.toPx() }
        val savedMarks = remember(highlights, palette, controls.activeHighlight, cornerPx) {
            highlights.mapNotNull { h ->
                val span = vm.spanOf(h) ?: return@mapNotNull null
                val color = HighlightColor.of(h.color)
                val paint = highlightPaint(color, palette, active = h.id == controls.activeHighlight).toArgb()
                if (color == HighlightColor.Underline) Mark(span, paint, MarkStyle.Underline, underlinePx) else Mark(span, paint, MarkStyle.Highlight, cornerPx)
            }
        }
        LaunchedEffect(state.page) {
            vm.select(null)
            controls.activeHighlight = null
        }
        val contentOrigin = Offset(contentLeft, contentTop)
        val layout = ReaderLayout(padding, topInset, bottomInset, heightPx, contentOrigin)
        val interaction = readerInteractions(vm, controls, guidePlate, guideLines, contentOrigin)
        ReaderPages(vm, settings, palette, controls, layout, savedMarks, highlights, marks, interaction, locator, footerWords, clock, battery, onClose)
        // Scrolling text dissolves under the lines above and below it, or passes under the strip.
        if (scroll) {
            // Solid behind the title and the bottom line, fading only over the last few dp before the text.
            EdgeFade(palette, padding.calculateTopPadding(), top = true, modifier = Modifier.align(Alignment.TopCenter), fade = 18.dp)
            if (!footer.isEmpty && footer.style == FooterStyle.Strip) {
                FooterStrip(palette, bottomInset + FooterStripHeight, Modifier.align(Alignment.BottomCenter))
            } else {
                EdgeFade(palette, padding.calculateBottomPadding(), top = false, modifier = Modifier.align(Alignment.BottomCenter), fade = 12.dp)
            }
        }
        guidePlate?.let {
            val textArea = Rect(0f, contentTop - with(density) { 6.dp.toPx() }, widthPx.toFloat(), heightPx - with(density) { padding.calculateBottomPadding().toPx() - 6.dp.toPx() })
            GuideLineOverlay(it, textArea, settings.guideDim.fade, palette)
        }
        ReaderSelectionOverlays(vm, settings, palette, controls, layout, highlights, locator)
        ReaderChrome(vm, settings, palette, controls, layout, fragments, marks, footerWords, clock, battery, onClose)
    }
}
