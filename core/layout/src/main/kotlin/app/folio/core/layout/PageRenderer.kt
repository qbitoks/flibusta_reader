package app.folio.core.layout

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.icu.text.BreakIterator
import app.folio.core.model.Position

/** A character range inside one block, e.g. a search hit to highlight. */
data class TextRange(val chapter: Int, val block: Int, val start: Int, val end: Int)

/** Text between two positions of one chapter, end exclusive: a selection or a saved highlight. */
data class TextSpan(val start: Position, val end: Position) {
    val isEmpty: Boolean get() = end <= start
    operator fun contains(p: Position): Boolean = p >= start && p < end
}

/** How a [Mark] is drawn. */
enum class MarkStyle {
    /** Square-cornered fill following the text, as a live selection. */
    Selection,

    /** Rounded rectangle per line behind the text; [Mark.size] is the corner radius. */
    Highlight,

    /** Rounded line under each line's descenders; [Mark.size] is the thickness. */
    Underline,
}

/** A coloured mark on [span]: selection, highlight or underline. */
data class Mark(val span: TextSpan, val color: Int, val style: MarkStyle = MarkStyle.Selection, val size: Float = 0f)

object PageRenderer {
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /**
     * Draws [page]'s text at the canvas origin (the caller translates to the content box).
     * Images are delegated to [drawImage] since bitmap loading lives in the UI layer.
     */
    fun draw(
        canvas: Canvas,
        page: Page,
        ink: Ink,
        highlight: TextRange? = null,
        marks: List<Mark> = emptyList(),
        drawImage: (PageItem.Image) -> Unit = {},
    ) {
        for (item in page.items) when (item) {
            is PageItem.Text -> {
                val layout = item.layout
                val base = layout.getLineTop(item.firstLine).toFloat()
                val bottom = layout.getLineBottom(item.endLine - 1).toFloat()
                layout.paint.color = ink.text
                val save = canvas.save()
                canvas.translate(item.left, item.top - base)
                canvas.clipRect(0f, base, layout.width.toFloat(), bottom)
                for (mark in marks) {
                    val range = rangeIn(page.chapter, item, mark.span) ?: continue
                    highlightPaint.color = mark.color
                    when (mark.style) {
                        MarkStyle.Selection -> for (r in lineRects(item, range, fullHeight = true)) canvas.drawRect(r, highlightPaint)
                        MarkStyle.Highlight -> for (r in lineRects(item, range)) {
                            canvas.drawRoundRect(r, mark.size, mark.size, highlightPaint)
                        }
                        MarkStyle.Underline -> for (r in lineRects(item, range)) {
                            val line = layout.getLineForVertical(r.centerY().toInt())
                            val y = layout.getLineBaseline(line) + layout.paint.descent() + mark.size
                            canvas.drawRoundRect(r.left, y - mark.size / 2, r.right, y + mark.size / 2, mark.size / 2, mark.size / 2, highlightPaint)
                        }
                    }
                }
                if (highlight != null && highlight.chapter == page.chapter && highlight.block == item.block && highlight.end > highlight.start) {
                    highlightPaint.color = ink.highlight
                    for (r in lineRects(item, highlight.start..highlight.end, fullHeight = true)) canvas.drawRect(r, highlightPaint)
                }
                layout.draw(canvas)
                canvas.restoreToCount(save)
            }
            is PageItem.Image -> drawImage(item)
        }
    }

    /** Part of [span] inside [item] as block offsets (first until last), or null if none. */
    fun rangeIn(chapter: Int, item: PageItem.Text, span: TextSpan): IntRange? {
        if (span.start.chapter > chapter || span.end.chapter < chapter) return null
        val s = when {
            span.start.chapter < chapter || span.start.block < item.block -> item.startOffset
            span.start.block > item.block -> return null
            else -> maxOf(span.start.offset, item.startOffset)
        }
        val e = when {
            span.end.chapter > chapter || span.end.block > item.block -> item.endOffset
            span.end.block < item.block -> return null
            else -> minOf(span.end.offset, item.endOffset)
        }
        return if (e > s) s..e else null
    }

    /**
     * One rectangle per line of [range] (block offsets from [rangeIn]) in the layout's own
     * coordinates, from the first character to the last visible one. Highlights stop just under
     * the descenders; [fullHeight] (selection) takes the whole line box.
     */
    fun lineRects(item: PageItem.Text, range: IntRange, fullHeight: Boolean = false): List<RectF> {
        val layout = item.layout
        val out = ArrayList<RectF>()
        val first = layout.getLineForOffset(range.first).coerceAtLeast(item.firstLine)
        val last = layout.getLineForOffset((range.last - 1).coerceAtLeast(range.first)).coerceAtMost(item.endLine - 1)
        for (line in first..last) {
            val s = maxOf(range.first, layout.getLineStart(line))
            val e = minOf(range.last, layout.getLineVisibleEnd(line))
            if (e <= s) continue
            val left = TextGeometry.x(item, line, s)
            val right = TextGeometry.x(item, line, e)
            // Without the extra line spacing, so neighbouring lines' highlights never touch.
            val top = layout.getLineTop(line).toFloat()
            val lineBottom = layout.getLineBottom(line).toFloat()
            val bottom = if (fullHeight) lineBottom else minOf(lineBottom, layout.getLineBaseline(line) + layout.paint.descent() * 1.6f)
            out += RectF(minOf(left, right), top, maxOf(left, right), bottom)
        }
        return out
    }

    /** First and last line rectangles of [span] on [page] in content-box coordinates, or null if it is not there. */
    fun bounds(page: Page, span: TextSpan): Pair<RectF, RectF>? {
        var first: RectF? = null
        var last: RectF? = null
        for (item in page.items) {
            if (item !is PageItem.Text) continue
            val range = rangeIn(page.chapter, item, span) ?: continue
            val base = item.layout.getLineTop(item.firstLine)
            for (r in lineRects(item, range)) {
                val moved = RectF(r.left + item.left, r.top + item.top - base, r.right + item.left, r.bottom + item.top - base)
                if (first == null) first = moved
                last = moved
            }
        }
        return if (first != null && last != null) first to last else null
    }

    /** The word under a long press, or null over blank space and punctuation-only runs. */
    fun wordAt(page: Page, x: Float, y: Float): TextSpan? {
        val hit = hitTest(page, x, y) ?: return null
        val item = textItem(page, hit.block) ?: return null
        val text = item.layout.text
        if (text.isEmpty()) return null
        val at = hit.offset.coerceIn(0, text.length - 1)
        val words = BreakIterator.getWordInstance()
        words.setText(text.toString())
        var end = words.following(at)
        if (end == BreakIterator.DONE) end = text.length
        var start = words.previous()
        if (start == BreakIterator.DONE) start = 0
        if ((start until end).none { Character.isLetterOrDigit(text[it]) }) return null
        return TextSpan(Position(page.chapter, hit.block, start), Position(page.chapter, hit.block, end))
    }

    /** Nearest character position to a point anywhere on the page (dragging selection handles). */
    fun positionNear(page: Page, x: Float, y: Float): Position? {
        val items = page.items.filterIsInstance<PageItem.Text>()
        if (items.isEmpty()) return null
        val item = items.firstOrNull { y < it.top + it.height } ?: items.last()
        val layout = item.layout
        val localY = (y - item.top).coerceIn(0f, item.height - 1f) + layout.getLineTop(item.firstLine)
        val line = layout.getLineForVertical(localY.toInt()).coerceIn(item.firstLine, item.endLine - 1)
        val offset = TextGeometry.offsetAt(item, line, x - item.left)
        return Position(page.chapter, item.block, offset)
    }

    /**
     * Where a selection boundary sits: x and the line's top and bottom in content-box coordinates.
     * [atEnd] places an end boundary at the end of the previous line rather than the next line's start.
     */
    fun caret(page: Page, position: Position, atEnd: Boolean): RectF? {
        if (position.chapter != page.chapter) return null
        val item = textItem(page, position.block) ?: return null
        val layout = item.layout
        val offset = position.offset.coerceIn(item.startOffset, item.endOffset)
        var line = layout.getLineForOffset(offset).coerceIn(item.firstLine, item.endLine - 1)
        if (atEnd && offset > item.startOffset && layout.getLineStart(line) == offset) line = layout.getLineForOffset(offset - 1)
        val x = TextGeometry.x(item, line, offset)
        val base = layout.getLineTop(item.firstLine)
        val top = item.top + layout.getLineTop(line) - base
        val bottom = item.top + layout.getLineBottom(line) - base - (layout.getLineBottom(line) - layout.getLineBaseline(line)) / 2f
        return RectF(item.left + x, top, item.left + x, bottom)
    }

    private fun textItem(page: Page, block: Int): PageItem.Text? =
        page.items.firstOrNull { it is PageItem.Text && it.block == block } as PageItem.Text?

    /**
     * Text lines of [page] in content-box coordinates, top to bottom (the guide line steps
     * through them). Horizontally each spans its layout's full width.
     */
    fun lines(page: Page): List<RectF> {
        val out = ArrayList<RectF>()
        for (item in page.items) {
            if (item !is PageItem.Text) continue
            val layout = item.layout
            val base = layout.getLineTop(item.firstLine)
            for (line in item.firstLine until item.endLine) {
                if (layout.getLineStart(line) == layout.getLineEnd(line)) continue
                val top = item.top + layout.getLineTop(line) - base
                val bottom = item.top + layout.getLineBottom(line) - base
                out += RectF(item.left, top, item.left + layout.width, bottom)
            }
        }
        return out
    }

    /** Maps a tap (in content-box coordinates) to a character position, or null for blank space. */
    fun hitTest(page: Page, x: Float, y: Float): Position? {
        for (item in page.items) {
            if (item !is PageItem.Text) continue
            if (y < item.top || y > item.top + item.height) continue
            val layout = item.layout
            val localY = y - item.top + layout.getLineTop(item.firstLine)
            val line = layout.getLineForVertical(localY.toInt()).coerceIn(item.firstLine, item.endLine - 1)
            val localX = x - item.left
            if (localX < layout.getLineLeft(line) - SLOP || localX > TextGeometry.right(item, line) + SLOP) return null
            val offset = TextGeometry.offsetAt(item, line, localX)
            return Position(page.chapter, item.block, offset)
        }
        return null
    }

    private const val SLOP = 24f
}
