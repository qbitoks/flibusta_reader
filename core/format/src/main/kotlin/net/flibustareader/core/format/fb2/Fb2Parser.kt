package net.flibustareader.core.format.fb2

import net.flibustareader.core.format.ParagraphBuilder
import net.flibustareader.core.format.ResourceSink
import net.flibustareader.core.format.UnsupportedBookException
import net.flibustareader.core.format.resolveInternalTargets
import net.flibustareader.core.model.Align
import net.flibustareader.core.model.Block
import net.flibustareader.core.model.Book
import net.flibustareader.core.model.BookMetadata
import net.flibustareader.core.model.Chapter
import net.flibustareader.core.model.Position
import net.flibustareader.core.model.Span
import net.flibustareader.core.model.TextKind
import net.flibustareader.core.model.TocEntry
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream
import java.io.StringReader
import java.nio.charset.Charset
import java.util.Base64
import javax.xml.parsers.SAXParserFactory

/**
 * Streaming FictionBook 2 parser. SAX keeps memory flat even for books with megabytes
 * of embedded base64 images, which a DOM parser would hold in full.
 */
internal class Fb2Parser(private val resources: ResourceSink) {

    fun parse(input: InputStream): Book {
        val xml = decode(readBounded(input))
        val handler = Handler(resources)
        try {
            run(xml, handler)
        } catch (e: SAXException) {
            // Hand-made FB2 files often use HTML entities or stray ampersands; repair and retry once.
            val retry = Handler(resources)
            run(sanitize(xml), retry)
            return retry.build()
        }
        return handler.build()
    }

    private fun run(xml: String, handler: Handler) {
        // No DTD and no external entities (project rule): a DOCTYPE fails the parse.
        val factory = SAXParserFactory.newInstance().apply { isNamespaceAware = true }
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        runCatching { factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        factory.newSAXParser().parse(InputSource(StringReader(xml)), handler)
    }

    private class SectionMark(val depth: Int, val block: Int, val id: String?) {
        var title: String? = null
    }

    private class Handler(private val resources: ResourceSink) : DefaultHandler() {
        // Description
        private var title = ""
        private val authors = ArrayList<String>()
        private var language: String? = null
        private var series: String? = null
        private var seriesIndex: Int? = null
        private val annotation = StringBuilder()
        private var coverId: String? = null
        private val authorParts = HashMap<String, String>()

        // Content
        private val blocks = ArrayList<Block>()
        private val sections = ArrayList<SectionMark>()
        private val openSections = ArrayList<SectionMark>()
        private val anchors = HashMap<String, Int>()
        private val footnotes = HashMap<String, List<Block>>()
        private var noteBlocks: ArrayList<Block>? = null
        private var noteId: String? = null
        private var noteDepth = 0

        private val paragraph = ParagraphBuilder()
        private val kinds = ArrayList<TextKind>()
        private val path = ArrayList<String>()
        private val pushed = ArrayList<Boolean>()
        private var textDepth = 0
        private var inNotesBody = false
        private var inBody = false
        private var inTitleInfo = false
        private var titleDepth = 0
        private val titleText = StringBuilder()
        private val field = StringBuilder()
        private var binaryId: String? = null
        private var binary: StringBuilder? = null

        private val target: ArrayList<Block> get() = noteBlocks ?: blocks

        override fun startElement(uri: String?, localName: String, qName: String?, attrs: Attributes) {
            val name = localName.ifEmpty { qName.orEmpty().substringAfter(':') }
            path += name
            if (path.size > MAX_DEPTH) throw IllegalArgumentException("FB2 nesting deeper than $MAX_DEPTH")
            var didPush = false
            when (name) {
                "title-info" -> inTitleInfo = true
                "body" -> {
                    inBody = true
                    val bodyName = attrs.getValue("name").orEmpty()
                    inNotesBody = bodyName == "notes" || bodyName == "comments" || (bodyName.isNotEmpty() && blocks.isNotEmpty())
                }
                "section" -> if (inBody) {
                    val id = attrs.getValue("id")
                    if (inNotesBody) {
                        if (id != null && noteBlocks == null) {
                            noteId = id
                            noteDepth = path.count { it == "section" }
                            noteBlocks = ArrayList()
                        }
                    } else {
                        flush()
                        val mark = SectionMark(openSections.size + 1, blocks.size, id)
                        sections += mark
                        openSections += mark
                        if (id != null) anchors.putIfAbsent(id, blocks.size)
                    }
                }
                "title" -> if (inBody) {
                    flush()
                    titleDepth++
                    kinds += if (openSections.isEmpty()) TextKind.Heading1 else TextKind.heading(openSections.size)
                    titleText.setLength(0)
                }
                "epigraph" -> kinds += TextKind.Epigraph
                "cite" -> kinds += TextKind.Cite
                "poem" -> kinds += TextKind.Poem
                "subtitle" -> {
                    flush()
                    kinds += TextKind.Subtitle
                    textDepth++
                }
                "text-author" -> {
                    flush()
                    kinds += TextKind.TextAuthor
                    textDepth++
                }
                "p", "v", "td", "th" -> if (inBody || inAnnotation()) {
                    if (name != "v") flush()
                    textDepth++
                    attrs.getValue("id")?.let { anchors.putIfAbsent(it, blocks.size) }
                }
                "empty-line" -> if (inBody) {
                    flush()
                    target += Block.Separator
                }
                "image" -> {
                    val href = href(attrs)?.removePrefix("#")
                    if (href != null) {
                        if (path.contains("coverpage")) {
                            if (coverId == null) coverId = href
                        } else if (inBody) {
                            flush()
                            target += Block.Image(href, attrs.getValue("title"))
                        }
                    }
                }
                "strong" -> didPush = paragraph.push(Span.BOLD)
                "emphasis" -> didPush = paragraph.push(Span.ITALIC)
                "strikethrough" -> didPush = paragraph.push(Span.STRIKE)
                "sub" -> didPush = paragraph.push(Span.SUBSCRIPT)
                "sup" -> didPush = paragraph.push(Span.SUPERSCRIPT)
                "code" -> didPush = paragraph.push(Span.MONOSPACE)
                "a" -> {
                    val href = href(attrs)
                    if (href != null) {
                        val isNote = attrs.getValue("type") == "note"
                        val external = !href.startsWith("#")
                        didPush = paragraph.push(
                            if (isNote && !external) Span.NOTE_REF else Span.LINK,
                            if (external) href else href.substring(1),
                        )
                    }
                }
                "sequence" -> if (inTitleInfo && series == null) {
                    series = attrs.getValue("name")?.trim()?.takeIf { it.isNotEmpty() }
                    seriesIndex = attrs.getValue("number")?.trim()?.toIntOrNull()
                }
                "binary" -> {
                    binaryId = attrs.getValue("id")
                    binary = StringBuilder()
                }
            }
            pushed += didPush
            field.setLength(0)
        }

        override fun endElement(uri: String?, localName: String, qName: String?) {
            val name = path.removeAt(path.lastIndex)
            if (pushed.removeAt(pushed.lastIndex)) paragraph.pop()
            when (name) {
                "title-info" -> inTitleInfo = false
                "book-title" -> if (inTitleInfo) title = field.toString().trim()
                "lang" -> if (inTitleInfo) language = field.toString().trim().takeIf { it.isNotEmpty() }
                "first-name", "middle-name", "last-name", "nickname" -> if (inTitleInfo && path.lastOrNull() == "author") {
                    authorParts[name] = field.toString().trim()
                }
                "author" -> if (inTitleInfo) {
                    val full = listOfNotNull(authorParts["first-name"], authorParts["middle-name"], authorParts["last-name"])
                        .filter { it.isNotEmpty() }.joinToString(" ")
                        .ifEmpty { authorParts["nickname"].orEmpty() }
                    if (full.isNotEmpty()) authors += full
                    authorParts.clear()
                }
                "body" -> {
                    flush()
                    inBody = false
                    inNotesBody = false
                }
                "section" -> if (inBody) {
                    if (inNotesBody) {
                        if (noteBlocks != null && path.count { it == "section" } + 1 == noteDepth) finishNote()
                    } else {
                        flush()
                        openSections.removeLastOrNull()
                    }
                }
                "title" -> if (inBody) {
                    flush()
                    titleDepth--
                    kinds.removeLastOrNull()
                    if (!inNotesBody) openSections.lastOrNull()?.let { if (it.title == null) it.title = titleText.toString().trim() }
                }
                "epigraph", "cite", "poem" -> {
                    flush()
                    kinds.removeLastOrNull()
                }
                "subtitle", "text-author" -> {
                    flush()
                    kinds.removeLastOrNull()
                    textDepth--
                }
                "stanza" -> flush()
                "v" -> if (textDepth > 0) {
                    paragraph.lineBreak()
                    textDepth--
                }
                "p", "td", "th" -> if (textDepth > 0) {
                    textDepth--
                    if (inAnnotation()) {
                        paragraph.flush()?.let { annotation.append(it.text).append('\n') }
                    } else {
                        val block = flush()
                        if (block != null && titleDepth > 0 && !inNotesBody) {
                            if (titleText.isNotEmpty()) titleText.append(". ")
                            titleText.append(block.text.replace('\n', ' '))
                        }
                    }
                }
                "binary" -> {
                    val id = binaryId
                    val data = binary
                    if (id != null && data != null) {
                        runCatching { Base64.getMimeDecoder().decode(data.toString()) }
                            .onSuccess { resources.put(id, it) }
                    }
                    binaryId = null
                    binary = null
                }
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            binary?.let {
                it.appendRange(ch, start, start + length)
                return
            }
            if (textDepth > 0) {
                paragraph.append(String(ch, start, length))
            } else if (inTitleInfo) {
                field.appendRange(ch, start, start + length)
            }
        }

        private fun inAnnotation() = inTitleInfo && path.contains("annotation")

        private fun href(attrs: Attributes): String? {
            for (i in 0 until attrs.length) {
                val n = attrs.getLocalName(i).ifEmpty { attrs.getQName(i).substringAfter(':') }
                if (n == "href") return attrs.getValue(i).trim().takeIf { it.isNotEmpty() }
            }
            return null
        }

        private fun finishNote() {
            flush()
            val id = noteId
            val list = noteBlocks
            if (id != null && list != null) footnotes[id] = list.toList()
            noteBlocks = null
            noteId = null
        }

        private fun flush(): Block.Text? {
            val kind = kinds.lastOrNull() ?: TextKind.Body
            val align = if (kind.isHeading || kind == TextKind.Subtitle) Align.Center else Align.Default
            val block = paragraph.flush(kind, align) ?: return null
            // Note titles ("1", "*") duplicate the reference label, so they are dropped.
            if (!(inNotesBody && titleDepth > 0)) target += block
            return block
        }

        fun build(): Book {
            flush()
            if (blocks.isEmpty()) throw UnsupportedBookException("FB2 has no body text")

            val splitDepth = chooseSplitDepth()
            val cuts = sections.filter { it.depth <= splitDepth }.map { it.block }.toMutableList()
            if (cuts.firstOrNull() != 0) cuts.add(0, 0)
            val boundaries = cuts.distinct().sorted()

            val chapterOf = IntArray(blocks.size)
            val chapters = ArrayList<Chapter>(boundaries.size)
            for ((i, start) in boundaries.withIndex()) {
                val end = boundaries.getOrElse(i + 1) { blocks.size }
                if (end <= start) continue
                for (b in start until end) chapterOf[b] = chapters.size
                val localAnchors = anchors.filterValues { it in start until end }.mapValues { it.value - start }
                val mark = sections.firstOrNull { it.block == start && it.depth <= splitDepth }
                chapters += Chapter(mark?.title?.takeIf { it.isNotEmpty() }, blocks.subList(start, end).toList(), localAnchors)
            }
            val chapterStart = IntArray(chapters.size)
            run {
                var c = -1
                for (b in blocks.indices) if (chapterOf[b] != c) {
                    c = chapterOf[b]
                    chapterStart[c] = b
                }
            }
            fun positionOf(globalBlock: Int): Position {
                val b = globalBlock.coerceIn(0, blocks.lastIndex)
                return Position(chapterOf[b], b - chapterStart[chapterOf[b]])
            }

            val book = Book(
                metadata = BookMetadata(
                    title = title.ifEmpty { "Untitled" },
                    authors = authors.distinct(),
                    language = language,
                    series = series,
                    seriesIndex = seriesIndex,
                    description = annotation.toString().trim().takeIf { it.isNotEmpty() },
                    coverResource = coverId,
                ),
                chapters = chapters,
                toc = buildToc(::positionOf),
                footnotes = footnotes,
            )
            return resolveInternalTargets(book) { id -> anchors[id]?.let(::positionOf) }
        }

        /** Split at the first level that has more than one section, so single-wrapper books still get chapters. */
        private fun chooseSplitDepth(): Int {
            var depth = 1
            while (depth < 4 && sections.count { it.depth == depth } <= 1 && sections.any { it.depth == depth + 1 }) depth++
            return depth
        }

        private fun buildToc(positionOf: (Int) -> Position): List<TocEntry> {
            fun level(from: Int, to: Int, depth: Int): List<TocEntry> {
                val result = ArrayList<TocEntry>()
                var i = from
                while (i < to) {
                    val mark = sections[i]
                    if (mark.depth == depth) {
                        var j = i + 1
                        while (j < to && sections[j].depth > depth) j++
                        val children = if (depth < 3) level(i + 1, j, depth + 1) else emptyList()
                        val title = mark.title
                        if (!title.isNullOrEmpty()) result += TocEntry(title, positionOf(mark.block), children)
                        else result += children
                        i = j
                    } else {
                        i++
                    }
                }
                return result
            }
            return level(0, sections.size, 1)
        }
    }

    companion object {
        /** Project limits: unpacked FB2 size and element nesting. */
        const val MAX_BYTES = 50L * 1024 * 1024
        const val MAX_DEPTH = 128

        private fun readBounded(input: InputStream): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                if (out.size() + n > MAX_BYTES) throw IllegalArgumentException("FB2 is larger than 50 MiB")
                out.write(buffer, 0, n)
            }
            return out.toByteArray()
        }

        private val encodingAttr = Regex("encoding\\s*=\\s*[\"']([A-Za-z0-9._-]+)[\"']")
        private val badEntity = Regex("&(?!(amp|lt|gt|quot|apos|#\\d+|#[xX][0-9a-fA-F]+);)([a-zA-Z][a-zA-Z0-9]*;)?")
        private val htmlEntities = mapOf(
            "nbsp;" to "&#160;", "mdash;" to "&#8212;", "ndash;" to "&#8211;", "laquo;" to "&#171;",
            "raquo;" to "&#187;", "hellip;" to "&#8230;", "copy;" to "&#169;", "bdquo;" to "&#8222;",
            "ldquo;" to "&#8220;", "rdquo;" to "&#8221;", "shy;" to "&#173;",
        )

        fun decode(bytes: ByteArray): String {
            if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
                return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
            }
            if (bytes.size >= 2 && (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() || bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte())) {
                return String(bytes, Charsets.UTF_16)
            }
            val prolog = String(bytes, 0, minOf(bytes.size, 256), Charsets.ISO_8859_1)
            val name = encodingAttr.find(prolog.substringBefore("?>"))?.groupValues?.get(1)
            val charset = name?.let { runCatching { Charset.forName(it) }.getOrNull() } ?: Charsets.UTF_8
            // The declared encoding is already applied here, so the parser must not re-apply it.
            return String(bytes, charset).replaceFirst(encodingAttr, "encoding=\"UTF-8\"")
        }

        fun sanitize(xml: String): String = xml.replace(badEntity) { m ->
            val entity = m.groupValues[2]
            htmlEntities[entity] ?: if (entity.isEmpty()) "&amp;" else "&amp;$entity"
        }
    }
}
