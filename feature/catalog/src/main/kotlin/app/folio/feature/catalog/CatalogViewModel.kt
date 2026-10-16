package app.folio.feature.catalog

import android.util.Log
import app.folio.core.data.catalog.CatalogPreset
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import app.folio.core.data.catalog.Catalog
import app.folio.core.data.catalog.CatalogRepository
import app.folio.core.data.catalog.DownloadItem
import app.folio.core.data.catalog.DownloadManager
import app.folio.core.data.catalog.NetworkErrors
import app.folio.core.data.db.DownloadState
import app.folio.core.opds.CatalogEntry
import app.folio.core.opds.CatalogLink
import app.folio.core.opds.CatalogPage
import app.folio.core.opds.Facet
import app.folio.core.opds.SearchTemplate
import java.io.File
import javax.inject.Inject

/** One feed on screen: a home section, an opened folder or a search scope. */
@Stable
class Feed(val address: String, title: String) {
    var title by mutableStateOf(title)
    var page by mutableStateOf<CatalogPage?>(null)
    var loading by mutableStateOf(false)
    var loadingMore by mutableStateOf(false)
    /** [NetworkErrors] code of the last failed load. */
    var error by mutableStateOf<String?>(null)
    val list = LazyListState()
    internal var job: Job? = null

    val entries: List<CatalogEntry> get() = page?.entries.orEmpty()
    val canLoadMore: Boolean get() = page?.next != null && !loading && !loadingMore
}

enum class SearchScope { All, Books, Authors }

/** [authors] only when the catalog searches authors separately (Flibusta). */
@Stable
class SearchResults(val query: String, val books: Feed, val authors: Feed?) {
    var scope by mutableStateOf(SearchScope.All)
}

/**
 * Catalog state for the current [Catalog]: the home (root sections and the new/popular shelves the
 * feed declares), a stack of opened folders, search when the catalog has one, and the download list.
 * Open folders and the last search survive process death through [SavedStateHandle]; their pages are
 * re-fetched (the repository caches them). Switching catalogs resets all of it.
 */
@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val repository: CatalogRepository,
    private val downloadManager: DownloadManager,
    private val saved: SavedStateHandle,
) : ViewModel() {
    val catalogs: StateFlow<List<Catalog>> = repository.catalogs
    val presets: StateFlow<List<CatalogPreset>> = repository.presets
    var catalog by mutableStateOf(repository.current.value)
        private set
    var root by mutableStateOf(Feed(catalog?.address.orEmpty(), ""))
        private set
    /** Shelves shown only when the catalog has them: a preset address, or a link the root feed declares. */
    var newBooks by mutableStateOf<Feed?>(null)
        private set
    var popular by mutableStateOf<Feed?>(null)
        private set
    /** Search templates; null until the root feed is read, or when the catalog has no OPDS search. */
    var bookSearch by mutableStateOf(catalog?.searchBooks)
        private set
    var authorSearch by mutableStateOf(catalog?.searchAuthors)
        private set
    private var capabilitiesJob: Job? = null
    val stack = mutableStateListOf<Feed>()
    var results by mutableStateOf<SearchResults?>(null)
        private set
    var query by mutableStateOf(saved.get<String>(KEY_QUERY).orEmpty())
        private set
    var selected by mutableStateOf<CatalogEntry?>(null)
    /** The current catalog's sign-in sheet is open (after a «нужен вход» answer). */
    var signInRequested by mutableStateOf(false)
    val logins: StateFlow<Map<String, String>> = repository.logins
    /** One-off error of a download request, shown in the book sheet. */
    var downloadError by mutableStateOf<String?>(null)

    val recent: StateFlow<List<String>> = repository.recent
    val downloadIssues: StateFlow<Map<String, String>> = repository.downloadIssues
    /** Entry id → the book and the formats still to try if the current one is refused. */
    private val fallbacks = mutableMapOf<String, Pair<CatalogEntry, List<String>>>()
    val downloads: StateFlow<List<DownloadItem>> =
        downloadManager.downloads.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val canGoBack: Boolean get() = stack.isNotEmpty() || results != null

    init {
        viewModelScope.launch {
            try {
                repository.refreshPresets()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("CatalogViewModel", "Catalog list not refreshed", e)
            }
        }
        loadHome()
        viewModelScope.launch { downloads.collect(::fallBack) }
        viewModelScope.launch {
            repository.current.collect { next ->
                val previous = catalog
                if (next != previous) {
                    catalog = next
                    if (next?.address != previous?.address || next?.searchBooks != previous?.searchBooks || next?.newBooks != previous?.newBooks) reset()
                }
            }
        }
        saved.get<String>(KEY_SEARCH)?.let { submitted ->
            viewModelScope.launch {
                // A declared search is known only once the root feed is read.
                snapshotFlow { bookSearch }.filterNotNull().first()
                search(submitted, remember = false, keepStack = true)
                saved.get<String>(KEY_SCOPE)?.let { scope -> results?.scope = SearchScope.valueOf(scope) }
            }
        }
        val addresses = saved.get<ArrayList<String>>(KEY_STACK).orEmpty()
        val titles = saved.get<ArrayList<String>>(KEY_TITLES).orEmpty()
        addresses.forEachIndexed { i, address -> push(address, titles.getOrElse(i) { "" }) }
    }

    fun loadHome(fresh: Boolean = false) {
        val current = catalog ?: return
        load(root, fresh = fresh)
        current.newBooks?.let { address -> if (newBooks?.address != address) newBooks = Feed(address, "") }
        newBooks?.let { load(it, fresh = fresh, fill = SHELF_BOOKS) }
        popular?.let { load(it, fresh = fresh, fill = SHELF_BOOKS) }
        capabilitiesJob?.cancel()
        capabilitiesJob = viewModelScope.launch {
            val page = snapshotFlow { root.page }.filterNotNull().first()
            if (newBooks == null) page.newBooks?.let { newBooks = Feed(it, "").also { feed -> load(feed, fill = SHELF_BOOKS) } }
            if (popular == null) page.popular?.takeIf { it != newBooks?.address }?.let { popular = Feed(it, "").also { feed -> load(feed, fill = SHELF_BOOKS) } }
            if (bookSearch == null) {
                bookSearch = page.search?.let { link ->
                    try { repository.searchTemplate(link) } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
                }
            }
        }
    }

    fun select(catalog: Catalog) = repository.select(catalog.id)

    /** Returns an error message, or null when saved. */
    fun saveCatalog(catalog: Catalog): String? = try {
        repository.save(catalog)
        null
    } catch (e: IllegalArgumentException) {
        e.message
    }

    /**
     * Saves the catalog and its sign-in ([password] null keeps the saved one, a blank [login] signs out);
     * the open catalog then reloads what it couldn't read.
     */
    fun saveCatalog(catalog: Catalog, login: String, password: String?): String? {
        saveCatalog(catalog)?.let { return it }
        try {
            repository.signIn(catalog.id, login, password)
        } catch (e: IllegalArgumentException) {
            return e.message
        }
        if (catalog.id == this.catalog?.id) {
            loadHome(fresh = true)
            stack.filter { it.error != null }.forEach(::retry)
        }
        return null
    }

    fun removeCatalog(catalog: Catalog) = repository.remove(catalog.id)

    fun setFavorite(catalog: Catalog, favorite: Boolean) = repository.setFavorite(catalog.id, favorite)

    fun resetCatalog(catalog: Catalog) = repository.reset(catalog.id)

    fun moveCatalog(from: Int, to: Int) = repository.move(from, to)

    fun newCatalogId(): String = repository.newId()

    /** Adds the opened folder (a catalog listed by a directory such as OPDS Home) as a catalog. */
    fun addAsCatalog(feed: Feed): String? = saveCatalog(Catalog(repository.newId(), feed.title, feed.address))

    fun updateQuery(value: String) { query = value; saved[KEY_QUERY] = value }

    fun search(text: String = query, remember: Boolean = true, keepStack: Boolean = false) {
        val value = text.trim()
        if (value.isEmpty()) return
        updateQuery(value)
        if (remember) repository.rememberSearch(value)
        val template = bookSearch ?: return
        results?.let { cancel(it.books); it.authors?.let(::cancel) }
        val next = SearchResults(
            value,
            books = Feed(SearchTemplate.fill(template, value), value),
            authors = authorSearch?.let { Feed(SearchTemplate.fill(it, value), value) },
        )
        results = next
        if (!keepStack) {
            stack.forEach(::cancel)
            stack.clear()
        }
        load(next.books)
        next.authors?.let(::load)
        syncSaved()
    }

    fun setScope(scope: SearchScope) {
        results?.scope = scope
        syncSaved()
    }

    fun closeSearch() {
        results?.let { cancel(it.books); it.authors?.let(::cancel) }
        results = null
        updateQuery("")
        syncSaved()
    }

    fun forgetSearch(value: String) = repository.forgetSearch(value)

    fun clearRecent() = repository.clearRecent()

    fun open(entry: CatalogEntry) {
        val target = entry.navigation
        if (target == null) {
            downloadError = null
            selected = entry
        } else {
            push(target, entry.title)
        }
    }

    fun openLink(link: CatalogLink) {
        selected = null
        push(link.href, link.title)
    }

    fun openShelf(feed: Feed, title: String) = push(feed.address, feed.page?.title?.ifBlank { null } ?: title)

    /** A facet replaces the folder it filters; on the home it opens as a folder. */
    fun openFacet(facet: Facet) {
        val top = stack.lastOrNull()
        if (top != null) {
            stack.removeAt(stack.lastIndex)
            cancel(top)
        }
        push(facet.href, top?.title ?: facet.title)
    }

    /** Pops the top folder, or leaves search results; false when there is nothing to go back to. */
    fun back(): Boolean {
        if (stack.isNotEmpty()) {
            cancel(stack.removeAt(stack.lastIndex))
            syncSaved()
            return true
        }
        if (results != null) {
            closeSearch()
            return true
        }
        return false
    }

    fun more(feed: Feed) { if (feed.canLoadMore) load(feed, more = true) }

    fun retry(feed: Feed) = load(feed, fresh = true)

    /** The book's formats with the open catalog's own format first; the rest keep the parser's order. */
    fun formatsOf(entry: CatalogEntry): List<String> {
        val first = catalog?.format ?: Catalog.DEFAULT_FORMAT
        return entry.downloads.keys.sortedBy { if (it == first) 0 else 1 }
    }

    /**
     * Starts [format]; with [fallBack], a refusal (sign-in, another site) tries the next main format.
     * A format picked from the menu is what the user asked for, so it gets no fallback.
     */
    fun download(entry: CatalogEntry, format: String, fallBack: Boolean = true) {
        val rest = if (fallBack) formatsOf(entry).dropWhile { it != format }.drop(1).filter { it in mainFormats } else emptyList()
        if (rest.isEmpty()) fallbacks.remove(entry.id) else fallbacks[entry.id] = entry to rest
        enqueue(entry, format)
    }

    private fun fallBack(items: List<DownloadItem>) {
        val byLink = items.byLink()
        fallbacks.entries.toList().forEach { (id, pair) ->
            val (entry, rest) = pair
            val failed = entry.downloads.values.mapNotNull { byLink[it] }.filter { it.state == DownloadState.Failed && NetworkErrors.isUnavailable(it.entity.error) }
            val active = entry.downloads.values.mapNotNull { byLink[it] }.any { it.active || it.state == DownloadState.Done }
            if (active) return@forEach
            val next = rest.firstOrNull { f -> byLink[entry.downloads[f]] == null }
            if (failed.isNotEmpty() && next != null) {
                fallbacks[id] = entry to rest.drop(rest.indexOf(next) + 1)
                enqueue(entry, next)
            } else if (failed.isNotEmpty()) {
                fallbacks.remove(id)
            }
        }
    }

    private fun enqueue(entry: CatalogEntry, format: String) {
        downloadError = null
        viewModelScope.launch {
            try {
                downloadManager.enqueue(entry, format)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                downloadError = NetworkErrors.code(e)
            }
        }
    }

    fun pause(id: String) { viewModelScope.launch { downloadManager.pause(id) } }

    fun resume(id: String) { viewModelScope.launch { downloadManager.resume(id) } }

    /** Stops and forgets [id] from the book sheet, with any fallback still planned for [entry]. */
    fun cancel(entry: CatalogEntry, id: String) {
        fallbacks.remove(entry.id)
        remove(id)
    }

    fun remove(id: String) { viewModelScope.launch { downloadManager.remove(id) } }

    fun clearFinished() { viewModelScope.launch { downloadManager.clearFinished() } }

    suspend fun cover(url: String): File? = repository.cover(url)

    private fun reset() {
        stack.forEach(::cancel)
        stack.clear()
        cancel(root); newBooks?.let(::cancel); popular?.let(::cancel)
        capabilitiesJob?.cancel()
        root = Feed(catalog?.address.orEmpty(), "")
        newBooks = null
        popular = null
        bookSearch = catalog?.searchBooks
        authorSearch = catalog?.searchAuthors
        closeSearch()
        loadHome()
    }

    private fun push(address: String, title: String) {
        val feed = Feed(address, title)
        stack += feed
        load(feed)
        syncSaved()
    }

    /**
     * Loads [feed]'s first page (or the next one with [more]). With [fill], keeps fetching pages until
     * that many readable books are in: a weekly list can be mostly PDF.
     */
    private fun load(feed: Feed, more: Boolean = false, fresh: Boolean = false, fill: Int = 0) {
        val target = if (more) feed.page?.next ?: return else feed.address
        feed.job?.cancel()
        if (more) feed.loadingMore = true else feed.loading = true
        feed.error = null
        feed.job = viewModelScope.launch {
            try {
                val result = repository.page(target, fresh)
                val previous = feed.page
                feed.page = if (more && previous != null) {
                    result.copy(title = previous.title, entries = (previous.entries + result.entries).distinctBy { it.id })
                } else {
                    result
                }
                if (feed.title.isBlank()) feed.title = result.title
                feed.loading = false
                var pages = 1
                while (fill > 0 && pages < MAX_FILL_PAGES && readableEntries(feed.entries).first.count { it.isBook } < fill) {
                    val next = feed.page?.next ?: break
                    feed.loadingMore = true
                    val page = repository.page(next, fresh)
                    feed.page = page.copy(title = feed.page?.title.orEmpty(), entries = (feed.entries + page.entries).distinctBy { it.id })
                    pages++
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                feed.error = NetworkErrors.code(e)
            } finally {
                feed.loading = false
                feed.loadingMore = false
            }
        }
    }

    private fun cancel(feed: Feed) {
        feed.job?.cancel()
        feed.loading = false
        feed.loadingMore = false
    }

    private fun syncSaved() {
        saved[KEY_STACK] = ArrayList(stack.map { it.address })
        saved[KEY_TITLES] = ArrayList(stack.map { it.title })
        saved[KEY_SEARCH] = results?.query
        saved[KEY_SCOPE] = results?.scope?.name
    }

    private companion object {
        const val KEY_QUERY = "query"
        const val KEY_SEARCH = "search"
        const val KEY_SCOPE = "scope"
        const val KEY_STACK = "stack"
        const val KEY_TITLES = "titles"
        const val SHELF_BOOKS = 12
        const val MAX_FILL_PAGES = 4
    }
}

/** Downloads by acquisition link, to show each format's state next to a book. */
internal fun List<DownloadItem>.byLink(): Map<String, DownloadItem> = associateBy { it.entity.link }

internal val DownloadItem.active: Boolean get() = state == DownloadState.Queued || state == DownloadState.Running

/**
 * Servers may list authors alphabetically, so «Лем» comes after «(Лемешевский)». Rank the loaded
 * ones: a name word equal to the query first, then words starting with it, then the rest; more
 * books first inside a rank.
 */
internal fun rankAuthors(entries: List<CatalogEntry>, query: String): List<CatalogEntry> {
    val words = query.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return entries
    fun rank(entry: CatalogEntry): Int {
        val name = entry.title.lowercase().split(Regex("[\\s,.()«»\"-]+")).filter { it.isNotEmpty() }
        return when {
            words.all { w -> name.any { it == w } } -> 0
            words.all { w -> name.any { it.startsWith(w) } } -> 1
            else -> 2
        }
    }
    fun books(entry: CatalogEntry) = Regex("\\d+").find(entry.summary)?.value?.toIntOrNull() ?: 0
    return entries.sortedWith(compareBy<CatalogEntry> { rank(it) }.thenByDescending { books(it) })
}

/** Books this app can open, and how many others the feed listed. */
internal fun readableEntries(entries: List<CatalogEntry>): Pair<List<CatalogEntry>, Int> {
    val shown = entries.filter { !it.isBook || it.downloads.isNotEmpty() || it.web != null }
    return shown to entries.size - shown.size
}
