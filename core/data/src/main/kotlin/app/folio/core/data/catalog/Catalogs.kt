package app.folio.core.data.catalog

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import java.net.URL

/**
 * One OPDS catalog the user keeps. [address] is the full feed URL. The optional fields override
 * what the feed declares: [downloads] is another origin to fetch books from (mirrors), the search
 * templates and [newBooks] cover servers whose declared links are menus (Flibusta). [format] is what the
 * book sheet's «Скачать» takes first. [favorite] puts it at the top of «Каталоги»; rows saved before
 * favorites existed decode as favorites.
 */
data class Catalog(
    val id: String,
    val title: String,
    val address: String,
    val downloads: String? = null,
    val searchBooks: String? = null,
    val searchAuthors: String? = null,
    val newBooks: String? = null,
    val format: String = DEFAULT_FORMAT,
    val favorite: Boolean = true,
) {
    val host: String get() = runCatching { URL(address).host }.getOrDefault(address)

    companion object {
        /** Formats a catalog can download first; the rest are conversions. */
        val FORMATS = listOf("EPUB", "FB2")
        const val DEFAULT_FORMAT = "EPUB"
    }
}

/**
 * A catalog with a line about it: the built-in one, or a row of a list being imported. [note] warns about a known
 * limit (sign-in, fragments only).
 */
data class CatalogPreset(val catalog: Catalog, val description: String, val note: String? = null)

/** The one catalog Folio ships with; everything else the user adds by address or imports from a list. */
object CatalogPresets {
    fun flibustaStyle(id: String, title: String, origin: String, downloads: String? = null, format: String = "FB2") = Catalog(
        id = id,
        title = title,
        address = "$origin/opds",
        downloads = downloads?.takeIf { it != origin },
        searchBooks = "$origin/opds/search?searchType=books&searchTerm={searchTerms}",
        searchAuthors = "$origin/opds/search?searchType=authors&searchTerm={searchTerms}",
        newBooks = "$origin/opds/new/0/new",
        format = format,
    )

    val all: List<CatalogPreset> = listOf(
        CatalogPreset(
            Catalog("iknigi", "iKnigi", "https://iknigi.net/opds"),
            "Книги ЛитРес",
            "Только ознакомительные фрагменты; через VPN может не открываться.",
        ),
    )

    /** First launch: the built-in catalog. */
    val starter: List<Catalog> get() = all.map { it.catalog }

    fun preset(id: String): CatalogPreset? = all.firstOrNull { it.catalog.id == id }
}

/**
 * The saved rows (favorites, the user's own catalogs, edited presets) merged with the presets. A preset
 * row is saved only while it is a favorite or differs from the preset; otherwise the preset itself lists it.
 */
object CatalogDirectory {
    /** Saved rows first, then presets that no saved row covers (same id or same address). */
    fun all(saved: List<Catalog>): List<Catalog> {
        val ids = saved.map { it.id }.toSet()
        val addresses = saved.map { it.address }.toSet()
        return saved + CatalogPresets.all.map { it.catalog.copy(favorite = false) }.filter { it.id !in ids && it.address !in addresses }
    }

    fun isPreset(catalog: Catalog): Boolean = CatalogPresets.preset(catalog.id) != null

    /** True when [catalog] is a preset whose fields the user changed. */
    fun isEdited(catalog: Catalog): Boolean =
        CatalogPresets.preset(catalog.id)?.let { it.catalog != catalog.copy(favorite = true) } ?: false

    /** Stars or unstars [catalog]; an unstarred unedited preset drops out of the saved rows. */
    fun setFavorite(saved: List<Catalog>, catalog: Catalog, favorite: Boolean): List<Catalog> {
        val index = saved.indexOfFirst { it.id == catalog.id }
        val row = (saved.getOrNull(index) ?: catalog).copy(favorite = favorite)
        return when {
            !favorite && isPreset(row) && !isEdited(row) -> saved.filterNot { it.id == row.id }
            index >= 0 -> saved.toMutableList().also { it[index] = row }
            else -> saved + row
        }
    }

    /**
     * Adds imported catalogs: one with the same id or feed address as a saved row updates that row (its id and
     * star stay), the rest are appended as favorites.
     */
    fun merge(saved: List<Catalog>, incoming: List<Catalog>): List<Catalog> {
        val result = saved.toMutableList()
        for (c in incoming) {
            val index = result.indexOfFirst { it.id == c.id || it.address == c.address }
            if (index >= 0) result[index] = c.copy(id = result[index].id, favorite = result[index].favorite)
            else result += c.copy(favorite = true)
        }
        return result
    }

    /** Puts a preset back to its own fields, keeping the star. */
    fun reset(saved: List<Catalog>, id: String): List<Catalog> {
        val preset = CatalogPresets.preset(id)?.catalog ?: return saved
        val row = saved.firstOrNull { it.id == id } ?: return saved
        return setFavorite(saved.map { if (it.id == id) preset else it }, preset, row.favorite)
    }
}

/** JSON in the `catalog` prefs; unknown or broken rows are skipped. */
internal object CatalogCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(list: List<Catalog>): String = buildJsonArray {
        list.forEach { c ->
            add(buildJsonObject {
                put("id", c.id)
                put("title", c.title)
                put("address", c.address)
                c.downloads?.let { put("downloads", it) }
                c.searchBooks?.let { put("searchBooks", it) }
                c.searchAuthors?.let { put("searchAuthors", it) }
                c.newBooks?.let { put("newBooks", it) }
                if (c.format != Catalog.DEFAULT_FORMAT) put("format", c.format)
                if (!c.favorite) put("favorite", false)
            })
        }
    }.toString()

    fun decode(value: String): List<Catalog> {
        val array = runCatching { json.parseToJsonElement(value) as? JsonArray }.getOrNull() ?: return emptyList()
        return array.mapNotNull { item ->
            val o = item as? JsonObject ?: return@mapNotNull null
            fun s(key: String) = (o[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
            Catalog(
                id = s("id") ?: return@mapNotNull null,
                title = s("title") ?: return@mapNotNull null,
                address = s("address") ?: return@mapNotNull null,
                downloads = s("downloads"),
                searchBooks = s("searchBooks"),
                searchAuthors = s("searchAuthors"),
                newBooks = s("newBooks"),
                format = s("format")?.takeIf { it in Catalog.FORMATS } ?: Catalog.DEFAULT_FORMAT,
                favorite = (o["favorite"] as? JsonPrimitive)?.contentOrNull != "false",
            )
        }
    }
}

/**
 * A catalog list file, for import and export: `{"version":1,"catalogs":[{id,title,address,downloads?,searchBooks?,
 * searchAuthors?,newBooks?,format?,description?,note?}]}` (the format of `catalogs.json` in folio-modules). Rows
 * without an HTTPS address are dropped; a newer major [VERSION], junk or an empty list decodes as null.
 */
object CatalogListCodec {
    private val json = Json { ignoreUnknownKeys = true }
    const val VERSION = 1

    fun decode(value: String): List<CatalogPreset>? {
        val root = runCatching { json.parseToJsonElement(value) as? JsonObject }.getOrNull() ?: return null
        if ((root["version"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() != VERSION) return null
        val rows = root["catalogs"] as? JsonArray ?: return null
        val presets = rows.mapNotNull { item ->
            val o = item as? JsonObject ?: return@mapNotNull null
            fun s(key: String) = (o[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
            fun https(key: String) = s(key)?.takeIf { it.startsWith("https://") }
            val catalog = Catalog(
                id = s("id") ?: return@mapNotNull null,
                title = s("title") ?: return@mapNotNull null,
                address = https("address") ?: return@mapNotNull null,
                downloads = https("downloads"),
                searchBooks = https("searchBooks"),
                searchAuthors = https("searchAuthors"),
                newBooks = https("newBooks"),
                format = s("format")?.takeIf { it in Catalog.FORMATS } ?: Catalog.DEFAULT_FORMAT,
            )
            CatalogPreset(catalog, s("description").orEmpty(), s("note"))
        }
        return presets.distinctBy { it.catalog.id }.takeIf { it.isNotEmpty() }
    }

    /** The file «Экспорт» writes; sign-ins never go into it. */
    fun encode(list: List<Catalog>): String = buildJsonObject {
        put("version", VERSION)
        put("catalogs", buildJsonArray {
            list.forEach { c ->
                add(buildJsonObject {
                    put("id", c.id)
                    put("title", c.title)
                    put("address", c.address)
                    c.downloads?.let { put("downloads", it) }
                    c.searchBooks?.let { put("searchBooks", it) }
                    c.searchAuthors?.let { put("searchAuthors", it) }
                    c.newBooks?.let { put("newBooks", it) }
                    if (c.format != Catalog.DEFAULT_FORMAT) put("format", c.format)
                })
            }
        })
    }.toString()
}
