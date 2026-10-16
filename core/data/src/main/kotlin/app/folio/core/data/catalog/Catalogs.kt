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
 * templates and [newBooks] cover servers whose declared links are menus (Flibusta). [favorite] puts it
 * at the top of «Каталоги»; rows saved before favorites existed decode as favorites.
 */
data class Catalog(
    val id: String,
    val title: String,
    val address: String,
    val downloads: String? = null,
    val searchBooks: String? = null,
    val searchAuthors: String? = null,
    val newBooks: String? = null,
    val favorite: Boolean = true,
) {
    val host: String get() = runCatching { URL(address).host }.getOrDefault(address)
}

/**
 * A known catalog listed in «Каталоги» without being saved; [note] warns about a known limit (sign-in,
 * fragments only), [russian] picks its group.
 */
data class CatalogPreset(val catalog: Catalog, val description: String, val note: String? = null, val russian: Boolean = true)

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
        CatalogPreset(Catalog("gutenberg", "Project Gutenberg", "https://www.gutenberg.org/ebooks.opds/"), "Более 79 000 книг, в основном классика на английском", russian = false),
        CatalogPreset(Catalog("openlibrary", "Open Library", "https://openlibrary.org/opds/"), "Классика и архив; многие книги только на время, на сайте", russian = false),
        CatalogPreset(Catalog("wolnelektury", "Wolne Lektury", "https://wolnelektury.pl/opds/"), "Польская литература", russian = false),
        CatalogPreset(Catalog("fantasyworlds", "Fantasy Worlds", "https://fantasy-worlds.org/opds"), "Фантастика и фэнтези"),
        CatalogPreset(Catalog("profilib", "PROFILIB", "https://profilib.com/opds/"), "Художественная литература"),
        CatalogPreset(Catalog("knihi", "Knihi.com", "https://knihi.com/opds.xml"), "Белорусская литература", russian = false),
        CatalogPreset(Catalog("ebooksgratuits", "Ebooks libres et gratuits", "https://www.ebooksgratuits.com/opds/"), "Французская классика", russian = false),
        CatalogPreset(Catalog("openedition", "OpenEdition Books", "https://opds.openedition.org/"), "Научные издания на разных языках", russian = false),
        CatalogPreset(Catalog("textos", "Textos.info", "https://www.textos.info/catalogo.atom"), "Испанская литература", russian = false),
    )

    /** First launch: Flibusta (the former built-in source) and Project Gutenberg. */
    val starter: List<Catalog> get() = listOf("flibusta", "gutenberg").mapNotNull { id -> all.firstOrNull { it.catalog.id == id }?.catalog }

    fun preset(id: String): CatalogPreset? = all.firstOrNull { it.catalog.id == id }

    /** OPDS Home, a directory of OPDS catalogs; opened in the browser. */
    const val DIRECTORY = "https://opdshome.uo1.net/"
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
                favorite = (o["favorite"] as? JsonPrimitive)?.contentOrNull != "false",
            )
        }
    }
}
