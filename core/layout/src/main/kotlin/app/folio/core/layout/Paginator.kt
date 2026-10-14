package app.folio.core.layout

import android.text.StaticLayout
import app.folio.core.model.Align
import app.folio.core.model.Block
import app.folio.core.model.Chapter
import app.folio.core.model.Position

/** Everything that determines where page breaks fall. Equal specs ⇒ identical pagination. */
data class LayoutSpec(
    val style: ReaderStyle,
    /** Content box (inside margins) in px. */
    val pageWidth: Int,
    val pageHeight: Int,
    val density: Float,
    val fontScale: Float,
    val language: String?,
)

sealed interface PageItem {
    val block: Int
    val top: Float

    class Text(
        override val block: Int,
        override val top: Float,
        val left: Float,
        val layout: StaticLayout,
        val firstLine: Int,
        /** Exclusive. */
        val endLine: Int,
        /** Inter-word justified: the layout stretches lines when drawing (see [TextGeometry]). */
        val justified: Boolean = false,
    ) : PageItem {
        val height: Float get() = (layout.getLineBottom(endLine - 1) - layout.getLineTop(firstLine)).toFloat()
        val startOffset: Int get() = layout.getLineStart(firstLine)
        val endOffset: Int get() = layout.getLineEnd(endLine - 1)
    }

    class Image(
        override val block: Int,
        override val top: Float,
        val left: Float,
        val width: Float,
        val height: Float,
        val resource: String,
    ) : PageItem
}

class Page(
    val chapter: Int,
    val index: Int,
    val start: Position,
    val items: List<PageItem>,
    /**
     * Space the page break swallowed above the first item (paragraph spacing, a separator line).
     * Pages draw from y = 0; the scroll mode puts this gap back so stacked pages read as one text.
     */
    val lead: Float = 0f,
)

class ChapterPages(val chapter: Int, val pages: List<Page>) {
    /** Index of the page containing [position] (the last page starting at or before it). */
    fun pageIndexOf(position: Position): Int {
        var lo = 0
        var hi = pages.lastIndex
        var found = 0
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (pages[mid].start <= position) {
                found = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return found
    }
}

/**
 * Splits a chapter into pages. Work is proportional to the chapter's text; callers run it
 * off the main thread and cache the result per [LayoutSpec].
 */
class Paginator(
    val spec: LayoutSpec,
    typefaces: TypefaceProvider,
    private val images: ImageSizer,
    val ink: Ink,
) {
    private val styler = TextStyler(spec, typefaces, ink)

    /** Called on the main thread after a theme change; layout is unaffected. */
    fun applyInk() {
        styler.allPaints.forEach { it.color = ink.text }
    }

    fun paginate(chapterIndex: Int, chapter: Chapter): ChapterPages {
        val builder = PageBuilder(chapterIndex)
        val width = spec.pageWidth
        val height = spec.pageHeight.toFloat()
        val em = styler.basePx
        val blocks = chapter.blocks

        for ((i, block) in blocks.withIndex()) {
            when (block) {
                Block.Separator -> {
                    if (builder.y > 0f) builder.y += styler.bodyLineHeight else if (builder.pages.isNotEmpty()) builder.lead += styler.bodyLineHeight
                    if (builder.y >= height) builder.breakPage()
                }

                is Block.Image -> {
                    val size = images.size(block.resource) ?: continue
                    var w = size.width.toFloat()
                    var h = size.height.toFloat()
                    // Small decorations keep a natural size; illustrations fill the width.
                    val natural = w * spec.density / 2f
                    val targetW = if (w >= width / 3f) width.toFloat() else natural.coerceAtMost(width.toFloat())
                    val scale = minOf(targetW / w, height / h)
                    w *= scale
                    h *= scale
                    if (builder.y > 0f && builder.y + h > height) builder.breakPage()
                    builder.markStart(Position(chapterIndex, i, 0))
                    builder.items += PageItem.Image(i, builder.y, (width - w) / 2f, w, h, block.resource)
                    builder.y += h
                    builder.bottom = builder.y
                    builder.y += em * 0.6f
                }

                is Block.Text -> {
                    val style = BlockStyle.of(block, spec.style.justify)
                    val left = style.insetStart * width
                    val layoutWidth = (width * (1f - style.insetStart - style.insetEnd)).toInt().coerceAtLeast(1)
                    val layout = styler.layout(block, style, layoutWidth)
                    val lineCount = layout.lineCount

                    if (builder.y > 0f) builder.y += style.spaceBefore * em
                    // Keep headings together with at least two lines of what follows.
                    if (style.keepWithNext && builder.y > 0f &&
                        builder.y + layout.height + styler.bodyLineHeight * 2 > height
                    ) {
                        builder.breakPage()
                    }

                    var line = 0
                    while (line < lineCount) {
                        val available = height - builder.y
                        val base = layout.getLineTop(line)
                        var end = line
                        while (end < lineCount && layout.getLineBottom(end) - base <= available) end++
                        val remaining = lineCount - line
                        var fit = end - line
                        if (fit < remaining) {
                            when {
                                // Orphan: a lone first line at the bottom of the page.
                                fit == 1 && remaining > 1 && builder.y > 0f -> fit = 0
                                // Widow: a lone last line at the top of the next page.
                                remaining - fit == 1 && fit >= 2 -> fit -= 1
                            }
                        }
                        if (fit == 0) {
                            if (builder.y == 0f) fit = 1 else {
                                builder.breakPage()
                                continue
                            }
                        }
                        builder.markStart(Position(chapterIndex, i, layout.getLineStart(line)))
                        builder.items += PageItem.Text(i, builder.y, left, layout, line, line + fit, justified = style.align == Align.Justify)
                        builder.y += (layout.getLineBottom(line + fit - 1) - base).toFloat()
                        builder.bottom = builder.y
                        line += fit
                        if (line < lineCount) builder.breakPage()
                    }
                    builder.y += style.spaceAfter * em
                }
            }
        }
        builder.breakPage()
        if (builder.pages.isEmpty()) {
            builder.pages += Page(chapterIndex, 0, Position(chapterIndex), emptyList())
        }
        return ChapterPages(chapterIndex, builder.pages)
    }

    private class PageBuilder(val chapter: Int) {
        val pages = ArrayList<Page>()
        var items = ArrayList<PageItem>()
        var y = 0f
        /** Bottom of the last placed item; [y] minus it is spacing still pending below. */
        var bottom = 0f
        /** Spacing carried over the page break to the next page ([Page.lead]). */
        var lead = 0f
        private var start: Position? = null

        fun markStart(position: Position) {
            if (start == null) start = if (pages.isEmpty()) Position(chapter) else position
        }

        fun breakPage() {
            if (items.isNotEmpty()) {
                pages += Page(chapter, pages.size, start ?: Position(chapter), items, lead)
                lead = (y - bottom).coerceAtLeast(0f)
            }
            items = ArrayList()
            y = 0f
            bottom = 0f
            start = null
        }
    }
}
