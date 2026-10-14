package app.folio.feature.catalog

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import app.folio.core.data.catalog.CatalogRepository
import app.folio.core.data.catalog.DownloadItem
import app.folio.core.data.catalog.DownloadManager
import app.folio.core.data.catalog.NetworkErrors
import app.folio.core.data.db.DownloadState
import app.folio.core.opds.CatalogEntry
import app.folio.core.opds.CatalogLink
import app.folio.core.opds.CatalogPage
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

@Stable
class SearchResults(val query: String, val books: Feed, val authors: Feed) {
    var scope by mutableStateOf(SearchScope.All)
}

/**
 * Catalog state: the home (root sections and this week's books), a stack of opened folders,
 * book + author search, and the download list. Open folders and the last search survive process
 * death through [SavedStateHandle]; their pages are re-fetched (the repository caches them).
 */
@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val repository: CatalogRepository,
    private val downloadManager: DownloadManager,
    private val saved: SavedStateHandle,
) : ViewModel() {
    var root by mutableStateOf(Feed(repository.rootAddress(), ""))
        private set
    var newBooks by mutableStateOf(Feed(repository.newBooksAddress(), ""))
        private set
    val stack = mutableStateListOf<Feed>()
    var results by mutableStateOf<SearchResults?>(null)
        private set
    var query by mutableStateOf(saved.get<String>(KEY_QUERY).orEmpty())
        private set
    var selected by mutableStateOf<CatalogEntry?>(null)
    /** One-off error of a download request, shown in the book sheet. */
    var downloadError by mutableStateOf<String?>(null)

    val recent: StateFlow<List<String>> = repository.recent
    val downloads: StateFlow<List<DownloadItem>> =
        downloadManager.downloads.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val catalogBase: String get() = repository.catalogBase
    val downloadBase: String get() = repository.downloadBase
    val canGoBack: Boolean get() = stack.isNotEmpty() || results != null

    init {
        loadHome()
        saved.get<String>(KEY_SEARCH)?.let { submitted ->
            search(submitted, remember = false)
            saved.get<String>(KEY_SCOPE)?.let { scope -> results?.scope = SearchScope.valueOf(scope) }
        }
        val addresses = saved.get<ArrayList<String>>(KEY_STACK).orEmpty()
        val titles = saved.get<ArrayList<String>>(KEY_TITLES).orEmpty()
        addresses.forEachIndexed { i, address -> push(address, titles.getOrElse(i) { "" }) }
    }

    fun loadHome(fresh: Boolean = false) {
        load(root, fresh = fresh)
        load(newBooks, fresh = fresh, fill = SHELF_BOOKS)
    }

    fun updateQuery(value: String) { query = value; saved[KEY_QUERY] = value }

    fun search(text: String = query, remember: Boolean = true) {
        val value = text.trim()
        if (value.isEmpty()) return
        updateQuery(value)
        if (remember) repository.rememberSearch(value)
        results?.let { cancel(it.books); cancel(it.authors) }
        val next = SearchResults(
            value,
            books = Feed(repository.searchAddress(value, authors = false), value),
            authors = Feed(repository.searchAddress(value, authors = true), value),
        )
        results = next
        stack.forEach(::cancel)
        stack.clear()
        load(next.books)
        load(next.authors)
        syncSaved()
    }

    fun setScope(scope: SearchScope) {
        results?.scope = scope
        syncSaved()
    }

    fun closeSearch() {
        results?.let { cancel(it.books); cancel(it.authors) }
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

    fun openNewBooks(title: String) = push(newBooks.address, newBooks.page?.title?.ifBlank { null } ?: title)

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

    fun download(entry: CatalogEntry, format: String) {
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

    fun remove(id: String) { viewModelScope.launch { downloadManager.remove(id) } }

    fun clearFinished() { viewModelScope.launch { downloadManager.clearFinished() } }

    suspend fun cover(url: String): File? = repository.cover(url)

    /** Returns an error message, or null when saved. */
    fun saveSources(catalog: String, downloads: String): String? = try {
        repository.saveSources(catalog, downloads)
        stack.forEach(::cancel)
        stack.clear()
        cancel(root); cancel(newBooks)
        root = Feed(repository.rootAddress(), "")
        newBooks = Feed(repository.newBooksAddress(), "")
        closeSearch()
        loadHome()
        null
    } catch (e: IllegalArgumentException) {
        e.message
    }

    private fun push(address: String, title: String) {
        val feed = Feed(address, title)
        stack += feed
        load(feed)
        syncSaved()
    }

    /**
     * Loads [feed]'s first page (or the next one with [more]). With [fill], keeps fetching pages until
     * that many readable books are in: most of Flibusta's weekly list is PDF.
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
 * Flibusta lists authors alphabetically, so «Лем» comes after «(Лемешевский)». Rank the loaded
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
    val shown = entries.filter { !it.isBook || it.downloads.isNotEmpty() }
    return shown to entries.size - shown.size
}
