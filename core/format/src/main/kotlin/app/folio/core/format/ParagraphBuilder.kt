package app.folio.core.format

import app.folio.core.model.Align
import app.folio.core.model.Block
import app.folio.core.model.Span
import app.folio.core.model.TextKind

/**
 * Accumulates inline text into a [Block.Text], collapsing HTML/XML whitespace and
 * tracking nested inline styles. Styles still open when a paragraph is flushed carry
 * over into the next paragraph (e.g. a bold `<div>` wrapping several `<p>`).
 */
internal class ParagraphBuilder {
    private val text = StringBuilder()
    private val spans = ArrayList<Span>()
    private val open = ArrayList<Open>()

    private class Open(var start: Int, val flags: Int, val target: String?)

    val isBlank: Boolean get() = text.isBlank()

    fun append(value: CharSequence, preserveWhitespace: Boolean = false) {
        if (preserveWhitespace) {
            text.append(value)
            return
        }
        for (c in value) {
            if (c.isWhitespace() && c != ' ') {
                if (text.isNotEmpty() && text.last() != ' ' && text.last() != '\n') text.append(' ')
            } else {
                text.append(c)
            }
        }
    }

    fun lineBreak() {
        if (text.isNotEmpty() && text.last() == ' ') text.setLength(text.length - 1)
        if (text.isNotEmpty()) text.append('\n')
    }

    /** Opens an inline style. Must be balanced with [pop]; returns false if nothing was pushed. */
    fun push(flags: Int, target: String? = null): Boolean {
        if (flags == 0) return false
        open += Open(text.length, flags, target)
        return true
    }

    fun pop() {
        val o = open.removeAt(open.lastIndex)
        if (text.length > o.start) spans += Span(o.start, text.length, o.flags, o.target)
    }

    /** Returns the finished paragraph, or null if it had no visible text. Resets the builder. */
    fun flush(kind: TextKind = TextKind.Body, align: Align = Align.Default): Block.Text? {
        while (text.isNotEmpty() && (text.last() == ' ' || text.last() == '\n')) text.setLength(text.length - 1)
        val len = text.length
        val result = if (text.isBlank()) {
            null
        } else {
            open.forEach { if (len > it.start) spans += Span(it.start, len, it.flags, it.target) }
            val clipped = spans.mapNotNull { s ->
                val end = minOf(s.end, len)
                if (end > s.start) s.copy(end = end) else null
            }
            Block.Text(text.toString(), clipped, kind, align)
        }
        text.setLength(0)
        spans.clear()
        open.forEach { it.start = 0 }
        return result
    }
}
