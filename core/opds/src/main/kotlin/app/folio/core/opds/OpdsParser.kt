package app.folio.core.opds

import java.net.URI

/** A titled link to another feed: «Все книги автора …», «Книги автора по сериям». */
data class CatalogLink(val title: String, val href: String)

data class CatalogEntry(
    val id: String,
    val title: String,
    val author: String,
    /** Plain text; paragraphs separated by newlines, the «Год издания / Формат / Размер» tail removed. */
    val summary: String,
    /** Readable formats («EPUB», «FB2») → acquisition link. */
    val downloads: Map<String, String>,
    /** Feed to open for a folder entry; null for a book. */
    val navigation: String?,
    /** Cover (books) or photo (authors), thumbnail preferred. */
    val cover: String? = null,
    val related: List<CatalogLink> = emptyList(),
    val genres: List<String> = emptyList(),
    val language: String? = null,
    val year: String? = null,
    /** File size the catalog reports, in bytes. */
    val size: Long? = null,
    /** Offered formats this app can't open («PDF», «MOBI»). */
    val otherFormats: List<String> = emptyList(),
) {
    val isBook: Boolean get() = navigation == null
}

data class CatalogPage(val title: String, val entries: List<CatalogEntry>, val next: String?)

object OpdsParser {
    private val readable = mapOf("epub" to "EPUB", "fb2" to "FB2")
    private val coverRels = listOf(
        "http://opds-spec.org/image/thumbnail",
        "http://opds-spec.org/thumbnail",
        "x-stanza-cover-image-thumbnail",
        "http://opds-spec.org/image",
        "x-stanza-cover-image",
    )

    fun parse(xml: String, base: String): CatalogPage {
        val root = Xml.parse(xml.toByteArray()).documentElement
        require(root.localName == "feed") { "Источник вернул не OPDS каталог" }
        fun resolve(link: org.w3c.dom.Element): String? {
            val href = link.getAttribute("href")
            if (href.isBlank()) return null
            val ancestry = mutableListOf<org.w3c.dom.Element>()
            var node: org.w3c.dom.Node? = link
            while (node is org.w3c.dom.Element) { ancestry += node; node = node.parentNode }
            fun relative(uri: String, value: String) = if (value.startsWith("?")) uri.substringBefore('#').substringBefore('?') + value else URI(uri).resolve(value).toString()
            var effective = base
            ancestry.asReversed().forEach { element ->
                element.getAttributeNS("http://www.w3.org/XML/1998/namespace", "base").takeIf { it.isNotBlank() }?.let { effective = relative(effective, it) }
            }
            val address = runCatching { relative(effective, href) }.getOrNull() ?: return null
            return address.takeIf { runCatching { URI(it).scheme == "https" }.getOrDefault(false) }
        }
        val entries = Xml.children(root, "entry").map { entry ->
            val links = Xml.children(entry, "link")
            val downloads = linkedMapOf<String, String>()
            val otherFormats = linkedSetOf<String>()
            links.filter { it.getAttribute("rel").startsWith("http://opds-spec.org/acquisition") }.forEach {
                val type = it.getAttribute("type")
                val format = readable.entries.firstOrNull { (key, _) -> type.contains(key) }?.value
                val address = resolve(it)
                when {
                    format != null && address != null -> downloads.putIfAbsent(format, address)
                    format == null -> formatName(type)?.let(otherFormats::add)
                }
            }
            val navigation = links.firstOrNull { it.getAttribute("type").contains("atom+xml") && it.getAttribute("rel").let { rel -> rel.isEmpty() || rel == "subsection" || rel.startsWith("http://opds-spec.org/sort") } }
                ?.let(::resolve)
                ?: links.firstOrNull { it.getAttribute("type").contains("atom+xml") && it.getAttribute("rel") !in listOf("related", "alternate") && !it.getAttribute("rel").contains("facet") }
                    ?.let(::resolve)
            val cover = coverRels.firstNotNullOfOrNull { rel -> links.firstOrNull { it.getAttribute("rel") == rel }?.let(::resolve) }
            val related = links
                .filter { it.getAttribute("type").contains("atom+xml") && (it.getAttribute("rel") == "related" || it.getAttribute("rel").contains("facet")) }
                .mapNotNull { link -> resolve(link)?.let { CatalogLink(link.getAttribute("title").trim(), it) } }
                .filter { it.title.isNotBlank() }
            fun text(name: String) = Xml.children(entry, name).firstOrNull()?.textContent.orEmpty().trim()
            val content = Description.parse(text("content").ifBlank { text("summary") })
            CatalogEntry(
                id = text("id").ifBlank { downloads.values.firstOrNull() ?: navigation ?: text("title") },
                title = text("title"),
                author = Xml.children(entry, "author").joinToString(", ") { Xml.children(it, "name").firstOrNull()?.textContent.orEmpty().trim() },
                summary = content.text,
                downloads = downloads,
                navigation = navigation.takeIf { downloads.isEmpty() && otherFormats.isEmpty() },
                cover = cover,
                related = related,
                genres = Xml.children(entry, "category").mapNotNull { c -> c.getAttribute("label").ifBlank { c.getAttribute("term") }.trim().takeIf { it.isNotEmpty() } }.distinct(),
                language = text("language").ifBlank { content.details["Язык"].orEmpty() }.takeIf { it.isNotBlank() },
                year = text("issued").ifBlank { content.details["Год издания"].orEmpty() }.takeIf { it.isNotBlank() },
                size = content.details["Размер"]?.let(::parseSize),
                otherFormats = otherFormats.toList(),
            )
        }
        val next = Xml.children(root, "link").firstOrNull { it.getAttribute("rel") == "next" }?.let(::resolve)
        return CatalogPage(Xml.children(root, "title").firstOrNull()?.textContent.orEmpty().trim(), entries, next)
    }

    /** «application/x-mobipocket-ebook» → «MOBI», «application/rtf+zip» → «RTF». */
    private fun formatName(type: String): String? {
        val subtype = type.substringAfter('/', "").substringBefore(';').removeSuffix("+zip").removePrefix("x-").trim()
        if (subtype.isEmpty() || subtype.contains("atom")) return null
        return when {
            subtype.startsWith("mobipocket") -> "MOBI"
            subtype == "octet-stream" -> null
            else -> subtype.uppercase()
        }
    }

    /** «825 Kb», «1.2 Mb» → bytes. */
    private fun parseSize(value: String): Long? {
        val match = Regex("""([\d.,]+)\s*([KkMm])""").find(value) ?: return null
        val number = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val unit = if (match.groupValues[2].lowercase() == "m") 1024.0 * 1024 else 1024.0
        return (number * unit).toLong()
    }
}

/** Flibusta puts HTML into `<content>`; this turns it into paragraphs and pulls out the «Ключ: значение» tail. */
internal object Description {
    data class Parsed(val text: String, val details: Map<String, String>)

    private val detailKeys = listOf("Год издания", "Формат", "Язык", "Размер")

    fun parse(html: String): Parsed {
        val plain = decodeEntities(
            html.replace(Regex("(?i)<br\\s*/?>|</p>|</div>|</li>"), "\n").replace(Regex("<[^>]*>"), ""),
        )
        val details = linkedMapOf<String, String>()
        val lines = plain.lines()
            .map { it.replace(Regex("[\\s ]+"), " ").trim() }
            .filter { line ->
                val key = detailKeys.firstOrNull { line.startsWith("$it:") }
                if (key != null) details[key] = line.substringAfter(':').trim()
                key == null && line.isNotEmpty()
            }
        return Parsed(lines.joinToString("\n"), details)
    }

    private val named = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
        "laquo" to "«", "raquo" to "»", "mdash" to "—", "ndash" to "–", "hellip" to "…",
        "bdquo" to "„", "ldquo" to "“", "rdquo" to "”", "lsquo" to "‘", "rsquo" to "’", "copy" to "©",
    )

    fun decodeEntities(value: String): String = Regex("&(#x[0-9a-fA-F]+|#\\d+|[a-zA-Z]+);").replace(value) { m ->
        val code = m.groupValues[1]
        when {
            code.startsWith("#x") || code.startsWith("#X") -> code.drop(2).toIntOrNull(16)?.let { String(Character.toChars(it)) }
            code.startsWith("#") -> code.drop(1).toIntOrNull()?.takeIf { Character.isValidCodePoint(it) }?.let { String(Character.toChars(it)) }
            else -> named[code]
        } ?: m.value
    }
}
