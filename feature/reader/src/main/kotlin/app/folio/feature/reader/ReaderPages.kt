package app.folio.feature.reader

import androidx.compose.runtime.getValue
import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.folio.core.data.db.HighlightEntity
import app.folio.core.data.settings.FooterStyle
import app.folio.core.data.settings.LineAlign
import app.folio.core.data.settings.PageHeader
import app.folio.core.data.settings.PageTurn
import app.folio.core.designsystem.ReaderPalette
import app.folio.core.designsystem.readerChromeSeparator
import app.folio.core.layout.Mark
import androidx.compose.foundation.layout.BoxScope
import app.folio.core.data.settings.ReaderSettings

@Composable
internal fun BoxScope.ReaderPages(
    vm: ReaderViewModel,
    settings: ReaderSettings,
    palette: ReaderPalette,
    controls: ReaderControls,
    layout: ReaderLayout,
    savedMarks: List<Mark>,
    highlights: List<HighlightEntity>,
    marks: Boolean,
    interaction: ReaderInteractions,
    locator: ScrollLocator,
    footerWords: FooterWords,
    clock: String?,
    battery: BatteryInfo?,
    onClose: () -> Unit,
) {
    val state = vm.state
    val scroll = settings.pageTurn == PageTurn.Scroll
    val selection = vm.selection
    val selectionColor = selectionFill(palette).toArgb()
    val padding = layout.padding
    val contentOrigin = layout.origin
    val header = settings.header
    val footer = settings.shownFooter
    val headerY = layout.headerY
    val footerY = layout.footerY
    val margin = layout.padding.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr)
    val headerLook = headerLook(settings, palette)
    val footerLook = footerLook(settings, palette)
    val footerStart = if (settings.guideLine && !scroll) (74.dp - margin).coerceAtLeast(0.dp) else 0.dp
    val footerEnd = 0.dp
    val headerAlpha by animateFloatAsState(if (marks) 0f else 1f, tween(150), label = "pageHeader")
    val footerAlpha by animateFloatAsState(if (controls.chromeVisible) 0f else 1f, tween(150), label = "pageFooter")
    when {
        state.error -> Text(
            stringResource(R.string.reader_error),
            color = palette.secondary,
            modifier = Modifier.align(Alignment.Center).padding(32.dp),
            textAlign = TextAlign.Center,
        )
        state.page == null -> CircularProgressIndicator(
            color = palette.secondary,
            strokeWidth = 2.dp,
            modifier = Modifier.align(Alignment.Center).size(28.dp),
        )
        scroll -> ScrollReader(
            vm = vm,
            generation = state.layoutGeneration,
            palette = palette,
            padding = padding,
            highlight = state.highlight,
            marks = if (selection != null && controls.focusPage != null) savedMarks + Mark(selection, selectionColor) else savedMarks,
            locator = locator,
            onTap = { page, local -> if (!interaction.tapOnPage(page, local)) controls.chromeVisible = !controls.chromeVisible },
            onLongPress = interaction.startSelection,
            onLongPressDrag = interaction.extendSelection,
            onScrollStart = {
                controls.chromeVisible = false
                vm.select(null)
                controls.activeHighlight = null
            },
            pageOverlay = { page -> NoteMarkers(page, Offset.Zero, highlights, vm, palette) { controls.noteFor = it } },
            modifier = run {
                val top = settings.scrollHeaderBoundary.takeUnless { header == PageHeader.None } ?: FooterStyle.None
                val bottom = settings.scrollFooterBoundary.takeUnless { footer.isEmpty } ?: FooterStyle.None
                Modifier.scrollEdges(top, padding.calculateTopPadding(), bottom, padding.calculateBottomPadding())
            },
        )
        else -> HorizontalPageTurner(
            current = state.page,
            prev = state.prev,
            next = state.next,
            canPrev = state.hasPrev,
            canNext = state.hasNext,
            mode = when {
                settings.pageTurn == PageTurn.Fade -> TurnMode.Fade
                settings.pageTurn == PageTurn.Curl && PageCurl.supported -> TurnMode.Curl
                else -> TurnMode.Slide
            },
            onNext = vm::next,
            onPrev = vm::prev,
            onTap = interaction.onTap,
            onTurnEvent = interaction.onTurnEvent,
            onDragStart = {
                controls.chromeVisible = false
                vm.clearHighlight()
                vm.select(null)
                controls.activeHighlight = null
            },
            onSwipeDown = onClose,
            pageColor = palette.background,
            onLongPress = { offset -> vm.state.page?.let { interaction.startSelection(it, offset - contentOrigin) } ?: false },
            onLongPressDrag = { offset -> vm.state.page?.let { interaction.extendSelection(it, offset - contentOrigin) } },
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            key(state.layoutGeneration) {
                // Title and page number belong to the page and slide with it (guideline § 12).
                Box(Modifier.fillMaxSize()) {
                    PageView(
                        page = page,
                        ink = vm.ink,
                        palette = palette,
                        highlight = state.highlight,
                        imageFile = vm::imageFile,
                        modifier = Modifier.fillMaxSize().padding(padding),
                        marks = if (page != null && page === state.page && selection != null) savedMarks + Mark(selection, selectionColor) else savedMarks,
                    )
                    // Like a printed book, a chapter's opening page may go without its running title.
                    val chapterStart = page != null && page.index == 0 && headerLook.layout.hideOnChapterStart
                    headerText(header, state.title, page?.let { vm.chapterTitleAt(vm.chapterPositionOf(it)) })?.takeUnless { chapterStart }?.let { title ->
                        PageTitle(
                            headerLook.text(title), palette, Modifier.align(Alignment.TopCenter).padding(top = headerY).alpha(headerAlpha),
                            headerLook.titleMargin(24.dp, margin), headerLook,
                        )
                    }
                    if (!footer.isEmpty && page != null) {
                        PageFooterBar(
                            center = footerLook.text(footerCenter(footer, vm.pageNumberOf(page.start), state.totalPages, vm.progressOf(page), vm.pagesLeftAfter(page), footerWords, readerChromeSeparator(settings.chromeStyles.footer))),
                            clock = clock,
                            battery = battery,
                            palette = palette,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(start = margin, end = margin, bottom = footerY + 12.dp).alpha(footerAlpha),
                            textStyle = footerLook.style,
                            spread = footerLook.layout.align == LineAlign.Spread,
                            rule = footerLook.ruleColor.takeIf { footerLook.layout.rule },
                            startInset = footerStart,
                            endInset = footerEnd,
                            onCenterTap = interaction.onFooterTap,
                        )
                    }
                }
            }
        }
    }
}
