package net.flibustareader.core.format.epub

import net.flibustareader.core.model.Align
import net.flibustareader.core.model.Span

/** The handful of CSS properties the reader honours; everything else is the reader's call. */
internal data class CssStyle(
    val flags: Int = 0,
    val align: Align? = null,
    val hidden: Boolean = false,
) {
    fun merge(other: CssStyle) = CssStyle(
        flags = flags or other.flags,
        align = other.align ?: align,
        hidden = hidden || other.hidden,
    )

    companion object {
        val Empty = CssStyle()
    }
}

/**
 * Deliberately tiny CSS model: only simple selectors (`tag`, `.class`, `tag.class`)
 * are indexed. Publisher typography (fonts, sizes, margins) is intentionally ignored so
 * the user's reading settings always win, as in Apple Books.
 */
internal class CssRules private constructor(private val rules: Map<String, CssStyle>) {

    fun styleOf(tag: String, classes: Collection<String>, inline: String?): CssStyle {
        if (rules.isEmpty() && inline == null) return CssStyle.Empty
        var style = rules[tag] ?: CssStyle.Empty
        for (cls in classes) {
            rules[".$cls"]?.let { style = style.merge(it) }
            rules["$tag.$cls"]?.let { style = style.merge(it) }
        }
        if (inline != null) style = style.merge(parseDeclarations(inline))
        return style
    }

    operator fun plus(other: CssRules): CssRules = when {
        other.rules.isEmpty() -> this
        rules.isEmpty() -> other
        else -> CssRules(
            HashMap(rules).apply { other.rules.forEach { (k, v) -> merge(k, v) { a, b -> a.merge(b) } } },
        )
    }

    companion object {
        val Empty = CssRules(emptyMap())

        private val comments = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL)
        private val simpleSelector = Regex("^[a-zA-Z0-9]*(\\.[a-zA-Z0-9_-]+)?$")

        fun parse(css: String): CssRules {
            val rules = HashMap<String, CssStyle>()
            val clean = css.replace(comments, "")
            var depth = 0
            var start = 0
            // Skip @media/@font-face blocks entirely; only top-level rules are considered.
            var i = 0
            while (i < clean.length) {
                when (clean[i]) {
                    '{' -> {
                        if (depth == 0) {
                            // substringAfterLast drops statements such as `@charset "utf-8";`.
                            val selectors = clean.substring(start, i).substringAfterLast(';').trim()
                            val end = clean.indexOf('}', i)
                            if (end < 0) break
                            if (selectors.startsWith("@")) {
                                depth = 1
                                i++
                                continue
                            }
                            val style = parseDeclarations(clean.substring(i + 1, end))
                            if (style != CssStyle.Empty) {
                                for (sel in selectors.split(',')) {
                                    val s = sel.trim().lowercase()
                                    if (simpleSelector.matches(s) && s.isNotEmpty()) {
                                        rules.merge(s, style) { a, b -> a.merge(b) }
                                    }
                                }
                            }
                            i = end
                            start = end + 1
                        } else {
                            depth++
                        }
                    }
                    '}' -> if (depth > 0) {
                        depth--
                        start = i + 1
                    }
                }
                i++
            }
            return CssRules(rules)
        }

        fun parseDeclarations(block: String): CssStyle {
            var flags = 0
            var align: Align? = null
            var hidden = false
            for (decl in block.split(';')) {
                val colon = decl.indexOf(':')
                if (colon < 0) continue
                val name = decl.substring(0, colon).trim().lowercase()
                val value = decl.substring(colon + 1).trim().lowercase().removeSuffix("!important").trim()
                when (name) {
                    "font-weight" -> if (value == "bold" || value == "bolder" || (value.toIntOrNull() ?: 0) >= 600) flags = flags or Span.BOLD
                    "font-style" -> if (value == "italic" || value == "oblique") flags = flags or Span.ITALIC
                    "text-align" -> align = when (value) {
                        "center" -> Align.Center
                        "right", "end" -> Align.End
                        "left", "start" -> Align.Start
                        "justify" -> Align.Justify
                        else -> null
                    }
                    "display" -> if (value == "none") hidden = true
                    "vertical-align" -> if (value == "super") flags = flags or Span.SUPERSCRIPT else if (value == "sub") flags = flags or Span.SUBSCRIPT
                    "font-variant" -> if (value == "small-caps") flags = flags or Span.SMALL
                }
            }
            return CssStyle(flags, align, hidden)
        }
    }
}
