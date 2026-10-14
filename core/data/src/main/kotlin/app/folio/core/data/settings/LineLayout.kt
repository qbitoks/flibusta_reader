package app.folio.core.data.settings

/** Where a reader line puts its text; [Spread] is the bottom line's time · page · battery across the width. */
enum class LineAlign { Start, Center, End, Spread }

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
) {
    fun encode(): String = listOf(customize, sizePct, opacityPct, align.name, edge, gap, uppercase, rule, hideOnChapterStart).joinToString("|")

    companion object {
        val HEADER = LineLayout()
        val FOOTER = LineLayout(align = LineAlign.Spread)
        val SIZE = 80..140
        val OPACITY = 40..100
        val EDGE = 0..24
        val GAP = -12..24

        fun decode(value: String?, default: LineLayout): LineLayout {
            val p = value?.split('|') ?: return default
            if (p.size < 9) return default
            return runCatching {
                LineLayout(
                    customize = p[0].toBooleanStrict(), sizePct = p[1].toInt().coerceIn(SIZE), opacityPct = p[2].toInt().coerceIn(OPACITY),
                    align = LineAlign.valueOf(p[3]), edge = p[4].toInt().coerceIn(EDGE), gap = p[5].toInt().coerceIn(GAP),
                    uppercase = p[6].toBooleanStrict(), rule = p[7].toBooleanStrict(), hideOnChapterStart = p[8].toBooleanStrict(),
                )
            }.getOrDefault(default)
        }
    }
}
