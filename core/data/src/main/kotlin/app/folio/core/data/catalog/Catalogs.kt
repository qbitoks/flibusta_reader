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
 * templates and [newBooks] cover servers whose declared links are menus (Flibusta).
 */
data class Catalog(
    val id: String,
    val title: String,
    val address: String,
    val downloads: String? = null,
    val searchBooks: String? = null,
    val searchAuthors: String? = null,
    val newBooks: String? = null,
) {
    val host: String get() = runCatching { URL(address).host }.getOrDefault(address)
}

/** A catalog offered in «Добавить каталог»; [note] warns about a known limit (sign-in, fragments only). */
data class CatalogPreset(val catalog: Catalog, val description: String, val note: String? = null)

object CatalogPresets {
    fun flibustaStyle(id: String, title: String, origin: String, downloads: String? = null) = Catalog(
        id = id,
        title = title,
        address = "$origin/opds",
        downloads = downloads?.takeIf { it != origin },
        searchBooks = "$origin/opds/search?searchType=books&searchTerm={searchTerms}",
        searchAuthors = "$origin/opds/search?searchType=authors&searchTerm={searchTerms}",
        newBooks = "$origin/opds/new/0/new",
    )

    val all: List<CatalogPreset> = listOf(
        CatalogPreset(flibustaStyle("flibusta", "Флибуста", "https://flibusta.is"), "Большая библиотека на русском: FB2, EPUB"),
        CatalogPreset(Catalog("librusec", "Либрусек", "https://lib.rus.ec/opds"), "Художественная, фантастика, техническая литература", "Скачивание только после входа на сайте."),
        CatalogPreset(Catalog("iknigi", "iKnigi", "https://iknigi.net/opds"), "Художественная и современная литература", "Отдаёт только ознакомительные фрагменты с ЛитРес."),
        CatalogPreset(Catalog("ekniga", "EKNIGA", "https://ekniga.org/opds"), "Книги разных жанров"),
        CatalogPreset(Catalog("imwerden", "ImWerden", "https://imwerden.de/opds/"), "Русская классика, редкие и архивные издания"),
        CatalogPreset(Catalog("anarchist", "Библиотека анархизма", "https://ru.anarchistlibraries.net/opds"), "Философия, история, общественная мысль"),
        CatalogPreset(Catalog("gutenberg", "Project Gutenberg", "https://www.gutenberg.org/ebooks.opds/"), "Более 79 000 книг, в основном классика на английском"),
        CatalogPreset(Catalog("openlibrary", "Open Library", "https://openlibrary.org/opds/"), "Классика и архив; многие книги только на время, на сайте"),
        CatalogPreset(Catalog("wolnelektury", "Wolne Lektury", "https://wolnelektury.pl/opds/"), "Польская литература"),
        CatalogPreset(Catalog("fantasyworlds", "Fantasy Worlds", "https://fantasy-worlds.org/opds"), "Фантастика и фэнтези"),
        CatalogPreset(Catalog("profilib", "PROFILIB", "https://profilib.com/opds/"), "Художественная литература"),
        CatalogPreset(Catalog("knihi", "Knihi.com", "https://knihi.com/opds.xml"), "Белорусская литература"),
        CatalogPreset(Catalog("ebooksgratuits", "Ebooks libres et gratuits", "https://www.ebooksgratuits.com/opds/"), "Французская классика"),
        CatalogPreset(Catalog("openedition", "OpenEdition Books", "https://opds.openedition.org/"), "Научные издания на разных языках"),
        CatalogPreset(Catalog("textos", "Textos.info", "https://www.textos.info/catalogo.atom"), "Испанская литература"),
    )

    /** First launch: Flibusta (the former built-in source) and Project Gutenberg. */
    val starter: List<Catalog> get() = listOf("flibusta", "gutenberg").mapNotNull { id -> all.firstOrNull { it.catalog.id == id }?.catalog }

    /** OPDS Home, a directory of OPDS catalogs; opened in the browser. */
    const val DIRECTORY = "https://opdshome.uo1.net/"
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
            )
        }
    }
}
