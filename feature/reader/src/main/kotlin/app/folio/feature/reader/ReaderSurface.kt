package app.folio.feature.reader

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.padding
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
import app.folio.core.data.db.HighlightEntity
import app.folio.core.data.settings.GuideFalloff
import app.folio.core.data.settings.PageTurn
import app.folio.core.designsystem.ReaderPalette
import app.folio.core.designsystem.components.GlassScope
import app.folio.core.layout.PageRenderer
import app.folio.core.layout.Mark
import app.folio.core.layout.MarkStyle
import app.folio.core.data.settings.ReaderSettings

internal data class ReaderLayout(
    val padding: PaddingValues,
    val topInset: Dp,
    val bottomInset: Dp,
    val height: Int,
    val origin: Offset,
    /** «Отступ от края» of the lines. */
    val headerEdge: Dp = 0.dp,
    val footerEdge: Dp = 0.dp,
) {
    val headerY: Dp get() = topInset + 14.dp + headerEdge
    val footerY: Dp get() = bottomInset + 16.dp + footerEdge
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
    val footer = settings.shownFooter
    val headerStyle = headerLook(settings, palette).style
    val footerStyle = footerLook(settings, palette).style
    val (topSpace, bottomSpace) = textInsets(settings,
        if (settings.pageTurn == PageTurn.Scroll) lineLabelHeight(headerLook(settings, palette)) else with(density) { headerStyle.lineHeight.toDp() },
        if (settings.pageTurn == PageTurn.Scroll) lineLabelHeight(footerLook(settings, palette)) else with(density) { footerStyle.lineHeight.toDp() })

    val clock by rememberClock(footer.clock)
    val battery by rememberBattery(footer.battery)
    val footerWords = FooterWords(
        pageOf = { n, total -> context.getString(R.string.reader_page_of, n, total) },
        percent = { context.getString(R.string.reader_percent, it) },
        pagesLeft = { context.resources.getQuantityString(R.plurals.footer_left_in_chapter, it, it) },
        chapterEnd = stringResource(R.string.reader_chapter_end),
    )
    GlassScope { source ->
    BoxWithConstraints(Modifier.fillMaxSize().background(palette.background)) {
        val margin = settings.style.horizontalMargin(maxWidth.value).dp
        val padding = PaddingValues(start = margin, end = margin, top = topInset + topSpace, bottom = bottomInset + bottomSpace)
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
        val marks = controls.chromeVisible
        val guideLines = remember(state.page, guideOn) { state.page?.takeIf { guideOn }?.let(PageRenderer::lines).orEmpty() }
        val jumpId = vm.scrollJump?.id ?: 0
        val guide = controls.guide.landed(state.current, state.layoutGeneration, guideLines.size, jumpId = jumpId)
        LaunchedEffect(state.page, state.layoutGeneration, jumpId) {
            controls.guide = controls.guide.landed(state.current, state.layoutGeneration, guideLines.size, jumpId = jumpId)
        }
        LaunchedEffect(guideOn) {
            controls.guideMoving = false
            controls.guide = controls.guide.landed(state.current, state.layoutGeneration, guideLines.size, reset = true, jumpId = jumpId)
        }
        // The tap target is the whole drawn plate: every fully lit line of the window, as `GuidePage` frames it.
        val guidePlate: Rect? = if (!guideOn) null else guideLines.getOrNull(guide.index)?.let { line ->
            val window = settings.guideWindow
            val full = window.falloff == GuideFalloff.None
            val first = guideLines[(guide.index - if (full) window.above else 0).coerceAtLeast(0)]
            val last = guideLines[(guide.index + if (full) window.below else 0).coerceAtMost(guideLines.size - 1)]
            val padX = with(density) { 14.dp.toPx() }
            val padY = with(density) { 3.dp.toPx() }
            Rect(contentLeft + line.left - padX, contentTop + first.top - padY, contentLeft + line.right + padX, contentTop + last.bottom + padY)
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
        val speech by vm.speechState.collectAsStateWithLifecycle()
        NoVoiceEffect(vm, speech)
        val pageMarks = speechMark(speech, vm.bookId, palette, cornerPx)?.let { savedMarks + it } ?: savedMarks
        LaunchedEffect(state.page) {
            vm.select(null)
            controls.activeHighlight = null
        }
        val contentOrigin = Offset(contentLeft, contentTop)
        val layout = ReaderLayout(padding, topInset, bottomInset, heightPx, contentOrigin,
            settings.shownHeaderLayout.edge.dp, settings.shownFooterLayout.edge.dp)
        val interaction = readerInteractions(vm, controls, guidePlate, guideLines, contentOrigin) { event ->
            controls.guideMoving = event.active
            val landed = event.to
            // A requested direction is useful only after that exact page becomes current.
            if (landed != null && landed !== event.from && vm.state.page === landed) {
                controls.guide = controls.guide.landed(
                    PageRef(landed.chapter, landed.index), vm.state.layoutGeneration,
                    PageRenderer.lines(landed).size, direction = event.direction, jumpId = vm.scrollJump?.id ?: 0,
                )
            }
        }
        Box(Modifier.fillMaxSize().then(source)) {
            ReaderPages(vm, settings, palette, controls, layout, pageMarks, highlights, marks, interaction, locator, footerWords, clock, battery, onClose)
        }
        ReaderSelectionOverlays(vm, settings, palette, controls, layout, highlights, locator)
        ReaderChrome(vm, settings, palette, controls, layout, fragments, marks, footerWords, clock, battery, onClose, interaction.onFooterTap,
            compact = maxHeight < 500.dp, screenHeight = maxHeight)
        androidx.compose.animation.AnimatedVisibility(speech.bookId == vm.bookId && !controls.chromeVisible,
            Modifier.align(Alignment.BottomCenter).padding(bottom = layout.bottomInset + 28.dp),
            enter = androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.fadeOut()) {
            SpeechMiniPlayer(vm, speech, palette)
        }
    }
    }
}
