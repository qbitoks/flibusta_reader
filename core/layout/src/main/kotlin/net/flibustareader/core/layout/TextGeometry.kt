package net.flibustareader.core.layout

/**
 * Character x positions that match what is drawn. On the test phone (Android 16)
 * getPrimaryHorizontal, getOffsetForHorizontal and getSelectionPath ignore inter-word
 * justification while draw() stretches the line (and getLineRight reports the stretched width),
 * so selections and highlights drifted left along justified lines — by ≈26 px per space on a
 * 1040 px line. Here a stretched line's extra width is spread over its spaces the way the
 * framework draws it (only U+0020 stretches). If a platform measures the stretch itself,
 * there is no extra width and nothing changes.
 */
internal object TextGeometry {

    /** Extra width each space of [line] gets when drawn, 0 if the line is not stretched. */
    private fun extraPerSpace(item: PageItem.Text, line: Int): Float {
        if (!item.justified) return 0f
        val layout = item.layout
        if (line >= layout.lineCount - 1) return 0f
        val text = layout.text
        val start = layout.getLineStart(line)
        val end = layout.getLineEnd(line)
        if (end > 0 && text[end - 1] == '\n') return 0f
        val visibleEnd = layout.getLineVisibleEnd(line)
        var spaces = 0
        for (i in start until visibleEnd) if (text[i] == ' ') spaces++
        if (spaces == 0) return 0f
        val extra = layout.width - naturalRight(item, line)
        return if (extra > 0.5f) extra / spaces else 0f
    }

    /**
     * Right edge of [line]'s text before stretching. getLineRight/getLineMax already report the
     * stretched width, so it is measured from the characters (plus the hyphen of a broken word).
     */
    private fun naturalRight(item: PageItem.Text, line: Int): Float {
        val layout = item.layout
        val text = layout.text
        val end = layout.getLineEnd(line)
        val visibleEnd = layout.getLineVisibleEnd(line)
        if (visibleEnd < end) return layout.getPrimaryHorizontal(visibleEnd)
        if (visibleEnd <= layout.getLineStart(line)) return layout.getLineLeft(line)
        val last = visibleEnd - 1
        var right = layout.getPrimaryHorizontal(last) + layout.paint.measureText(text, last, visibleEnd)
        if (end < text.length && text[last].isLetter() && text[end].isLetter()) right += layout.paint.measureText("-")
        return right
    }

    /** x of the boundary before [offset] on [line], in the layout's coordinates. */
    fun x(item: PageItem.Text, line: Int, offset: Int): Float {
        val layout = item.layout
        val start = layout.getLineStart(line)
        val visibleEnd = layout.getLineVisibleEnd(line)
        if (offset >= visibleEnd) return right(item, line)
        val per = extraPerSpace(item, line)
        val natural = layout.getPrimaryHorizontal(offset.coerceAtLeast(start))
        if (per == 0f) return natural
        var spaces = 0
        val text = layout.text
        for (i in start until offset) if (text[i] == ' ') spaces++
        return natural + per * spaces
    }

    /** Right edge of [line]'s visible text as drawn. */
    fun right(item: PageItem.Text, line: Int): Float {
        val layout = item.layout
        return if (extraPerSpace(item, line) > 0f) layout.width.toFloat() else naturalRight(item, line)
    }

    /** Character boundary on [line] nearest to [x]. */
    fun offsetAt(item: PageItem.Text, line: Int, x: Float): Int {
        val layout = item.layout
        if (extraPerSpace(item, line) == 0f) return layout.getOffsetForHorizontal(line, x)
        val start = layout.getLineStart(line)
        val end = layout.getLineVisibleEnd(line)
        var best = start
        var bestDistance = Float.MAX_VALUE
        for (o in start..end) {
            val d = kotlin.math.abs(x(item, line, o) - x)
            if (d < bestDistance) {
                bestDistance = d
                best = o
            }
        }
        return best
    }
}
