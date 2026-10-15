package app.folio.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import app.folio.core.data.settings.*
import app.folio.core.designsystem.*
import app.folio.core.layout.*
import app.folio.core.model.Block
import app.folio.core.model.Chapter
import app.folio.core.model.TextKind
import java.io.File

/** What the preview shows: a bounded excerpt, the titles and the page numbers of its first page. */
internal class PreviewSource(
    val blocks: List<Block>,
    val title: String,
    val chapterTitle: String?,
    val page: Int,
    val total: Int,
    val progress: Float,
    val pagesLeft: Int,
    val language: String?,
    val imageFile: (String) -> File,
)

/**
 * A neutral sample, also inside a book: the book's numbers change with every re-pagination and made the
 * preview and the footer capsules flicker.
 */
@Composable
internal fun samplePreviewSource(): PreviewSource {
    val blocks = previewSampleBlocks()
    val title = stringResource(R.string.settings_preview_book)
    val chapter = stringResource(R.string.settings_preview_chapter)
    return remember(blocks, title, chapter) {
        PreviewSource(blocks, title, chapter, 42, 586, 0.07f, 5, null) { File(it) }
    }
}

/**
 * Short, fixed text that exercises the settings: a heading, dialogue, long words for hyphenation and
 * justification, digits and quotes. Short so each change re-paginates at once.
 */
@Composable
private fun previewSampleBlocks(): List<Block> {
    val heading = stringResource(R.string.themes_preview_heading)
    val paragraphs = listOf(R.string.themes_preview_text, R.string.themes_preview_text2,
        R.string.themes_preview_text3, R.string.themes_preview_text4).map { stringResource(it) }
    return remember(heading, paragraphs) {
        listOf(Block.Text(heading, kind = TextKind.Heading2)) + paragraphs.map { Block.Text(it) }
    }
}

/**
 * Fixed, readable excerpt using the book renderer; pagination here never updates the reading viewport.
 * The lines lie over the text as in the reader, so with «Прокрутка» the excerpt scrolls under them
 * and shows «Под строкой при прокрутке». Paged modes turn the excerpt's pages by swipe with the chosen
 * animation; taps stay inert.
 */
@Composable
internal fun PageSettingsPreview(
    source: PreviewSource,
    settings: ReaderSettings,
    palette: ReaderPalette,
    modifier: Modifier = Modifier,
) {
    val chapter = remember(source.blocks) { Chapter(null, source.blocks) }
    val headerLook = headerLook(settings, palette)
    val footerLook = footerLook(settings, palette)
    val header = headerText(settings.header, source.title, source.chapterTitle)
    val footer = settings.shownFooter
    val clock by rememberClock(footer.clock)
    val battery by rememberBattery(footer.battery)
    val resources = LocalContext.current.resources
    val density = LocalDensity.current
    val words = FooterWords(
        { n, total -> resources.getString(R.string.reader_page_of, n, total) },
        { resources.getString(R.string.reader_percent, it) },
        { resources.getQuantityString(R.plurals.footer_left_in_chapter, it, it) },
        stringResource(R.string.reader_chapter_end),
    )
    // The excerpt's n-th page counts on from the source's numbers, never from the book; pages left are the excerpt's own.
    fun footerOf(n: Int, count: Int) = footerLook.text(footerCenter(footer, source.page + n, source.total,
        source.progress + n.toFloat() / source.total, (count - 1 - n).coerceAtLeast(0), words,
        readerChromeSeparator(settings.chromeStyles.footer)))
    val description = stringResource(R.string.settings_page_preview)
    val scroll = settings.pageTurn == PageTurn.Scroll
    val scrollInsets = textInsets(settings, lineLabelHeight(headerLook), lineLabelHeight(footerLook))
    var headerHeight by remember { mutableStateOf(0.dp) }
    var footerHeight by remember { mutableStateOf(0.dp) }
    BoxWithConstraints(modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(16.dp))
        .background(palette.background).border(0.5.dp, Folio.colors.separator, RoundedCornerShape(16.dp))
        .semantics { contentDescription = description }) {
        val margin = settings.style.horizontalMargin(maxWidth.value).dp
        if (scroll) {
            val padding = PaddingValues(start = margin, end = margin, top = scrollInsets.first, bottom = scrollInsets.second)
            val (pages, ink) = previewPages(chapter, source, settings, palette, padding, maxWidth, maxHeight)
            ScrollText(pages, ink, source, settings, palette, padding)
            if (header != null) ReaderLineBlock(true, headerLook, margin,
                Modifier.align(Alignment.TopCenter)) {
                BlockTitle(header, headerLook, headerLook.titleMargin(24.dp, margin))
            }
            if (!footer.isEmpty) ReaderLineBlock(false, footerLook, margin,
                Modifier.align(Alignment.BottomCenter)) {
                PageFooterBar(
                    center = footerOf(0, pages.size),
                    clock = clock, battery = battery, palette = palette, textStyle = footerLook.style,
                    spread = footerLook.layout.align == LineAlign.Spread,
                    modifier = Modifier.padding(horizontal = margin),
                )
            }
            return@BoxWithConstraints
        }
        val padding = PaddingValues(start = margin, end = margin, top = headerHeight, bottom = footerHeight)
        val (pages, ink) = previewPages(chapter, source, settings, palette, padding, maxWidth, maxHeight)
        // Stays on its page across re-paginations while that page still exists.
        var index by remember { mutableIntStateOf(0) }
        val last = (pages.size - 1).coerceAtLeast(0)
        val shown = index.coerceIn(0, last)
        // The guide steps by taps like in the reader: forward lands on the first row, back on the last.
        var guideRow by remember { mutableIntStateOf(0) }
        fun rowsOf(i: Int) = pages.getOrNull(i)?.let { PageRenderer.lines(it).size } ?: 0
        // «Отступ сверху/снизу» move a line from the edge, «Отступ до текста» from the text; negatives shrink the default gap.
        val headerLayout = settings.shownHeaderLayout
        val footerLayout = settings.shownFooterLayout
        HorizontalPageTurner(
            current = pages.getOrNull(shown),
            prev = pages.getOrNull(shown - 1),
            next = pages.getOrNull(shown + 1),
            canPrev = shown > 0,
            canNext = shown < last,
            mode = when {
                settings.pageTurn == PageTurn.Fade -> TurnMode.None
                settings.pageTurn == PageTurn.Curl && PageCurl.supported -> TurnMode.Curl
                else -> TurnMode.Slide
            },
            onNext = { (shown < last).also { if (it) { index = shown + 1; guideRow = 0 } } },
            onPrev = { (shown > 0).also { if (it) { index = shown - 1; guideRow = (rowsOf(shown - 1) - 1).coerceAtLeast(0) } } },
            onTap = { offset, width ->
                if (!settings.guideLine) TapAction.Handled
                else GuideNavigation(index = guideRow).step(if (offset.x >= width / 2) 1 else -1, rowsOf(shown)).let {
                    guideRow = it.navigation.index
                    it.action
                }
            },
            onDragStart = {},
            onSwipeDown = {},
            pageColor = palette.background,
            pageCorner = 16.dp,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            // Title and page number belong to the page and slide with it, as in the reader.
            val n = pages.indexOf(page).coerceAtLeast(0)
            Box(Modifier.fillMaxSize().background(palette.background)) {
                PageView(page, ink, palette, null, source.imageFile, Modifier.fillMaxSize().padding(padding).clipToBounds())
                if (settings.guideLine && page != null) key(page) {
                    val lines = remember(page) { PageRenderer.lines(page) }
                    val row = when (n) {
                        shown -> guideRow
                        shown - 1 -> lines.size - 1
                        else -> 0
                    }
                    GuidePage(lines, row, padding, settings.guideDim.fade, palette, window = settings.guideWindow)
                }
                Column(Modifier.align(Alignment.TopCenter).fillMaxWidth()
                    .onSizeChanged { headerHeight = with(density) { it.height.toDp() } }) {
                    Spacer(Modifier.height(10.dp))
                    if (header != null) {
                        Spacer(Modifier.height(headerLayout.edge.coerceAtLeast(0).dp))
                        Text(headerLook.text(header), style = headerLook.style, modifier = Modifier.fillMaxWidth().padding(horizontal = margin, vertical = 4.dp),
                            textAlign = headerLook.textAlign, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (headerLook.layout.rule) LineRule(headerLook.ruleColor, Modifier.padding(horizontal = margin, vertical = 2.dp))
                        Spacer(Modifier.height((12 + headerLayout.gap).coerceAtLeast(0).dp))
                    }
                }
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .onSizeChanged { footerHeight = with(density) { it.height.toDp() } }) {
                    if (!footer.isEmpty) {
                        Spacer(Modifier.height((12 + footerLayout.gap).coerceAtLeast(0).dp))
                        PageFooterBar(
                            center = footerOf(n, pages.size),
                            clock = clock, battery = battery, palette = palette, textStyle = footerLook.style,
                            spread = footerLook.layout.align == LineAlign.Spread,
                            rule = footerLook.ruleColor.takeIf { footerLook.layout.rule },
                            modifier = Modifier.padding(horizontal = margin, vertical = 5.dp),
                        )
                        Spacer(Modifier.height(footerLayout.edge.coerceAtLeast(0).dp + 10.dp))
                    } else Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

/** The excerpt's pages for the text area inside [padding]; the previous pages stay until the new ones are ready. */
@Composable
private fun previewPages(chapter: Chapter, source: PreviewSource, settings: ReaderSettings, palette: ReaderPalette,
                         padding: PaddingValues, maxWidth: Dp, maxHeight: Dp): Pair<List<Page>, Ink> {
    val context = LocalContext.current
    val typefaces = remember(context) { ResourceTypefaceProvider(context) }
    val density = LocalDensity.current
    val ink = remember { Ink() }
    SideEffect {
        ink.text = palette.text.toArgb()
        ink.secondary = palette.secondary.toArgb()
        ink.link = palette.link.toArgb()
    }
    val width = with(density) { (maxWidth - padding.calculateStartPadding(LayoutDirection.Ltr) - padding.calculateEndPadding(LayoutDirection.Ltr)).roundToPx() }.coerceAtLeast(1)
    val height = with(density) { (maxHeight - padding.calculateTopPadding() - padding.calculateBottomPadding()).roundToPx() }.coerceAtLeast(1)
    val spec = LayoutSpec(settings.style, width, height, density.density, density.fontScale, source.language)
    val pages by produceState(emptyList<Page>(), chapter, spec) {
        value = withContext(Dispatchers.Default) {
            Paginator(spec, typefaces, ImageSizer { null }, ink).paginate(0, chapter).pages
        }
    }
    return pages to ink
}

/** With «Прокрутка» all of the excerpt, scrolling under the lines. */
@Composable
private fun ScrollText(pages: List<Page>, ink: Ink, source: PreviewSource, settings: ReaderSettings,
                       palette: ReaderPalette, padding: PaddingValues) {
    val density = LocalDensity.current
    val footer = settings.shownFooter
    val top = settings.scrollHeaderBoundary.takeUnless { settings.header == PageHeader.None } ?: FooterStyle.None
    val bottom = settings.scrollFooterBoundary.takeUnless { footer.isEmpty } ?: FooterStyle.None
    Column(Modifier.fillMaxSize()
        .scrollEdges(top, padding.calculateTopPadding(), bottom, padding.calculateBottomPadding())
        .verticalScroll(rememberScrollState())
        .padding(padding)) {
        pages.forEach { page ->
            PageView(page, ink, palette, null, source.imageFile,
                Modifier.fillMaxWidth().height(with(density) { (page.lead + page.contentHeight()).toDp() }).padding(top = with(density) { page.lead.toDp() }))
        }
    }
}
