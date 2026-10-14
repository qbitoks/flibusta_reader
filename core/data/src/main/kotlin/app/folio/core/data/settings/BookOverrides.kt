package app.folio.core.data.settings

/**
 * A book's own reader settings over the shared ones. Only fields that differ are kept, so a later
 * change of a shared setting reaches every book that never changed that field.
 */
data class BookOverrides(val values: Map<String, String> = emptyMap()) {
    val isEmpty: Boolean get() = values.isEmpty()

    fun has(keys: Collection<String>): Boolean = keys.any { it in values }

    fun applyTo(shared: ReaderSettings): ReaderSettings =
        FIELDS.fold(shared) { s, field -> values[field.key]?.let { field.write(s, it) } ?: s }

    fun encode(): String = values.entries.joinToString("\n") { "${it.key}=${it.value}" }

    /** Fields apply in order: the text card before the fields that edit «Мой». */
    private class Field(val key: String, val read: (ReaderSettings) -> String, val write: (ReaderSettings, String) -> ReaderSettings?)

    companion object {
        const val THEME = "theme"
        const val APPEARANCE = "appearance"
        const val FONT_SIZE = "font_size"
        const val TEXT_PRESET = "text_preset"
        const val CUSTOM_COLORS = "custom_colors"
        const val FONT = "font"
        const val BOLD = "bold"
        const val CUSTOM_SPACING = "custom_spacing"
        const val LINE_SPACING = "line_spacing"
        const val LETTER_SPACING = "letter_spacing"
        const val WORD_SPACING = "word_spacing"
        const val MARGINS = "margins"
        const val JUSTIFY = "justify"
        const val PAGE_TURN = "page_turn"
        const val HEADER = "header"
        const val HEADER_STYLE = "header_style"
        const val HEADER_LAYOUT = "header_layout"
        const val FOOTER_PAGE = "footer_page"
        const val FOOTER_LEFT = "footer_left"
        const val FOOTER_CLOCK = "footer_clock"
        const val FOOTER_BATTERY = "footer_battery"
        const val FOOTER_UNDER = "footer_under"
        const val HEADER_UNDER = "header_under"
        const val FOOTER_STYLE = "footer_style"
        const val FOOTER_LAYOUT = "footer_layout"

        /** «Свои параметры» fields; the switch goes first, their values after it. */
        val TEXT = listOf(TEXT_PRESET, CUSTOM_COLORS, FONT, BOLD, CUSTOM_SPACING, LINE_SPACING, LETTER_SPACING, WORD_SPACING, MARGINS, JUSTIFY)
        val HEADER_KEYS = listOf(HEADER, HEADER_STYLE, HEADER_LAYOUT, HEADER_UNDER)
        val FOOTER_KEYS = listOf(FOOTER_PAGE, FOOTER_LEFT, FOOTER_CLOCK, FOOTER_BATTERY, FOOTER_UNDER, FOOTER_STYLE, FOOTER_LAYOUT)

        private inline fun <reified E : Enum<E>> enumField(key: String, crossinline get: (ReaderSettings) -> E, crossinline set: (ReaderSettings, E) -> ReaderSettings) =
            Field(key, { get(it).name }, { s, v -> runCatching { enumValueOf<E>(v) }.getOrNull()?.let { set(s, it) } })

        private fun boolField(key: String, get: (ReaderSettings) -> Boolean, set: (ReaderSettings, Boolean) -> ReaderSettings) =
            Field(key, { get(it).toString() }, { s, v -> v.toBooleanStrictOrNull()?.let { set(s, it) } })

        private fun intField(key: String, get: (ReaderSettings) -> Int, set: (ReaderSettings, Int) -> ReaderSettings) =
            Field(key, { get(it).toString() }, { s, v -> v.toIntOrNull()?.let { set(s, it) } })

        private fun floatField(key: String, get: (ReaderSettings) -> Float, set: (ReaderSettings, Float) -> ReaderSettings) =
            Field(key, { get(it).toString() }, { s, v -> v.toFloatOrNull()?.let { set(s, it) } })

        private fun ReaderSettings.text(change: (TextSet) -> TextSet) = withText(change(textSet))

        private val FIELDS = listOf(
            enumField(THEME, { it.theme }) { s, v -> s.copy(theme = v) },
            enumField(APPEARANCE, { it.appearance }) { s, v -> s.copy(appearance = v) },
            floatField(FONT_SIZE, { it.fontSize }) { s, v -> s.copy(fontSize = v) },
            enumField(TEXT_PRESET, { it.textPreset }) { s, v -> s.copy(textPreset = v) },
            Field(CUSTOM_COLORS, { it.customColors?.encode().orEmpty() }) { s, v -> s.copy(customColors = CustomColors.decode(v)) },
            Field(FONT, { it.textSet.font }) { s, v -> s.text { it.copy(font = v) } },
            boolField(BOLD, { it.textSet.boldText }) { s, v -> s.text { it.copy(boldText = v) } },
            boolField(CUSTOM_SPACING, { it.textSet.customize }) { s, v -> s.text { it.copy(customize = v) } },
            floatField(LINE_SPACING, { it.textSet.lineSpacing }) { s, v -> s.text { it.copy(lineSpacing = v) } },
            intField(LETTER_SPACING, { it.textSet.letterSpacingPct }) { s, v -> s.text { it.copy(letterSpacingPct = v) } },
            intField(WORD_SPACING, { it.textSet.wordSpacingPct }) { s, v -> s.text { it.copy(wordSpacingPct = v) } },
            intField(MARGINS, { it.textSet.marginsPct }) { s, v -> s.text { it.copy(marginsPct = v) } },
            boolField(JUSTIFY, { it.textSet.justify }) { s, v -> s.text { it.copy(justify = v) } },
            enumField(PAGE_TURN, { it.pageTurn }) { s, v -> s.copy(pageTurn = v) },
            enumField(HEADER, { it.header }) { s, v -> s.copy(header = v) },
            enumField(HEADER_STYLE, { it.chromeStyles.header }) { s, v -> s.copy(chromeStyles = s.chromeStyles.copy(header = v)) },
            Field(HEADER_LAYOUT, { it.headerLayout.encode() }) { s, v -> s.copy(headerLayout = LineLayout.decode(v, s.headerLayout)) },
            enumField(FOOTER_PAGE, { it.footer.page }) { s, v -> s.copy(footer = s.footer.copy(page = v)) },
            boolField(FOOTER_LEFT, { it.footer.pagesLeft }) { s, v -> s.copy(footer = s.footer.copy(pagesLeft = v)) },
            boolField(FOOTER_CLOCK, { it.footer.clock }) { s, v -> s.copy(footer = s.footer.copy(clock = v)) },
            boolField(FOOTER_BATTERY, { it.footer.battery }) { s, v -> s.copy(footer = s.footer.copy(battery = v)) },
            enumField(FOOTER_UNDER, { it.footer.style }) { s, v -> s.copy(footer = s.footer.copy(style = v)) },
            enumField(FOOTER_STYLE, { it.chromeStyles.footer }) { s, v -> s.copy(chromeStyles = s.chromeStyles.copy(footer = v)) },
            Field(FOOTER_LAYOUT, { it.footerLayout.encode() }) { s, v -> s.copy(footerLayout = LineLayout.decode(v, s.footerLayout)) },
            enumField(HEADER_UNDER, { it.headerUnder }) { s, v -> s.copy(headerUnder = v) },
        )

        /** What [edited] changes against [shared]; a field equal to the shared value is not kept. */
        fun between(shared: ReaderSettings, edited: ReaderSettings): BookOverrides {
            var base = shared
            val values = linkedMapOf<String, String>()
            for (field in FIELDS) {
                val value = field.read(edited)
                if (value != field.read(base)) {
                    values[field.key] = value
                    base = field.write(base, value) ?: base
                }
            }
            return BookOverrides(values)
        }

        fun decode(value: String?): BookOverrides = BookOverrides(
            value.orEmpty().lineSequence().mapNotNull { line ->
                val i = line.indexOf('=')
                if (i <= 0) null else line.substring(0, i) to line.substring(i + 1)
            }.toMap(),
        )
    }
}
