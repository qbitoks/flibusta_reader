package net.flibustareader.core

import java.io.File
import java.util.Base64
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.w3c.dom.Element
import org.w3c.dom.Node

object Fb2Converter {
    fun convert(input: File, output: File): String {
        require(input.length() <= 50L * 1024 * 1024) { "FB2 превышает 50 МБ" }
        val root = Xml.parse(input.readBytes()).documentElement
        require((root.localName ?: root.tagName) == "FictionBook") { "Файл не является FB2" }
        val info = Xml.descendants(root, "title-info").firstOrNull()
        fun text(name: String) = info?.let { Xml.descendants(it, name).firstOrNull()?.textContent?.trim() }.orEmpty()
        val title = text("book-title").ifBlank { input.nameWithoutExtension }
        val language = text("lang").ifBlank { "ru" }
        val author = info?.let { Xml.children(it, "author").joinToString(", ") { a ->
            listOf("first-name", "middle-name", "last-name").mapNotNull { n -> Xml.children(a, n).firstOrNull()?.textContent }.joinToString(" ")
        } }.orEmpty()
        val images = linkedMapOf<String, Triple<String, String, ByteArray>>()
        Xml.children(root, "binary").forEach { binary ->
            val mime = binary.getAttribute("content-type").lowercase()
            val ext = when (mime) { "image/jpeg" -> "jpg"; "image/png" -> "png"; "image/gif" -> "gif"; "image/webp" -> "webp"; else -> null }
            if (ext != null) {
                val path = "images/${images.size}.$ext"
                images[binary.getAttribute("id")] = Triple(path, mime, Base64.getMimeDecoder().decode(binary.textContent))
            }
        }
        val toc = mutableListOf<Pair<String, String>>()
        var sequence = 0
        fun render(node: Node): String {
            if (node.nodeType == Node.TEXT_NODE || node.nodeType == Node.CDATA_SECTION_NODE) return Xml.escape(node.nodeValue.orEmpty())
            val element = node as? Element ?: return ""
            val name = element.localName ?: element.tagName
            fun content(): String = (0 until element.childNodes.length).joinToString("") { render(element.childNodes.item(it)) }
            val sourceId = element.getAttribute("id")
            if (name == "section") {
                val id = sourceId.ifBlank { "section-${++sequence}" }
                Xml.children(element, "title").firstOrNull()?.textContent?.trim()?.takeIf { it.isNotBlank() }?.let { toc += id to it }
                return "<section id=\"${Xml.escape(id)}\">${content()}</section>"
            }
            if (name == "image") {
                val href = element.getAttributeNS("http://www.w3.org/1999/xlink", "href").ifBlank { element.getAttribute("href") }
                val image = images[href.removePrefix("#")] ?: return ""
                return "<img src=\"${image.first}\" alt=\"Иллюстрация\"/>"
            }
            if (name == "a") {
                val href = element.getAttributeNS("http://www.w3.org/1999/xlink", "href").ifBlank { element.getAttribute("href") }
                return if (href.startsWith("#")) "<a href=\"${Xml.escape(href)}\">${content()}</a>" else content()
            }
            if (name == "empty-line") return "<p>&#160;</p>"
            val tag = when (name) {
                "p", "strong", "sub", "sup", "table", "tr", "td", "th" -> name
                "emphasis" -> "em"
                "strikethrough" -> "s"
                "title", "subtitle" -> "header"
                "epigraph", "cite" -> "blockquote"
                "poem", "stanza", "body" -> "div"
                "v", "text-author" -> "p"
                else -> "span"
            }
            val id = if (sourceId.isBlank()) "" else " id=\"${Xml.escape(sourceId)}\""
            return "<$tag$id>${content()}</$tag>"
        }
        val bodies = Xml.children(root, "body")
        require(bodies.isNotEmpty()) { "В FB2 нет текста" }
        val html = bodies.joinToString("") { render(it) }
        if (toc.isEmpty()) toc += "start" to title
        val escapedTitle = Xml.escape(title)
        val content = """<?xml version="1.0" encoding="UTF-8"?><html xmlns="http://www.w3.org/1999/xhtml" xml:lang="${Xml.escape(language)}"><head><title>$escapedTitle</title><style>body{line-height:1.5}p{margin:0 0 .7em;text-indent:1.2em}header{font-size:1.3em;font-weight:bold;margin:1.5em 0}header p{text-indent:0}img{max-width:100%;height:auto}blockquote{margin:1em}table{max-width:100%}</style></head><body id="start">$html</body></html>"""
        val navItems = toc.joinToString("") { (id, name) -> "<li><a href=\"content.xhtml#${Xml.escape(id)}\">${Xml.escape(name)}</a></li>" }
        val nav = """<?xml version="1.0" encoding="UTF-8"?><html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>Оглавление</title></head><body><nav epub:type="toc"><h1>Оглавление</h1><ol>$navItems</ol></nav></body></html>"""
        val imageManifest = images.values.mapIndexed { i, image -> "<item id=\"image$i\" href=\"${image.first}\" media-type=\"${image.second}\"/>" }.joinToString("")
        val opf = """<?xml version="1.0" encoding="UTF-8"?><package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="book-id"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="book-id">urn:uuid:${UUID.randomUUID()}</dc:identifier><dc:title>$escapedTitle</dc:title><dc:creator>${Xml.escape(author)}</dc:creator><dc:language>${Xml.escape(language)}</dc:language><meta property="dcterms:modified">2000-01-01T00:00:00Z</meta></metadata><manifest><item id="content" href="content.xhtml" media-type="application/xhtml+xml"/><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>$imageManifest</manifest><spine><itemref idref="content"/></spine></package>"""
        ZipOutputStream(output.outputStream()).use { zip ->
            fun put(name: String, bytes: ByteArray, stored: Boolean = false) {
                val entry = ZipEntry(name)
                if (stored) { entry.method = ZipEntry.STORED; entry.size = bytes.size.toLong(); entry.compressedSize = entry.size; entry.crc = CRC32().apply { update(bytes) }.value }
                zip.putNextEntry(entry); zip.write(bytes); zip.closeEntry()
            }
            put("mimetype", "application/epub+zip".toByteArray(), true)
            put("META-INF/container.xml", """<?xml version="1.0"?><container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0"><rootfiles><rootfile full-path="OEBPS/package.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""".toByteArray())
            put("OEBPS/package.opf", opf.toByteArray())
            put("OEBPS/content.xhtml", content.toByteArray())
            put("OEBPS/nav.xhtml", nav.toByteArray())
            images.values.forEach { put("OEBPS/${it.first}", it.third) }
        }
        return title
    }
}
