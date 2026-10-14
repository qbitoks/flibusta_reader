package app.folio.core.data.settings

import app.folio.core.layout.ReaderStyle

/**
 * A text style card: font, weight, spacing and margins. The size is shared by all styles.
 * Spacing overrides take effect while [customize] is on.
 *
 * Percent ranges follow Apple Books: letter spacing −10…+10 %, word spacing −20…+20 %,
 * margins −10…+10 %; line spacing is a multiplier 0.80…2.50.
 */
data class TextSet(
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

    /** Typography the paginator uses for this style at the global [fontSize]. */
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

        fun decode(value: String?, d: TextSet): TextSet {
            val p = value?.split('|') ?: return d
            if (p.size != 8) return d
            return runCatching {
                TextSet(
                    font = p[0], boldText = p[1].toBoolean(), customize = p[2].toBoolean(),
                    lineSpacing = p[3].toFloat(), letterSpacingPct = p[4].toInt(), wordSpacingPct = p[5].toInt(),
                    marginsPct = p[6].toInt(), justify = p[7].toBoolean(),
                )
            }.getOrDefault(d)
        }
    }
}

/**
 * «Стиль текста» cards; [Custom] («Мой») is the user's own set. Choosing a theme selects its
 * [ReaderTheme.textPreset] unless «Мой» is chosen.
 */
enum class TextPreset(val text: TextSet?) {
    Classic(TextSet(font = "literata")),
    Book(TextSet(font = "source_serif", customize = true, lineSpacing = 1.3f, marginsPct = 4)),
    Quiet(TextSet(font = "pt_serif", customize = true, lineSpacing = 1.4f, letterSpacingPct = 2, marginsPct = 6)),
    Soft(TextSet(font = "lora", customize = true, lineSpacing = 1.35f)),
    Clear(TextSet(font = "inter", justify = false)),
    Bold(TextSet(font = "inter", boldText = true, justify = false)),
    Compact(TextSet(font = "pt_serif", customize = true, lineSpacing = 1.1f, marginsPct = -6)),
    Custom(null),
}

/** Each Apple Books theme had its own typeface; its card keeps that pairing. */
val ReaderTheme.textPreset: TextPreset get() = when (this) {
    ReaderTheme.Original -> TextPreset.Classic
    ReaderTheme.Quiet -> TextPreset.Quiet
    ReaderTheme.Paper -> TextPreset.Book
    ReaderTheme.Bold -> TextPreset.Bold
    ReaderTheme.Calm -> TextPreset.Soft
    ReaderTheme.Focus -> TextPreset.Clear
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

        /** Bold shares Original's colours, so «Фон» offers only Original. */
        val BASES: List<ReaderTheme> = ReaderTheme.entries - ReaderTheme.Bold

        /** Starts from [theme]'s colours, so turning «Свои параметры» on does not change the page. */
        fun of(theme: ReaderTheme): CustomColors = CustomColors(if (theme == ReaderTheme.Bold) ReaderTheme.Original else theme)
    }
}
