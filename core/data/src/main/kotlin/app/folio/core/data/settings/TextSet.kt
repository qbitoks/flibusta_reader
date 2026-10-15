package app.folio.core.data.settings

import app.folio.core.layout.ReaderStyle

/** Typography independent of the reading palette. Margins are percent of the viewport on each side. */
data class TextSet(
    val font: String,
    val boldText: Boolean = false,
    val lineSpacing: Float = DEFAULT_LINE_SPACING,
    val letterSpacingPct: Int = 0,
    val wordSpacingPct: Int = 0,
    val marginsPct: Int = 5,
    val justify: Boolean = true,
    val paragraphSpacing: Float = 0f,
    val firstLineIndent: Float = 1.6f,
) {
    fun encode(): String = listOf(font, boldText, lineSpacing, letterSpacingPct, wordSpacingPct,
        marginsPct, justify, paragraphSpacing, firstLineIndent).joinToString("|")

    fun toReaderStyle(fontSize: Float): ReaderStyle = ReaderStyle(
        font = font, fontSize = fontSize, lineSpacing = lineSpacing,
        letterSpacing = letterSpacingPct * 0.005f, wordSpacing = wordSpacingPct * 0.0125f,
        marginPercent = marginsPct.toFloat(), justify = justify, hyphenate = true,
        boldText = boldText, paragraphSpacing = paragraphSpacing, firstLineIndent = firstLineIndent,
    )

    companion object {
        const val DEFAULT_LINE_SPACING = 1.2f
        val DEFAULT = ReaderStyle().let { TextSet(font = it.font, justify = true, firstLineIndent = it.firstLineIndent) }

        fun decode(value: String?, default: TextSet): TextSet {
            val p = value?.split('|')?.takeIf { it.size == 9 } ?: return default
            return runCatching {
                TextSet(font = p[0], boldText = p[1].toBooleanStrict(),
                    lineSpacing = p[2].toFloat().also { require(it.isFinite()) }.coerceIn(0.8f, 2.5f),
                    letterSpacingPct = p[3].toInt().coerceIn(-10, 10), wordSpacingPct = p[4].toInt().coerceIn(-20, 20),
                    marginsPct = p[5].toInt().coerceIn(0, 15), justify = p[6].toBooleanStrict(),
                    paragraphSpacing = p[7].toFloat().also { require(it.isFinite()) }.coerceIn(0f, 2f),
                    firstLineIndent = p[8].toFloat().also { require(it.isFinite()) }.coerceIn(0f, 3f))
            }.getOrDefault(default)
        }
    }
}

/**
 * Colours of «Свои параметры»: the page colours of a [base] theme, [warmth] −10 (cool) … +10 (warm) and
 * [contrast] −10 (soft) … +10 (crisp); 0 draws the base as it is.
 */
data class CustomColors(val base: ReaderTheme = ReaderTheme.Original, val warmth: Int = 0, val contrast: Int = 0) {
    fun encode(): String = "${base.name}|$warmth|$contrast"

    companion object {
        fun decode(value: String?): CustomColors? {
            val p = value?.split('|')?.takeIf { it.size == 3 } ?: return null
            return runCatching {
                CustomColors(of(enumValueOf<ReaderTheme>(p[0])).base, p[1].toInt().coerceIn(-10, 10), p[2].toInt().coerceIn(-10, 10))
            }.getOrNull()
        }

        val BASES: List<ReaderTheme> = listOf(ReaderTheme.Original, ReaderTheme.Focus, ReaderTheme.Calm, ReaderTheme.Paper, ReaderTheme.Quiet)

        /** Starts from [theme]'s colours, so turning «Свои параметры» on does not change the page. */
        fun of(theme: ReaderTheme): CustomColors = CustomColors(if (theme == ReaderTheme.Bold) ReaderTheme.Original else theme)
    }
}
