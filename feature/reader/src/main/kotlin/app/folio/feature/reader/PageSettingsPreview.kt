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

/** What the preview shows: a bounded excerpt, the titles and the page numbers of its lines. */
internal class PreviewSource(
    val blocks: List<Block>,
    val title: String,
    val chapterTitle: String?,
    val page: Int?,
    val total: Int?,
    val progress: Float,
    val pagesLeft: Int?,
    val language: String?,
    val imageFile: (String) -> File,
)

/** The book's own lines over the sample text: the text stays the same in every book. */
@Composable
internal fun bookPreviewSource(vm: ReaderViewModel): PreviewSource {
    val blocks = previewSampleBlocks()
    val state = vm.state
    // Each settings change re-paginates and briefly clears the page numbers; keep the last known ones
    // so the footer doesn't flash the percent fallback.
    val numbers = remember { mutableStateOf<Triple<Int?, Int?, Int?>>(Triple(null, null, null)) }
    if (state.globalPage != null && state.totalPages != null) numbers.value = Triple(state.globalPage, state.totalPages, state.pagesLeftInChapter)
    val (page, total, left) = numbers.value
    return PreviewSource(blocks, state.title, state.chapterTitle, page, total, state.progress,
        left, null, vm::imageFile)
}

/** A neutral sample for the shared settings, edited outside any book. */
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
 * and shows «Под строкой при прокрутке».
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
    val description = stringResource(R.string.settings_page_preview)
    val margin = settings.style.margin.dp
    val scroll = settings.pageTurn == PageTurn.Scroll
    val scrollInsets = textInsets(settings, lineLabelHeight(headerLook), lineLabelHeight(footerLook))
    var headerHeight by remember { mutableStateOf(0.dp) }
    var footerHeight by remember { mutableStateOf(0.dp) }
    Box(modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(16.dp))
        .background(palette.background).border(0.5.dp, Folio.colors.separator, RoundedCornerShape(16.dp))
        .semantics { contentDescription = description }) {
        if (scroll) {
            PreviewText(chapter, source, settings, palette, true,
                PaddingValues(start = margin, end = margin, top = scrollInsets.first, bottom = scrollInsets.second),
                Modifier.fillMaxSize())
            if (header != null) ReaderLineBlock(true, headerLook, margin,
                Modifier.align(Alignment.TopCenter)) {
                BlockTitle(header, headerLook, headerLook.titleMargin(24.dp, margin))
            }
            if (!footer.isEmpty) ReaderLineBlock(false, footerLook, margin,
                Modifier.align(Alignment.BottomCenter)) {
                PageFooterBar(
                    center = footerLook.text(footerCenter(footer, source.page, source.total, source.progress, source.pagesLeft, words,
                        readerChromeSeparator(settings.chromeStyles.footer))),
                    clock = clock, battery = battery, palette = palette, textStyle = footerLook.style,
                    spread = footerLook.layout.align == LineAlign.Spread,
                    modifier = Modifier.padding(horizontal = margin),
                )
            }
            return@Box
        }
        PreviewText(chapter, source, settings, palette, scroll, PaddingValues(start = margin, end = margin, top = headerHeight, bottom = footerHeight),
            Modifier.fillMaxSize())
        // «Отступ сверху/снизу» move a line from the edge, «Отступ до текста» from the text; negatives shrink the default gap.
        val headerLayout = settings.shownHeaderLayout
        val footerLayout = settings.shownFooterLayout
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
                    center = footerLook.text(footerCenter(footer, source.page, source.total, source.progress, source.pagesLeft, words,
                        readerChromeSeparator(settings.chromeStyles.footer))),
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

/** One page of the excerpt between the lines, or with «Прокрутка» all of it, scrolling under them. */
@Composable
private fun PreviewText(chapter: Chapter, source: PreviewSource, settings: ReaderSettings, palette: ReaderPalette,
                        scroll: Boolean, padding: PaddingValues, modifier: Modifier) {
    val context = LocalContext.current
    val typefaces = remember(context) { ResourceTypefaceProvider(context) }
    val density = LocalDensity.current
    val ink = remember { Ink() }
    SideEffect {
        ink.text = palette.text.toArgb()
        ink.secondary = palette.secondary.toArgb()
        ink.link = palette.link.toArgb()
    }
    BoxWithConstraints(modifier) {
        val width = with(density) { (maxWidth - padding.calculateStartPadding(LayoutDirection.Ltr) - padding.calculateEndPadding(LayoutDirection.Ltr)).roundToPx() }.coerceAtLeast(1)
        val height = with(density) { (maxHeight - padding.calculateTopPadding() - padding.calculateBottomPadding()).roundToPx() }.coerceAtLeast(1)
        val spec = LayoutSpec(settings.style, width, height, density.density, density.fontScale, source.language)
        val pages by produceState(emptyList<Page>(), chapter, spec) {
            value = withContext(Dispatchers.Default) {
                Paginator(spec, typefaces, ImageSizer { null }, ink).paginate(0, chapter).pages
            }
        }
        if (!scroll) {
            PageView(pages.firstOrNull(), ink, palette, null, source.imageFile, Modifier.fillMaxSize().padding(padding).clipToBounds())
            return@BoxWithConstraints
        }
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
}
