package net.flibustareader.feature.reader

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
import net.flibustareader.core.data.settings.*
import net.flibustareader.core.designsystem.*
import net.flibustareader.core.layout.*
import net.flibustareader.core.model.Block
import net.flibustareader.core.model.Chapter

/** Fixed, readable excerpt using the book renderer; pagination here never updates the reading viewport. */
@Composable
internal fun PageSettingsPreview(vm: ReaderViewModel, settings: ReaderSettings, palette: ReaderPalette, modifier: Modifier = Modifier) {
    val position = remember(vm.bookId) { vm.readingPosition }
    val sample = remember(vm.book, position) { settingsPreviewBlocks(vm.book, position) }
    val fallback = stringResource(R.string.themes_preview_text)
    val chapter = remember(sample, fallback) { Chapter(null, sample.ifEmpty { listOf(Block.Text(fallback)) }) }
    val state = vm.state
    val headerStyle = readerChromeTextStyle(settings.chromeStyles.header, palette, settings.style.font)
    val footerStyle = readerChromeTextStyle(settings.chromeStyles.footer, palette, settings.style.font)
    val header = headerText(settings.header, state.title, state.chapterTitle)
    val footer = settings.footer
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
        .background(palette.background).border(0.5.dp, Flibusta.colors.separator, RoundedCornerShape(16.dp))
        .semantics { contentDescription = description }.padding(vertical = 10.dp)) {
        if (header != null) Text(header, style = headerStyle, modifier = Modifier.fillMaxWidth().padding(horizontal = margin, vertical = 4.dp),
            textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
            PreviewText(chapter, vm, settings, palette, Modifier.fillMaxSize().padding(horizontal = margin))
            EdgeFade(palette, 12.dp, top = false, modifier = Modifier.align(Alignment.BottomCenter), fade = 12.dp)
        }
        if (!footer.isEmpty) {
            Box(Modifier.fillMaxWidth()) {
                if (settings.pageTurn == PageTurn.Scroll && footer.style == FooterStyle.Strip) {
                    FooterStrip(palette, with(LocalDensity.current) { footerStyle.lineHeight.toDp() } + 10.dp)
                }
                PageFooterBar(
                    center = footerCenter(footer, state.globalPage, state.totalPages, state.progress, state.pagesLeftInChapter, words,
                        readerChromeSeparator(settings.chromeStyles.footer)),
                    clock = clock, battery = battery, palette = palette, textStyle = footerStyle,
                    modifier = Modifier.padding(horizontal = margin, vertical = 5.dp),
                )
            }
        }
    }
}

@Composable
private fun PreviewText(chapter: Chapter, vm: ReaderViewModel, settings: ReaderSettings, palette: ReaderPalette, modifier: Modifier) {
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
        val spec = LayoutSpec(settings.style, width, height, density.density, density.fontScale, vm.book?.metadata?.language)
        val page by produceState<Page?>(null, chapter, spec) {
            value = withContext(Dispatchers.Default) {
                Paginator(spec, typefaces, ImageSizer { null }, ink).paginate(0, chapter).pages.firstOrNull()
            }
        }
        PageView(page, ink, palette, null, vm::imageFile, Modifier.fillMaxSize().clipToBounds())
    }
}
