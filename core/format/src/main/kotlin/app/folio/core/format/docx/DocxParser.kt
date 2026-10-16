package app.folio.core.format.docx

import app.folio.core.format.ParagraphBuilder
import app.folio.core.format.ResourceSink
import app.folio.core.format.UnsupportedBookException
import app.folio.core.format.epub.EpubParser
import app.folio.core.format.readBounded
import app.folio.core.format.resolveInternalTargets
import app.folio.core.format.splitChapters
import app.folio.core.model.Align
import app.folio.core.model.Block
import app.folio.core.model.Book
import app.folio.core.model.BookMetadata
import app.folio.core.model.Span
import app.folio.core.model.TextKind
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.File
import java.util.zip.ZipFile

/**
 * Word 2007+ documents: paragraphs, headings (by style), bold/italic/strike/super/sub,
 * alignment, line breaks, images and footnotes. Tables read cell by cell; layout is dropped.
 */
internal class DocxParser(private val file: File, private val resources: ResourceSink) {

    private lateinit var zip: ZipFile
    private val headingStyles = HashMap<String, Int>()
    private val relations = HashMap<String, String>()
    private val footnotes = HashMap<String, List<Block>>()

    fun parse(): Book = ZipFile(file).use { zip ->
        this.zip = zip
        val document = xml("word/document.xml") ?: throw UnsupportedBookException("DOCX without document.xml")
        readStyles()
        xml("word/_rels/document.xml.rels")?.select("Relationship")?.forEach {
            relations[it.attr("Id")] = EpubParser.resolvePath("word", it.attr("Target"))
        }
        xml("word/footnotes.xml")?.select("w|footnote")?.forEach { note ->
            val id = note.attr("w:id")
            if (note.attr("w:type").isEmpty()) footnotes["fn$id"] = convert(note)
        }
        val body = document.selectFirst("w|body") ?: throw UnsupportedBookException("DOCX without body")
        val blocks = convert(body)
        if (blocks.isEmpty()) throw UnsupportedBookException("DOCX has no text")
        val split = splitChapters(blocks)
        resolveInternalTargets(Book(readMetadata(), split.chapters, split.toc, footnotes)) { null }
    }

    private fun convert(root: Element): List<Block> {
        val blocks = ArrayList<Block>()
        for (p in root.select("w|p")) {
            // Paragraphs inside text boxes are reached through their own w:p.
            val builder = ParagraphBuilder()
            val props = p.selectFirst("> w|pPr")
            val style = props?.selectFirst("> w|pStyle")?.attr("w:val").orEmpty()
            val outline = props?.selectFirst("> w|outlineLvl")?.attr("w:val")?.toIntOrNull()
            val level = headingStyles[style] ?: outline?.let { it + 1 }
            val align = when (props?.selectFirst("> w|jc")?.attr("w:val")) {
                "center" -> Align.Center
                "right", "end" -> Align.End
                "both", "distribute" -> Align.Justify
                "left", "start" -> Align.Start
                else -> Align.Default
            }
            val images = ArrayList<String>()
            for (run in p.select("> w|r, > w|hyperlink > w|r, > w|ins > w|r, > w|smartTag > w|r")) {
                val rp = run.selectFirst("> w|rPr")
                fun on(tag: String) = rp?.selectFirst("> w|$tag")?.attr("w:val")?.let { it != "0" && it != "false" } ?: false
                var flags = 0
                if (on("b")) flags = flags or Span.BOLD
                if (on("i")) flags = flags or Span.ITALIC
                if (on("strike") || on("dstrike")) flags = flags or Span.STRIKE
                when (rp?.selectFirst("> w|vertAlign")?.attr("w:val")) {
                    "superscript" -> flags = flags or Span.SUPERSCRIPT
                    "subscript" -> flags = flags or Span.SUBSCRIPT
                }
                val pushed = builder.push(flags)
                for (child in run.children()) when (child.tagName()) {
                    "w:t" -> builder.append(child.wholeText(), preserveWhitespace = true)
                    "w:tab" -> builder.append(" ", preserveWhitespace = true)
                    "w:br", "w:cr" -> builder.lineBreak()
                    "w:noBreakHyphen" -> builder.append("‑", preserveWhitespace = true)
                    "w:footnoteReference" -> {
                        val id = "fn${child.attr("w:id")}"
                        if (footnotes.containsKey(id)) {
                            builder.push(Span.NOTE_REF or Span.SUPERSCRIPT, id)
                            builder.append(child.attr("w:id"), preserveWhitespace = true)
                            builder.pop()
                        }
                    }
                    "w:drawing", "w:pict" -> child.select("a|blip, v|imagedata").forEach { blip ->
                        val rel = blip.attr("r:embed").ifEmpty { blip.attr("r:id") }
                        relations[rel]?.let(images::add)
                    }
                }
                if (pushed) builder.pop()
            }
            val kind = if (level != null && level in 1..6) TextKind.heading(level) else TextKind.Body
            builder.flush(kind, align)?.let { blocks += it }
            for (path in images) {
                val entry = zip.getEntry(path) ?: continue
                resources.put(path, zip.getInputStream(entry).use(::readBounded))
                blocks += Block.Image(path)
            }
        }
        return blocks
    }

    private fun readStyles() {
        xml("word/styles.xml")?.select("w|style")?.forEach { style ->
            val name = style.selectFirst("w|name")?.attr("w:val")?.lowercase().orEmpty()
            val id = style.attr("w:styleId")
            val level = when {
                name == "title" -> 1
                name.startsWith("heading ") -> name.removePrefix("heading ").toIntOrNull()
                else -> style.selectFirst("w|pPr > w|outlineLvl")?.attr("w:val")?.toIntOrNull()?.plus(1)
            }
            if (level != null) headingStyles[id] = level
        }
    }

    private fun readMetadata(): BookMetadata {
        val core = xml("docProps/core.xml")
        fun dc(name: String) = core?.selectFirst("dc|$name")?.text()?.trim()?.takeIf { it.isNotEmpty() }
        return BookMetadata(
            title = dc("title").orEmpty(),
            authors = listOfNotNull(dc("creator")),
            language = dc("language"),
            description = dc("description"),
        )
    }

    private fun xml(path: String): Document? {
        val entry = zip.getEntry(path) ?: return null
        return Jsoup.parse(String(zip.getInputStream(entry).use(::readBounded), Charsets.UTF_8), "", Parser.xmlParser())
    }
}
