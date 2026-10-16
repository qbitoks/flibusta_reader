package app.folio.core.format.html

import app.folio.core.format.ResourceSink
import app.folio.core.format.UnsupportedBookException
import app.folio.core.format.epub.CssRules
import app.folio.core.format.epub.EpubParser
import app.folio.core.format.epub.HtmlConverter
import app.folio.core.format.resolveInternalTargets
import app.folio.core.format.splitChapters
import app.folio.core.model.Block
import app.folio.core.model.Book
import app.folio.core.model.BookMetadata
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * A single HTML document as a book: a saved web page, Flibusta's `html.zip`, or the text of a
 * MOBI. Chapters split at headings; images come from [images] by their resolved path.
 */
internal object HtmlBookParser {

    fun parse(
        html: String,
        resources: ResourceSink,
        images: (path: String) -> ByteArray? = { null },
        dir: String = "",
        metadata: (Document) -> BookMetadata = ::readMetadata,
    ): Book {
        val doc = Jsoup.parse(html)
        val css = doc.select("style").fold(CssRules.Empty) { rules, style -> rules + CssRules.parse(style.data()) }
        val converted = HtmlConverter(css) { href ->
            when {
                href.startsWith("#") -> href
                href.startsWith("rec:") -> href
                else -> {
                    // Links into the same document keep only their fragment.
                    val fragment = href.substringAfter('#', "")
                    if (fragment.isNotEmpty()) "#$fragment" else EpubParser.resolvePath(dir, href)
                }
            }
        }.convert(doc.body())
        if (converted.blocks.none { it is Block.Text && it.text.isNotBlank() || it is Block.Image }) {
            throw UnsupportedBookException("HTML has no readable text")
        }
        val split = splitChapters(converted.blocks, converted.anchors)
        val stored = HashSet<String>()
        val blocks = split.chapters.flatMap { it.blocks }
        for (block in blocks) {
            if (block is Block.Image && stored.add(block.resource)) images(block.resource)?.let { resources.put(block.resource, it) }
        }
        val book = Book(metadata(doc), split.chapters, split.toc, converted.footnotes.mapKeys { "#${it.key}" })
        return resolveInternalTargets(book) { target ->
            converted.anchors[target.substringAfter('#')]?.let(split::position)
        }
    }

    private fun readMetadata(doc: Document): BookMetadata {
        fun meta(name: String) = doc.selectFirst("meta[name=$name]")?.attr("content")?.trim()?.takeIf { it.isNotEmpty() }
        return BookMetadata(
            title = doc.title().trim(),
            authors = listOfNotNull(meta("author")),
            language = doc.selectFirst("html")?.attr("lang")?.takeIf { it.isNotBlank() },
            description = meta("description"),
        )
    }
}
