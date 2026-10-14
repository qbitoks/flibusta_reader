package app.folio.core.format.epub

import app.folio.core.format.ParagraphBuilder
import app.folio.core.model.Align
import app.folio.core.model.Block
import app.folio.core.model.Span
import app.folio.core.model.TextKind
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

internal class ConvertedChapter(
    val blocks: List<Block>,
    val anchors: Map<String, Int>,
    val firstHeading: String?,
    val footnotes: Map<String, List<Block>>,
)

/**
 * Converts one XHTML spine document into flat [Block]s. Link targets are emitted as
 * absolute zip paths (`OEBPS/ch2.xhtml#note3`) and resolved to positions later by
 * [EpubParser], once every chapter is known.
 */
internal class HtmlConverter(
    private val css: CssRules,
    private val resolve: (href: String) -> String,
) {
    private val blocks = ArrayList<Block>()
    private val anchors = HashMap<String, Int>()
    private val footnotes = HashMap<String, List<Block>>()
    private val paragraph = ParagraphBuilder()
    private var firstHeading: String? = null

    private val kinds = ArrayList<TextKind>()
    private val aligns = ArrayList<Align>()
    private var preDepth = 0

    fun convert(body: Element): ConvertedChapter {
        walk(body)
        flush()
        return ConvertedChapter(blocks, anchors, firstHeading, footnotes)
    }

    private fun walk(node: Node) {
        for (child in node.childNodes()) {
            when (child) {
                is TextNode -> paragraph.append(child.wholeText, preserveWhitespace = preDepth > 0)
                is Element -> element(child)
            }
        }
    }

    private fun element(el: Element) {
        val tag = el.normalName().substringAfter(':')
        if (tag in ignored) return

        val id = el.id()
        if (id.isNotEmpty()) anchors.putIfAbsent(id, blocks.size)

        val epubType = el.attr("epub:type")
        if (tag == "aside" && footnoteTypes.any { epubType.contains(it) } && id.isNotEmpty()) {
            footnotes[id] = HtmlConverter(css, resolve).convert(el).blocks
            return
        }

        val style = css.styleOf(tag, el.classNames(), el.attr("style").takeIf { it.isNotEmpty() })
        if (style.hidden) return

        when (tag) {
            "br" -> {
                paragraph.lineBreak()
                return
            }
            "hr" -> {
                flush()
                blocks += Block.Separator
                return
            }
            "img" -> {
                image(el.attr("src"), el.attr("alt"))
                return
            }
            "image" -> {
                image(el.attr("xlink:href").ifEmpty { el.attr("href") }, null)
                return
            }
            "svg" -> {
                // Cover pages often wrap a single <image> in an <svg>; everything else is dropped.
                el.getElementsByTag("image").firstOrNull()?.let { element(it) }
                    ?: el.getElementsByTag("svg:image").firstOrNull()?.let { element(it) }
                return
            }
        }

        val block = tag in blockTags
        val heading = headingLevel(tag)
        if (block) flush()

        val kind = when {
            heading > 0 -> TextKind.heading(heading)
            tag == "blockquote" -> TextKind.Cite
            tag == "pre" -> TextKind.Preformatted
            else -> null
        }
        if (kind != null) kinds += kind
        if (style.align != null) aligns += style.align
        if (tag == "pre") preDepth++

        if (tag == "li") paragraph.append(listMarker(el))

        val target = linkTarget(el, tag)
        var flags = inlineFlags(tag) or style.flags
        if (target == null) {
            flags = flags and Span.LINK.inv()
        } else if (!target.contains("://") && isNoteRef(el, epubType)) {
            flags = (flags and Span.LINK.inv()) or Span.NOTE_REF
        }
        val pushed = paragraph.push(flags, target)
        walk(el)
        if (pushed) paragraph.pop()

        if (block) flush()
        if (tag == "pre") preDepth--
        if (style.align != null) aligns.removeAt(aligns.lastIndex)
        if (kind != null) kinds.removeAt(kinds.lastIndex)
    }

    private fun linkTarget(el: Element, tag: String): String? {
        if (tag != "a") return null
        val href = el.attr("href").trim()
        if (href.isEmpty()) return null
        return if (href.contains("://") || href.startsWith("mailto:")) href else resolve(href)
    }

    /**
     * EPUB3 marks note references explicitly; most converted books (especially from FB2)
     * don't, so short superscript-like link labels are treated as notes too.
     */
    private fun isNoteRef(a: Element, epubType: String): Boolean {
        if (epubType.contains("noteref")) return true
        if (!a.attr("href").contains('#')) return false
        val label = a.text().trim()
        if (label.isEmpty() || label.length > 6) return false
        val cls = a.className().lowercase()
        return a.parent()?.normalName() == "sup" ||
            a.getElementsByTag("sup").isNotEmpty() ||
            cls.contains("note") ||
            noteLabel.matches(label)
    }

    private fun inlineFlags(tag: String): Int = when (tag) {
        "b", "strong" -> Span.BOLD
        "i", "em", "cite", "dfn", "var" -> Span.ITALIC
        "sup" -> Span.SUPERSCRIPT
        "sub" -> Span.SUBSCRIPT
        "s", "strike", "del" -> Span.STRIKE
        "code", "tt", "kbd", "samp" -> Span.MONOSPACE
        "small" -> Span.SMALL
        "a" -> Span.LINK
        else -> 0
    }

    private fun listMarker(li: Element): String {
        val parent = li.parent()
        return if (parent != null && parent.normalName() == "ol") {
            val start = parent.attr("start").toIntOrNull() ?: 1
            "${start + li.elementSiblingIndex()}. "
        } else {
            "• "
        }
    }

    private fun image(src: String, alt: String?) {
        if (src.isBlank() || src.startsWith("data:")) return
        flush()
        blocks += Block.Image(resolve(src).substringBefore('#'), alt?.takeIf { it.isNotBlank() })
    }

    private fun flush() {
        val kind = kinds.lastOrNull() ?: TextKind.Body
        val align = aligns.lastOrNull() ?: Align.Default
        paragraph.flush(kind, align)?.let {
            if (firstHeading == null && kind.isHeading) firstHeading = it.text.replace('\n', ' ')
            blocks += it
        }
    }

    private fun headingLevel(tag: String): Int =
        if (tag.length == 2 && tag[0] == 'h' && tag[1] in '1'..'6') tag[1] - '0' else 0

    private companion object {
        val ignored = setOf("head", "script", "style", "title", "meta", "link", "noscript", "iframe", "object", "audio", "video", "math")
        val footnoteTypes = listOf("footnote", "endnote", "rearnote", "note")
        val noteLabel = Regex("^[\\[(]?(\\d{1,4}|\\*{1,3})[])]?$")
        val blockTags = setOf(
            "p", "div", "li", "dt", "dd", "blockquote", "section", "article", "header", "footer",
            "figure", "figcaption", "pre", "aside", "nav", "main", "center", "address", "tr", "td", "th",
            "table", "ul", "ol", "dl", "h1", "h2", "h3", "h4", "h5", "h6", "body", "caption",
        )
    }
}
