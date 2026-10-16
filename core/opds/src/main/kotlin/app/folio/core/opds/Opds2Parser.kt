package app.folio.core.opds

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * OPDS 2 (JSON, Readium Web Publication Manifest): navigation, publications, groups and facets map
 * onto the same [CatalogPage] as Atom. Groups become folders when they link to a feed of their own.
 */
internal object Opds2Parser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(text: String, base: String): CatalogPage {
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
        require(root != null && (root["metadata"] != null || root["links"] != null)) { "Источник вернул не OPDS каталог" }
        val links = root.array("links")
        fun rel(value: String) = links.firstOrNull { value in rels(it) }?.href(base)
        val entries = mutableListOf<CatalogEntry>()
        root.array("navigation").mapNotNullTo(entries) { navigation(it, base) }
        root.array("publications").mapNotNullTo(entries) { publication(it, base) }
        root.array("groups").forEach { group ->
            val title = group.obj("metadata")?.string("title").orEmpty()
            val self = group.array("links").firstOrNull { "self" in rels(it) }?.href(base)
            group.array("navigation").mapNotNullTo(entries) { navigation(it, base) }
            val books = group.array("publications")
            if (books.isNotEmpty()) {
                if (self != null && title.isNotBlank()) {
                    entries += CatalogEntry(id = self, title = title, author = "", summary = "", downloads = emptyMap(), navigation = self)
                } else {
                    books.mapNotNullTo(entries) { publication(it, base) }
                }
            }
        }
        val searchLink = links.firstOrNull { "search" in rels(it) }
        val search = searchLink?.href(base)?.let { href ->
            when {
                SearchTemplate.isTemplate(href) -> SearchLink(href, isDescription = false)
                searchLink.string("type").orEmpty().contains("opensearch") -> SearchLink(href, isDescription = true)
                else -> null
            }
        }
        val facets = root.array("facets").mapNotNull { group ->
            val list = group.array("links").mapNotNull { link ->
                val href = link.href(base) ?: return@mapNotNull null
                val title = link.string("title")?.trim().orEmpty().ifEmpty { return@mapNotNull null }
                Facet(title, href, (link.obj("properties")?.get("active") as? JsonPrimitive)?.booleanOrNull == true)
            }
            list.takeIf { it.isNotEmpty() }?.let { FacetGroup(group.obj("metadata")?.string("title").orEmpty(), it) }
        }
        return CatalogPage(
            title = root.obj("metadata")?.string("title").orEmpty(),
            entries = entries.distinctBy { it.id },
            next = rel("next"),
            search = search,
            newBooks = rel("http://opds-spec.org/sort/new"),
            popular = rel("http://opds-spec.org/sort/popular"),
            facets = facets,
        )
    }

    private fun navigation(link: JsonObject, base: String): CatalogEntry? {
        val href = link.href(base) ?: return null
        val title = link.string("title")?.trim().orEmpty().ifEmpty { return null }
        return CatalogEntry(id = href, title = title, author = "", summary = "", downloads = emptyMap(), navigation = href)
    }

    private fun publication(item: JsonObject, base: String): CatalogEntry? {
        val meta = item.obj("metadata") ?: return null
        val title = meta.string("title")?.trim().orEmpty().ifEmpty { return null }
        val links = item.array("links")
        val downloads = linkedMapOf<String, String>()
        val other = linkedSetOf<String>()
        links.filter { link -> rels(link).let { r -> r.any { it.startsWith("http://opds-spec.org/acquisition") } && r.none { it.endsWith("/borrow") || it.endsWith("/buy") } } }
            .forEach { link ->
                val type = link.string("type").orEmpty()
                val href = link.href(base)
                val format = OpdsParser.formatOf(type)
                when {
                    format != null && href != null -> downloads.putIfAbsent(format, href)
                    format == null && !type.contains("json") -> OpdsParser.formatName(type)?.let(other::add)
                }
            }
        val web = links.firstOrNull { "alternate" in rels(it) && it.string("type").orEmpty().startsWith("text/html") }?.href(base)
        val authors = when (val a = meta["author"]) {
            is JsonArray -> a.mapNotNull(::name)
            null -> emptyList()
            else -> listOfNotNull(name(a))
        }
        val language = when (val l = meta["language"]) {
            is JsonArray -> (l.firstOrNull() as? JsonPrimitive)?.contentOrNull
            is JsonPrimitive -> l.contentOrNull
            else -> null
        }
        return CatalogEntry(
            id = meta.string("identifier") ?: links.firstOrNull { "self" in rels(it) }?.href(base) ?: downloads.values.firstOrNull() ?: web ?: title,
            title = title,
            author = authors.joinToString(", "),
            summary = Description.parse(meta.string("description").orEmpty()).text,
            downloads = OpdsParser.sortFormats(downloads),
            navigation = null,
            cover = item.array("images").firstOrNull()?.href(base),
            genres = meta.array("subject").mapNotNull { it.string("name") }.distinct(),
            language = language,
            year = meta.string("published")?.take(4),
            otherFormats = other.toList(),
            web = web.takeIf { downloads.isEmpty() },
        )
    }

    private fun name(value: JsonElement): String? = when (value) {
        is JsonPrimitive -> value.contentOrNull
        is JsonObject -> value.string("name")
        else -> null
    }?.trim()?.takeIf { it.isNotEmpty() }

    private fun rels(link: JsonObject): List<String> = when (val r = link["rel"]) {
        is JsonArray -> r.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        is JsonPrimitive -> listOfNotNull(r.contentOrNull)
        else -> emptyList()
    }

    private fun JsonObject.href(base: String): String? = string("href")?.let { Links.resolve(base, it) }
    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
    private fun JsonObject.array(key: String): List<JsonObject> = (this[key] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
}
