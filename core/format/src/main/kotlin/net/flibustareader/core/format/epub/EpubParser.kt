package net.flibustareader.core.format.epub

import net.flibustareader.core.format.ResourceSink
import net.flibustareader.core.format.UnsupportedBookException
import net.flibustareader.core.format.resolveInternalTargets
import net.flibustareader.core.model.Book
import net.flibustareader.core.model.BookMetadata
import net.flibustareader.core.model.Chapter
import net.flibustareader.core.model.Position
import net.flibustareader.core.model.TocEntry
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.File
import java.net.URLDecoder
import java.util.zip.ZipFile

internal class EpubParser(
    private val file: File,
    private val resources: ResourceSink,
) {
    private class ManifestItem(val id: String, val path: String, val mediaType: String, val properties: String)

    private lateinit var zip: ZipFile
    private val cssCache = HashMap<String, CssRules>()

    fun parse(): Book = ZipFile(file).use { zip ->
        this.zip = zip
        val opfPath = xml("META-INF/container.xml")
            ?.selectFirst("rootfile")?.attr("full-path")
            ?.takeIf { it.isNotEmpty() }
            ?: throw UnsupportedBookException("EPUB without rootfile")
        val opf = xml(opfPath) ?: throw UnsupportedBookException("Missing package document $opfPath")
        val opfDir = opfPath.substringBeforeLast('/', "")

        val manifest = opf.select("manifest > item").associate { item ->
            val id = item.attr("id")
            id to ManifestItem(id, resolvePath(opfDir, item.attr("href")), item.attr("media-type"), item.attr("properties"))
        }
        val spine = opf.select("spine > itemref")
            .filter { it.attr("linear") != "no" }
            .mapNotNull { manifest[it.attr("idref")] }
            .filter { it.mediaType.contains("html") || it.path.endsWith("htm") || it.path.endsWith("html") }

        val chapterIndex = HashMap<String, Int>()
        val chapters = ArrayList<Chapter>(spine.size)
        val headings = ArrayList<String?>(spine.size)
        val footnotes = HashMap<String, List<net.flibustareader.core.model.Block>>()
        for (item in spine) {
            val doc = xml(item.path) ?: continue
            val body = doc.selectFirst("body") ?: continue
            val dir = item.path.substringBeforeLast('/', "")
            val converted = HtmlConverter(stylesFor(doc, dir)) { href -> resolveHref(item.path, dir, href) }.convert(body)
            chapterIndex[item.path] = chapters.size
            converted.footnotes.forEach { (id, blocks) -> footnotes["${item.path}#$id"] = blocks }
            chapters += Chapter(null, converted.blocks, converted.anchors)
            headings += converted.firstHeading
        }
        if (chapters.isEmpty()) throw UnsupportedBookException("EPUB has no readable chapters")

        fun locate(target: String): Position? {
            val path = target.substringBefore('#')
            val chapter = chapterIndex[path] ?: return null
            val fragment = target.substringAfter('#', "")
            val block = if (fragment.isEmpty()) 0 else chapters[chapter].anchors[fragment] ?: 0
            return Position(chapter, block)
        }

        var toc = readNav(manifest.values, ::locate)
        if (toc.isEmpty()) toc = readNcx(opf, manifest, ::locate)
        if (toc.isEmpty()) {
            toc = headings.mapIndexedNotNull { i, h -> h?.let { TocEntry(it, Position(i)) } }
        }

        val titledChapters = chapters.mapIndexed { i, c ->
            val title = toc.flattenFirstFor(i) ?: headings[i]
            Chapter(title, c.blocks, c.anchors)
        }

        val cover = findCover(opf, manifest)
        manifest.values.filter { it.mediaType.startsWith("image/") }.forEach { item ->
            zip.getEntry(item.path)?.let { entry -> resources.put(item.path, zip.getInputStream(entry).use { it.readBytes() }) }
        }

        val book = Book(readMetadata(opf, cover), titledChapters, toc, footnotes)
        resolveInternalTargets(book, ::locate)
    }

    private fun readMetadata(opf: Document, cover: String?): BookMetadata {
        val meta = opf.selectFirst("metadata") ?: opf
        fun dc(name: String) = meta.getElementsByTag("dc:$name").ifEmpty { meta.getElementsByTag(name) }
        val series = meta.select("meta[name=calibre:series]").attr("content").ifEmpty {
            meta.select("meta[property=belongs-to-collection]").text()
        }
        val seriesIndex = meta.select("meta[name=calibre:series_index]").attr("content").ifEmpty {
            meta.select("meta[property=group-position]").text()
        }
        return BookMetadata(
            title = dc("title").firstOrNull()?.text()?.trim().orEmpty().ifEmpty { file.nameWithoutExtension },
            authors = dc("creator").map { it.text().trim() }.filter { it.isNotEmpty() }.distinct(),
            language = dc("language").firstOrNull()?.text()?.trim()?.takeIf { it.isNotEmpty() },
            series = series.trim().takeIf { it.isNotEmpty() },
            seriesIndex = seriesIndex.toDoubleOrNull()?.toInt(),
            description = dc("description").firstOrNull()?.let { Jsoup.parse(it.text()).text() }?.takeIf { it.isNotBlank() },
            coverResource = cover,
        )
    }

    private fun findCover(opf: Document, manifest: Map<String, ManifestItem>): String? {
        val images = manifest.values.filter { it.mediaType.startsWith("image/") }
        images.firstOrNull { it.properties.split(' ').contains("cover-image") }?.let { return it.path }
        opf.select("meta[name=cover]").attr("content").takeIf { it.isNotEmpty() }?.let { id ->
            manifest[id]?.takeIf { it.mediaType.startsWith("image/") }?.let { return it.path }
        }
        return images.firstOrNull { it.id.contains("cover", true) || it.path.substringAfterLast('/').contains("cover", true) }?.path
    }

    private fun readNav(items: Collection<ManifestItem>, locate: (String) -> Position?): List<TocEntry> {
        val navItem = items.firstOrNull { it.properties.split(' ').contains("nav") } ?: return emptyList()
        val doc = xml(navItem.path) ?: return emptyList()
        val nav = doc.select("nav").firstOrNull { it.attr("epub:type").contains("toc") } ?: doc.selectFirst("nav") ?: return emptyList()
        val dir = navItem.path.substringBeforeLast('/', "")
        fun list(ol: Element?): List<TocEntry> = ol?.children()?.filter { it.normalName() == "li" }?.mapNotNull { li ->
            val link = li.children().firstOrNull { it.normalName() == "a" || it.normalName() == "span" }
            val title = link?.text()?.trim().orEmpty()
            val children = list(li.children().firstOrNull { it.normalName() == "ol" })
            val href = link?.attr("href").orEmpty()
            val position = href.takeIf { it.isNotEmpty() }?.let { locate(resolveHref(navItem.path, dir, it)) }
                ?: children.firstOrNull()?.position
            if (title.isEmpty() || position == null) null else TocEntry(title, position, children)
        }.orEmpty()
        return list(nav.children().firstOrNull { it.normalName() == "ol" })
    }

    private fun readNcx(opf: Document, manifest: Map<String, ManifestItem>, locate: (String) -> Position?): List<TocEntry> {
        val ncxItem = manifest[opf.selectFirst("spine")?.attr("toc").orEmpty()]
            ?: manifest.values.firstOrNull { it.mediaType == "application/x-dtbncx+xml" }
            ?: return emptyList()
        val doc = xml(ncxItem.path) ?: return emptyList()
        val dir = ncxItem.path.substringBeforeLast('/', "")
        fun points(parent: Element): List<TocEntry> = parent.children().filter { it.normalName() == "navpoint" }.mapNotNull { np ->
            val title = np.selectFirst("navLabel > text, navlabel > text")?.text()?.trim().orEmpty()
            val src = np.children().firstOrNull { it.normalName() == "content" }?.attr("src").orEmpty()
            val children = points(np)
            val position = src.takeIf { it.isNotEmpty() }?.let { locate(resolveHref(ncxItem.path, dir, it)) } ?: children.firstOrNull()?.position
            if (title.isEmpty() || position == null) null else TocEntry(title, position, children)
        }
        val navMap = doc.selectFirst("navMap, navmap") ?: return emptyList()
        return points(navMap)
    }

    private fun stylesFor(doc: Document, dir: String): CssRules {
        var rules = CssRules.Empty
        for (link in doc.select("link[rel~=(?i)stylesheet]")) {
            val path = resolvePath(dir, link.attr("href"))
            rules += cssCache.getOrPut(path) {
                zip.getEntry(path)?.let { e -> CssRules.parse(zip.getInputStream(e).use { String(it.readBytes(), Charsets.UTF_8) }) } ?: CssRules.Empty
            }
        }
        for (style in doc.select("style")) rules += CssRules.parse(style.data())
        return rules
    }

    private fun xml(path: String): Document? {
        val entry = zip.getEntry(path) ?: zip.getEntry(URLDecoder.decode(path, "UTF-8")) ?: return null
        return zip.getInputStream(entry).use { Jsoup.parse(it, null, "", Parser.xmlParser()) }
    }

    private fun resolveHref(currentPath: String, dir: String, href: String): String {
        if (href.startsWith("#")) return currentPath + href
        val fragment = href.substringAfter('#', "")
        val path = resolvePath(dir, href.substringBefore('#'))
        return if (fragment.isEmpty()) path else "$path#$fragment"
    }

    private fun List<TocEntry>.flattenFirstFor(chapter: Int): String? {
        for (e in this) {
            if (e.position.chapter == chapter && e.position.block == 0) return e.title
            e.children.flattenFirstFor(chapter)?.let { return it }
        }
        return null
    }

    companion object {
        fun resolvePath(dir: String, href: String): String {
            val decoded = try { URLDecoder.decode(href.replace("+", "%2B"), "UTF-8") } catch (e: IllegalArgumentException) { href }
            val parts = ArrayList<String>()
            if (!decoded.startsWith("/") && dir.isNotEmpty()) parts += dir.split('/')
            for (seg in decoded.split('/')) when (seg) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                else -> parts += seg
            }
            return parts.joinToString("/")
        }
    }
}
