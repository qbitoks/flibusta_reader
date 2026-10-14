package app.folio.core.data.settings

/** Where a reader line puts its text; [Spread] is the bottom line's time · page · battery across the width. */
enum class LineAlign { Start, Center, End, Spread }

/** Position inside a line block, measured from the nearest screen edge towards the book text. */
enum class LineVertical { Edge, Center, Text }

/**
 * «Свои параметры» of a reader line. With [customize] off the line is drawn with [default] values,
 * while the stored ones wait for the switch, as «Свои интервалы и поля» does.
 */
data class LineLayout(
    val customize: Boolean = false,
    /** Percent of the visual preset's text size. */
    val sizePct: Int = 100,
    /** Percent of the preset's text opacity. */
    val opacityPct: Int = 100,
    val align: LineAlign = LineAlign.Center,
    /** Extra dp between the screen edge and the line. */
    val edge: Int = 0,
    /** dp added to (or, negative, taken from) the space between the line and the text. */
    val gap: Int = 0,
    val uppercase: Boolean = false,
    /** Hairline between the line and the text. */
    val rule: Boolean = false,
    /** Header only: no running title on the first page of a chapter. */
    val hideOnChapterStart: Boolean = false,
    /** Null retains the legacy edge offset when deriving the block height. */
    val blockHeight: Int? = null,
    val vertical: LineVertical = LineVertical.Center,
    /** Absolute distance to the book text; null derives it from the legacy gap. */
    val textGap: Int? = null,
) {
    fun encode(): String = listOf(customize, sizePct, opacityPct, align.name, edge, gap, uppercase, rule,
        hideOnChapterStart, blockHeight ?: "", vertical.name, textGap ?: "").joinToString("|")

    val requestedBlockHeight: Int get() = blockHeight ?: (36 + edge)
    val distanceToText: Int get() = textGap ?: (12 + gap).coerceAtLeast(0)

    companion object {
        val HEADER = LineLayout()
        val FOOTER = LineLayout(align = LineAlign.Spread)
        val SIZE = 80..140
        val OPACITY = 40..100
        val EDGE = 0..24
        val GAP = -12..24
        val BLOCK_HEIGHT = 32..112
        val TEXT_GAP = 0..48

        fun decode(value: String?, default: LineLayout): LineLayout {
            val p = value?.split('|') ?: return default
            if (p.size < 9) return default
            return runCatching {
                LineLayout(
                    customize = p[0].toBooleanStrict(), sizePct = p[1].toInt().coerceIn(SIZE), opacityPct = p[2].toInt().coerceIn(OPACITY),
                    align = LineAlign.valueOf(p[3]), edge = p[4].toInt().coerceIn(EDGE), gap = p[5].toInt().coerceIn(GAP),
                    uppercase = p[6].toBooleanStrict(), rule = p[7].toBooleanStrict(), hideOnChapterStart = p[8].toBooleanStrict(),
                    blockHeight = p.getOrNull(9)?.toIntOrNull()?.coerceIn(BLOCK_HEIGHT),
                    vertical = p.getOrNull(10)?.let { runCatching { LineVertical.valueOf(it) }.getOrNull() } ?: LineVertical.Center,
                    textGap = p.getOrNull(11)?.toIntOrNull()?.coerceIn(TEXT_GAP),
                )
            }.getOrDefault(default)
        }
    }
}
