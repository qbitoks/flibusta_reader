package app.folio.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import app.folio.core.data.settings.*
import app.folio.core.designsystem.*
import app.folio.core.layout.*
import app.folio.core.model.Block
import app.folio.core.model.Chapter
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

/** The book at the reading position, so settings are judged on its own text. */
@Composable
internal fun bookPreviewSource(vm: ReaderViewModel): PreviewSource {
    val position = remember(vm.bookId) { vm.readingPosition }
    val blocks = remember(vm.book, position) { settingsPreviewBlocks(vm.book, position) }
    val state = vm.state
    return PreviewSource(blocks, state.title, state.chapterTitle, state.globalPage, state.totalPages, state.progress,
        state.pagesLeftInChapter, vm.book?.metadata?.language, vm::imageFile)
}

/** A neutral sample for the shared settings, edited outside any book. */
@Composable
internal fun samplePreviewSource(): PreviewSource {
    val text = stringResource(R.string.themes_preview_text)
    val title = stringResource(R.string.settings_preview_book)
    val chapter = stringResource(R.string.settings_preview_chapter)
    return remember(text, title, chapter) {
        PreviewSource(List(3) { Block.Text(text) }, title, chapter, 42, 586, 0.07f, 5, null) { File(it) }
    }
}

/** Fixed, readable excerpt using the book renderer; pagination here never updates the reading viewport. */
@Composable
internal fun PageSettingsPreview(source: PreviewSource, settings: ReaderSettings, palette: ReaderPalette, modifier: Modifier = Modifier) {
    val fallback = stringResource(R.string.themes_preview_text)
    val chapter = remember(source.blocks, fallback) { Chapter(null, source.blocks.ifEmpty { listOf(Block.Text(fallback)) }) }
    val headerLook = headerLook(settings, palette)
    val footerLook = footerLook(settings, palette)
    val header = headerText(settings.header, source.title, source.chapterTitle)
    val footer = settings.shownFooter
    val clock by rememberClock(footer.clock)
    val battery by rememberBattery(footer.battery)
    val resources = LocalContext.current.resources
    val words = FooterWords(
        { n, total -> resources.getString(R.string.reader_page_of, n, total) },
        { resources.getString(R.string.reader_percent, it) },
        { resources.getQuantityString(R.plurals.footer_left_in_chapter, it, it) },
        stringResource(R.string.reader_chapter_end),
    )
    val description = stringResource(R.string.settings_page_preview)
    val margin = settings.style.margin.dp
    Column(modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(16.dp))
        .background(palette.background).border(0.5.dp, Folio.colors.separator, RoundedCornerShape(16.dp))
        .semantics { contentDescription = description }.padding(vertical = 10.dp)) {
        if (header != null) {
            Text(headerLook.text(header), style = headerLook.style, modifier = Modifier.fillMaxWidth().padding(horizontal = margin, vertical = 4.dp),
                textAlign = headerLook.textAlign, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (headerLook.layout.rule) LineRule(headerLook.ruleColor, Modifier.padding(horizontal = margin, vertical = 2.dp))
        }
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
            PreviewText(chapter, source, settings, palette, Modifier.fillMaxSize().padding(horizontal = margin))
            EdgeFade(palette, 12.dp, top = false, modifier = Modifier.align(Alignment.BottomCenter), fade = 12.dp)
        }
        if (!footer.isEmpty) {
            Box(Modifier.fillMaxWidth()) {
                val strip = settings.pageTurn == PageTurn.Scroll && footer.style == FooterStyle.Strip
                if (strip) FooterStrip(palette, with(LocalDensity.current) { footerLook.style.lineHeight.toDp() } + 10.dp)
                PageFooterBar(
                    center = footerLook.text(footerCenter(footer, source.page, source.total, source.progress, source.pagesLeft, words,
                        readerChromeSeparator(settings.chromeStyles.footer))),
                    clock = clock, battery = battery, palette = palette, textStyle = footerLook.style,
                    spread = footerLook.layout.align == LineAlign.Spread,
                    rule = footerLook.ruleColor.takeIf { footerLook.layout.rule && !strip },
                    modifier = Modifier.padding(horizontal = margin, vertical = 5.dp),
                )
            }
        }
    }
}

@Composable
private fun PreviewText(chapter: Chapter, source: PreviewSource, settings: ReaderSettings, palette: ReaderPalette, modifier: Modifier) {
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
        val width = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
        val height = with(density) { maxHeight.roundToPx() }.coerceAtLeast(1)
        val spec = LayoutSpec(settings.style, width, height, density.density, density.fontScale, source.language)
        val page by produceState<Page?>(null, chapter, spec) {
            value = withContext(Dispatchers.Default) {
                Paginator(spec, typefaces, ImageSizer { null }, ink).paginate(0, chapter).pages.firstOrNull()
            }
        }
        PageView(page, ink, palette, null, source.imageFile, Modifier.fillMaxSize().clipToBounds())
    }
}
