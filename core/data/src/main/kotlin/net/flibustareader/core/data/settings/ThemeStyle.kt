package net.flibustareader.core.data.settings

import net.flibustareader.core.layout.ReaderStyle

/**
 * Typography saved independently for each reading theme.
 * Edits apply immediately; spacing overrides take effect while [customize] is on.
 *
 * Percent ranges follow Apple Books: letter spacing −10…+10 %, word spacing −20…+20 %,
 * margins −10…+10 %; line spacing is a multiplier 0.80…2.50.
 */
data class ThemeStyle(
    val font: String,
    val boldText: Boolean = false,
    val customize: Boolean = false,
    val lineSpacing: Float = DEFAULT_LINE_SPACING,
    val letterSpacingPct: Int = 0,
    val wordSpacingPct: Int = 0,
    val marginsPct: Int = 0,
    val justify: Boolean = true,
) {
    fun encode(): String = listOf(font, boldText, customize, lineSpacing, letterSpacingPct, wordSpacingPct, marginsPct, justify).joinToString("|")

    /** Typography the paginator uses for this theme at the global [fontSize]. */
    fun toReaderStyle(fontSize: Float): ReaderStyle = ReaderStyle(
        font = font,
        fontSize = fontSize,
        lineSpacing = if (customize) lineSpacing else DEFAULT_LINE_SPACING,
        // 10 % of tracking ≈ 0.05 em; 20 % of word spacing ≈ a whole space (0.25 em).
        letterSpacing = if (customize) letterSpacingPct * 0.005f else 0f,
        wordSpacing = if (customize) wordSpacingPct * 0.0125f else 0f,
        margin = BASE_MARGIN * (1f + (if (customize) marginsPct else 0) * 0.07f),
        justify = justify,
        hyphenate = true,
        boldText = boldText,
    )

    companion object {
        const val DEFAULT_LINE_SPACING = 1.25f
        const val BASE_MARGIN = 30f

        /** Each Apple Books theme has its own typeface; these are our OFL stand-ins. */
        fun defaultFor(theme: ReaderTheme): ThemeStyle = when (theme) {
            ReaderTheme.Original -> ThemeStyle(font = "literata")
            ReaderTheme.Quiet -> ThemeStyle(font = "pt_serif")
            ReaderTheme.Paper -> ThemeStyle(font = "source_serif")
            ReaderTheme.Bold -> ThemeStyle(font = "inter", boldText = true)
            ReaderTheme.Calm -> ThemeStyle(font = "lora")
            ReaderTheme.Focus -> ThemeStyle(font = "inter")
        }

        fun decode(value: String?, theme: ReaderTheme): ThemeStyle {
            val d = defaultFor(theme)
            val p = value?.split('|') ?: return d
            if (p.size != 8) return d
            return runCatching {
                ThemeStyle(
                    font = p[0], boldText = p[1].toBoolean(), customize = p[2].toBoolean(),
                    lineSpacing = p[3].toFloat(), letterSpacingPct = p[4].toInt(), wordSpacingPct = p[5].toInt(),
                    marginsPct = p[6].toInt(), justify = p[7].toBoolean(),
                )
            }.getOrDefault(d)
        }
    }
}
