package net.flibustareader.feature.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import net.flibustareader.core.data.db.BookEntity
import net.flibustareader.core.data.db.CollectionEntity
import net.flibustareader.core.data.library.FolderScanner
import net.flibustareader.core.data.library.ImportResult
import net.flibustareader.core.data.library.LibraryRepository
import net.flibustareader.core.data.settings.Appearance
import net.flibustareader.core.data.settings.LibraryLayout
import net.flibustareader.core.data.settings.LibrarySettings
import net.flibustareader.core.data.settings.LibrarySort
import net.flibustareader.core.data.settings.SettingsRepository
import net.flibustareader.core.data.settings.SystemCollection
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** A list of books shown on its own screen: a built-in collection, a user collection, or «Предыдущие». */
sealed interface Shelf {
    val key: String

    data object WantToRead : Shelf { override val key = "want" }
    data object Finished : Shelf { override val key = "finished" }
    data object Books : Shelf { override val key = "books" }
    data object Previous : Shelf { override val key = "previous" }
    data class Collection(val id: Long) : Shelf { override val key = "c$id" }

    companion object {
        fun fromKey(key: String): Shelf = when {
            key == WantToRead.key -> WantToRead
            key == Finished.key -> Finished
            key == Books.key -> Books
            key == Previous.key -> Previous
            key.startsWith("c") -> Collection(key.drop(1).toLongOrNull() ?: 0)
            else -> Books
        }
    }
}

data class BookItem(val entity: BookEntity, val cover: File?) {
    val id: String get() = entity.id
    val title: String get() = entity.displayTitle
    val author: String? get() = entity.authorList.firstOrNull()

    /** Gradient for the «Продолжить» card, from colours sampled off the cover at import. */
    val gradient: Pair<Color, Color>? by lazy {
        entity.coverColors?.split(',')?.mapNotNull { it.toIntOrNull() }?.takeIf { it.size == 2 }
            ?.let { Color(it[0]) to Color(it[1]) }
    }
}

data class HomeState(
    val loaded: Boolean = false,
    val isEmpty: Boolean = true,
    val continueReading: List<BookItem> = emptyList(),
    val wantToRead: List<BookItem> = emptyList(),
    val previous: List<BookItem> = emptyList(),
)

/** One row of the «Коллекции» sheet. */
data class CollectionRow(val shelf: Shelf, val name: String?, val count: Int, val system: SystemCollection?)

sealed interface LibraryMessage {
    data class Added(val count: Int) : LibraryMessage
    data object AlreadyThere : LibraryMessage
    data class Failed(val name: String) : LibraryMessage
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val library: LibraryRepository,
    private val settings: SettingsRepository,
    private val scanner: FolderScanner,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val books: StateFlow<List<BookItem>> = library.allBooks
        .map { list -> list.map { BookItem(it, library.coverFile(it.id).takeIf { _ -> it.hasCover }) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val librarySettings: StateFlow<LibrarySettings> =
        settings.library.stateIn(viewModelScope, SharingStarted.Eagerly, LibrarySettings())

    val appAppearance: StateFlow<Appearance> =
        settings.appAppearance.stateIn(viewModelScope, SharingStarted.Eagerly, Appearance.Auto)

    fun setAppAppearance(value: Appearance) = viewModelScope.launch { settings.setAppAppearance(value) }

    val collections: StateFlow<List<CollectionEntity>> =
        library.collections.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * «Продолжить» holds started, unfinished books not removed from it; everything else that was
     * ever opened goes to «Предыдущие». Both are ordered by the last time the book was opened.
     */
    val home: StateFlow<HomeState> = books.map { all ->
        val opened = all.filter { it.entity.isStarted }.sortedByDescending { it.entity.lastOpenedAt }
        val (continueReading, previous) = opened.partition { !it.entity.isFinished && !it.entity.hiddenFromContinue }
        HomeState(
            loaded = true,
            isEmpty = all.isEmpty(),
            continueReading = continueReading,
            wantToRead = all.filter { it.entity.wantToRead && !it.entity.isFinished },
            previous = previous,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeState())

    /** All books in the library tab, ordered by the chosen sort. */
    val libraryBooks: StateFlow<List<BookItem>> = combine(books, librarySettings) { all, prefs -> sort(all, prefs.sort) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val collectionRows: StateFlow<List<CollectionRow>> = combine(
        books, collections, library.collectionCounts, librarySettings,
    ) { all, user, counts, prefs ->
        buildList {
            add(CollectionRow(Shelf.WantToRead, null, all.count { it.entity.wantToRead }, SystemCollection.WantToRead))
            add(CollectionRow(Shelf.Finished, null, all.count { it.entity.isFinished }, SystemCollection.Finished))
            add(CollectionRow(Shelf.Books, null, all.size, SystemCollection.Books))
            user.forEach { add(CollectionRow(Shelf.Collection(it.id), it.name, counts[it.id] ?: 0, null)) }
        }.filter { it.system == null || it.system !in prefs.hiddenSystemCollections }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Rows including hidden system collections, for edit mode. */
    val allCollectionRows: StateFlow<List<CollectionRow>> = combine(books, collections, library.collectionCounts) { all, user, counts ->
        buildList {
            add(CollectionRow(Shelf.WantToRead, null, all.count { it.entity.wantToRead }, SystemCollection.WantToRead))
            add(CollectionRow(Shelf.Finished, null, all.count { it.entity.isFinished }, SystemCollection.Finished))
            add(CollectionRow(Shelf.Books, null, all.size, SystemCollection.Books))
            user.forEach { add(CollectionRow(Shelf.Collection(it.id), it.name, counts[it.id] ?: 0, null)) }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun shelfBooks(shelf: Shelf): Flow<List<BookItem>> = when (shelf) {
        Shelf.WantToRead -> books.map { all -> all.filter { it.entity.wantToRead } }
        Shelf.Finished -> books.map { all -> all.filter { it.entity.isFinished }.sortedByDescending { it.entity.finishedAt } }
        Shelf.Books -> libraryBooks
        Shelf.Previous -> home.map { it.previous }
        is Shelf.Collection -> library.collectionBookIds(shelf.id).flatMapLatest { ids ->
            books.map { all ->
                val byId = all.associateBy { it.id }
                ids.mapNotNull { byId[it] }
            }
        }
    }

    fun collectionName(id: Long): String? = collections.value.firstOrNull { it.id == id }?.name

    fun collectionsOf(bookId: String): Flow<List<Long>> = library.collectionsOf(bookId)

    // --- Selection mode ---

    var selecting by mutableStateOf(false)
        private set
    var selection by mutableStateOf(emptySet<String>())
        private set

    fun startSelection() {
        selecting = true
        selection = emptySet()
    }

    fun endSelection() {
        selecting = false
        selection = emptySet()
    }

    fun toggleSelected(id: String) {
        selection = if (id in selection) selection - id else selection + id
    }

    fun selectAll(ids: List<String>) {
        selection = ids.toSet()
    }

    // --- Search ---

    val query = MutableStateFlow("")
    val searchHistory: StateFlow<List<String>> = library.searchHistory.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val searchResults: StateFlow<List<BookItem>> = combine(books, query) { all, q ->
        val needle = normalize(q.trim())
        if (needle.isEmpty()) {
            emptyList()
        } else {
            all.filter { normalize(it.title).contains(needle) || normalize(it.entity.authors).contains(needle) || normalize(it.entity.title).contains(needle) }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setQuery(value: String) {
        query.value = value
    }

    fun commitSearch() = viewModelScope.launch { library.rememberSearch(query.value) }
    fun clearSearchHistory() = viewModelScope.launch { library.clearSearchHistory() }

    private fun normalize(s: String) = s.lowercase().replace('ё', 'е')

    // --- Messages ---

    private val _messages = Channel<LibraryMessage>(Channel.BUFFERED)
    val messages: Flow<LibraryMessage> = _messages.receiveAsFlow()

    init {
        viewModelScope.launch { library.backfillCoverColors() }
    }

    // --- Import and folder ---

    fun import(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            var added = 0
            for (uri in uris) {
                runCatching { library.import(uri) }
                    .onSuccess { if (it is ImportResult.Added) added++ else if (uris.size == 1) _messages.send(LibraryMessage.AlreadyThere) }
                    .onFailure { _messages.send(LibraryMessage.Failed(uri.lastPathSegment?.substringAfterLast('/') ?: "file")) }
            }
            if (added > 0) _messages.send(LibraryMessage.Added(added))
        }
    }

    fun watchFolder(uri: Uri?) = viewModelScope.launch {
        val previous = librarySettings.value.watchedFolder
        if (previous != null && previous != uri?.toString()) {
            runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(previous), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
        if (uri != null) context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        settings.setWatchedFolder(uri?.toString())
        if (uri != null) scanNow()
    }

    fun scanNow() = viewModelScope.launch {
        val added = runCatching { scanner.scan() }.getOrDefault(0)
        if (added > 0) _messages.send(LibraryMessage.Added(added))
    }

    // --- Book actions ---

    fun setWantToRead(id: String, value: Boolean) = viewModelScope.launch { library.setWantToRead(id, value) }
    fun setFinished(id: String, value: Boolean) = viewModelScope.launch { library.setFinished(id, value) }
    fun rename(id: String, title: String) = viewModelScope.launch { library.rename(id, title) }
    fun removeFromContinue(id: String) = viewModelScope.launch { library.setHiddenFromContinue(id, true) }
    fun delete(ids: Collection<String>) = viewModelScope.launch {
        ids.forEach {
            library.delete(it)
            settings.setBookAppearance(it, null)
        }
    }

    /** Adds books to a shelf. Built-in shelves are flags on the book; only user collections are real tables. */
    fun addTo(shelf: Shelf, ids: Collection<String>) = viewModelScope.launch {
        ids.forEach { id ->
            when (shelf) {
                Shelf.WantToRead -> library.setWantToRead(id, true)
                Shelf.Finished -> library.setFinished(id, true)
                is Shelf.Collection -> library.addToCollection(shelf.id, id)
                else -> Unit
            }
        }
    }

    fun removeFrom(shelf: Shelf, id: String) = viewModelScope.launch {
        when (shelf) {
            Shelf.WantToRead -> library.setWantToRead(id, false)
            Shelf.Finished -> library.setFinished(id, false)
            is Shelf.Collection -> library.removeFromCollection(shelf.id, id)
            Shelf.Previous -> library.setHiddenFromContinue(id, false)
            Shelf.Books -> Unit
        }
    }

    /** Drag-and-drop in the library switches the sort to «Вручную», as Apple Books does. */
    fun moveBook(fromId: String, toId: String) {
        val current = libraryBooks.value.map { it.id }.toMutableList()
        val from = current.indexOf(fromId)
        val to = current.indexOf(toId)
        if (from < 0 || to < 0) return
        current.add(to, current.removeAt(from))
        viewModelScope.launch {
            if (librarySettings.value.sort != LibrarySort.Manual) settings.setLibrarySort(LibrarySort.Manual)
            library.reorderBooks(current)
        }
    }

    fun bookFile(item: BookItem): File? =
        File(context.filesDir, "books").listFiles { f -> f.name.startsWith(item.id + ".") }?.firstOrNull()

    // --- View options ---

    fun setLayout(value: LibraryLayout) = viewModelScope.launch { settings.setLibraryLayout(value) }
    fun setSort(value: LibrarySort) = viewModelScope.launch { settings.setLibrarySort(value) }

    // --- Collections ---

    fun createCollection(name: String, addIds: Collection<String> = emptyList()) = viewModelScope.launch {
        if (name.isBlank()) return@launch
        val id = library.createCollection(name)
        addIds.forEach { library.addToCollection(id, it) }
    }

    fun renameCollection(id: Long, name: String) = viewModelScope.launch { if (name.isNotBlank()) library.renameCollection(id, name) }
    fun deleteCollection(id: Long) = viewModelScope.launch { library.deleteCollection(id) }
    fun reorderCollections(ids: List<Long>) = viewModelScope.launch { library.reorderCollections(ids) }
    fun setSystemHidden(collection: SystemCollection, hidden: Boolean) = viewModelScope.launch { settings.setSystemCollectionHidden(collection, hidden) }

    private fun sort(all: List<BookItem>, sort: LibrarySort): List<BookItem> = when (sort) {
        LibrarySort.Recent -> all.sortedByDescending { it.entity.lastOpenedAt ?: it.entity.addedAt }
        LibrarySort.Title -> all.sortedBy { it.title.lowercase() }
        LibrarySort.Author -> all.sortedBy { (it.author ?: "￿").substringAfterLast(' ').lowercase() }
        LibrarySort.Manual -> all.sortedWith(compareBy<BookItem> { it.entity.manualOrder }.thenByDescending { it.entity.addedAt })
    }
}
