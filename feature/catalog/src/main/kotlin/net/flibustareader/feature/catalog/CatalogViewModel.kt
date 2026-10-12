package net.flibustareader.feature.catalog

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import net.flibustareader.core.data.catalog.CatalogRepository
import net.flibustareader.core.opds.CatalogEntry
import net.flibustareader.core.opds.CatalogPage
import javax.inject.Inject

/** What the catalog is busy with, for the status capsule. */
sealed interface CatalogWork {
    data object Loading : CatalogWork
    data class Downloading(val percent: Int?) : CatalogWork
}

/**
 * OPDS browsing state. Addresses (current and back stack) survive process death through
 * [SavedStateHandle]; their pages are re-fetched on restore.
 */
@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val repository: CatalogRepository,
    private val saved: SavedStateHandle,
) : ViewModel() {
    var query by mutableStateOf(saved.get<String>(KEY_QUERY).orEmpty())
        private set
    var searchAuthors by mutableStateOf(saved.get<Boolean>(KEY_AUTHORS) ?: false)
        private set
    var page by mutableStateOf<CatalogPage?>(null)
        private set
    var work by mutableStateOf<CatalogWork?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var selected by mutableStateOf<CatalogEntry?>(null)

    private var address: String = saved.get<String>(KEY_ADDRESS).orEmpty()
    private val history = ArrayList<Pair<String, CatalogPage>>()
    var canGoBack by mutableStateOf(false)
        private set

    private val _opened = Channel<String>(Channel.BUFFERED)
    /** Ids of downloaded books, to open them right away. */
    val opened: Flow<String> = _opened.receiveAsFlow()

    val catalogBase: String get() = repository.catalogBase
    val downloadBase: String get() = repository.downloadBase

    init {
        val addresses = saved.get<ArrayList<String>>(KEY_HISTORY).orEmpty()
        if (address.isNotBlank()) perform(CatalogWork.Loading) {
            addresses.forEach { history += it to repository.page(it) }
            page = repository.page(address)
            syncHistory()
        }
    }

    fun updateQuery(value: String) { query = value; saved[KEY_QUERY] = value }

    fun updateAuthors(value: Boolean) { searchAuthors = value; saved[KEY_AUTHORS] = value }

    fun search() {
        if (query.isBlank()) return
        load(repository.searchAddress(query, searchAuthors), reset = true)
    }

    fun newArrivals() = load(repository.newAddress(), reset = true)

    fun open(entry: CatalogEntry) {
        val target = entry.navigation
        if (target != null) load(target, push = true) else selected = entry
    }

    fun more() { page?.next?.let { load(it, append = true) } }

    fun back(): Boolean {
        if (work != null || history.isEmpty()) return false
        val (previousAddress, previousPage) = history.removeAt(history.lastIndex)
        address = previousAddress
        page = previousPage
        syncHistory()
        return true
    }

    fun download(entry: CatalogEntry, format: String) {
        selected = null
        perform(CatalogWork.Downloading(null)) {
            val result = repository.download(entry, format) { percent -> work = CatalogWork.Downloading(percent) }
            _opened.send(result.bookId)
        }
    }

    fun dismissError() { error = null }

    /** Returns an error message, or null when saved. */
    fun saveSources(catalog: String, downloads: String): String? = try {
        repository.saveSources(catalog, downloads)
        history.clear(); page = null; address = ""; syncHistory()
        null
    } catch (e: IllegalArgumentException) {
        e.message
    }

    private fun load(target: String, reset: Boolean = false, push: Boolean = false, append: Boolean = false) {
        val previous = page
        val previousAddress = address
        perform(CatalogWork.Loading) {
            val result = repository.page(target)
            if (reset) history.clear()
            if (push && previous != null) history += previousAddress to previous
            page = if (append && previous != null) result.copy(entries = (previous.entries + result.entries).distinctBy { it.id }) else result
            address = target
            syncHistory()
        }
    }

    private fun syncHistory() {
        canGoBack = history.isNotEmpty()
        saved[KEY_ADDRESS] = address
        saved[KEY_HISTORY] = ArrayList(history.map { it.first })
    }

    private fun perform(kind: CatalogWork, action: suspend () -> Unit) {
        if (work != null) return
        work = kind
        error = null
        viewModelScope.launch {
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            } finally {
                work = null
            }
        }
    }

    private companion object {
        const val KEY_QUERY = "query"
        const val KEY_AUTHORS = "authors"
        const val KEY_ADDRESS = "address"
        const val KEY_HISTORY = "history"
    }
}
