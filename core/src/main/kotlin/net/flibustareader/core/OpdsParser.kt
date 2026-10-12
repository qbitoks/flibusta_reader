package net.flibustareader.core

import java.net.URI

data class CatalogEntry(val id: String, val title: String, val author: String, val summary: String, val downloads: Map<String, String>, val navigation: String?)
data class CatalogPage(val title: String, val entries: List<CatalogEntry>, val next: String?)
object OpdsParser {
    fun parse(xml: String, base: String): CatalogPage {
        val root = Xml.parse(xml.toByteArray()).documentElement
        require(root.localName == "feed") { "Источник вернул не OPDS каталог" }
        fun resolve(href: String): String? {
            if (href.isBlank()) return null
            val address = if (href.startsWith("?")) base.substringBefore('?') + href else URI(base).resolve(href).toString()
            return address.takeIf { runCatching { URI(it).scheme == "https" }.getOrDefault(false) }
        }
        val entries = Xml.children(root, "entry").map { entry ->
            val links = Xml.children(entry, "link")
            val downloads = linkedMapOf<String, String>()
            links.filter { it.getAttribute("rel").startsWith("http://opds-spec.org/acquisition") }.forEach {
                val format = when {
                    it.getAttribute("type").contains("epub") -> "EPUB"
                    it.getAttribute("type").contains("fb2") -> "FB2"
                    else -> null
                }
                val address = resolve(it.getAttribute("href"))
                if (format != null && address != null) downloads[format] = address
            }
            val navigation = links.firstOrNull { it.getAttribute("type").contains("atom+xml") && it.getAttribute("rel") !in listOf("related", "alternate") }
                ?.getAttribute("href")?.let(::resolve)
            fun text(name: String) = Xml.children(entry, name).firstOrNull()?.textContent.orEmpty().trim()
            CatalogEntry(
                id = text("id").ifBlank { downloads.values.firstOrNull() ?: navigation ?: text("title") },
                title = text("title"),
                author = Xml.children(entry, "author").joinToString(", ") { Xml.children(it, "name").firstOrNull()?.textContent.orEmpty() },
                summary = text("content").ifBlank { text("summary") }.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim(),
                downloads = downloads,
                navigation = navigation.takeIf { downloads.isEmpty() }
            )
        }
        val next = Xml.children(root, "link").firstOrNull { it.getAttribute("rel") == "next" }?.getAttribute("href")?.let(::resolve)
        return CatalogPage(Xml.children(root, "title").firstOrNull()?.textContent.orEmpty(), entries, next)
    }
}
