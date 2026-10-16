package app.folio.feature.reader

import androidx.compose.runtime.setValue
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.folio.core.data.db.BookmarkEntity
import app.folio.core.data.db.HighlightEntity
import app.folio.core.data.library.BookResources
import app.folio.core.data.library.LibraryRepository
import app.folio.core.data.settings.BookOverrides
import app.folio.core.data.settings.ReaderSettings
import app.folio.core.data.settings.SettingsRepository
import app.folio.core.designsystem.ResourceTypefaceProvider
import app.folio.core.layout.Ink
import app.folio.core.layout.LayoutSpec
import app.folio.core.layout.Page
import app.folio.core.layout.PageItem
import app.folio.core.layout.PageRenderer
import app.folio.core.layout.Paginator
import app.folio.core.layout.ReaderStyle
import app.folio.core.layout.TextRange
import app.folio.core.layout.TextSpan
import app.folio.core.model.Block
import app.folio.core.model.Book
import app.folio.core.model.Position
import app.folio.core.model.Span
import app.folio.core.model.TocEntry
import app.folio.core.model.footnoteBlocks
import app.folio.core.model.parseInternalTarget
import app.folio.core.model.progressAt
import app.folio.core.speech.SpeechController
import app.folio.core.speech.SpeechState
import app.folio.core.data.settings.PageTurn
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** Size of the text box inside the page margins, supplied by the UI. */
data class Viewport(val width: Int, val height: Int, val density: Float, val fontScale: Float)

data class ReaderState(
    val loading: Boolean = true,
    val error: Boolean = false,
    val title: String = "",
    val current: PageRef? = null,
    val page: Page? = null,
    val prev: Page? = null,
    val next: Page? = null,
    /** The book goes on, even while that page's chapter is still laying out ([prev]/[next] null). */
    val hasPrev: Boolean = false,
    val hasNext: Boolean = false,
    val chapterTitle: String? = null,
    val currentTocIndex: Int? = null,
    val pagesLeftInChapter: Int? = null,
    val globalPage: Int? = null,
    val totalPages: Int? = null,
    val progress: Float = 0f,
    val bookmarked: Boolean = false,
    val highlight: TextRange? = null,
    /** Bumped whenever pagination is rebuilt so the UI drops stale pages. */
    val layoutGeneration: Int = 0,
)

sealed interface TapTarget {
    data class Note(val blocks: List<Block>) : TapTarget
    data class Link(val position: Position) : TapTarget
    data class External(val url: String) : TapTarget
    data class Image(val file: File) : TapTarget
}

/** A jump the scroll list must perform; [id] tells repeated jumps to the same place apart. */
class ScrollJump(val position: Position, val id: Int)

/** Jump history for ↶ / ↷: positions to return to and to come back to. */
data class NavHistory(val back: List<Position> = emptyList(), val forward: List<Position> = emptyList())

@OptIn(FlowPreview::class)
@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val library: LibraryRepository,
    private val settingsRepository: SettingsRepository,
    private val speech: SpeechController,
    @ApplicationContext context: Context,
) : ViewModel() {
    val bookId: String = checkNotNull(savedState["bookId"])

    private val typefaces = ResourceTypefaceProvider(context)
    val ink = Ink()

    /** Settings shared by all books. */
    val sharedSettings: StateFlow<ReaderSettings> = settingsRepository.reader
        .stateIn(viewModelScope, SharingStarted.Eagerly, ReaderSettings())

    /** What this book changes over [sharedSettings]. */
    val ownSettings: StateFlow<BookOverrides> = settingsRepository.bookOverrides(bookId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, BookOverrides())

    /** The book as it is read: shared settings with its own on top. */
    val settings: StateFlow<ReaderSettings> = combine(sharedSettings, ownSettings) { shared, own -> own.applyTo(shared) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ReaderSettings())

    val bookmarks: StateFlow<List<BookmarkEntity>> =
        library.bookmarks(bookId).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * Snapshot state rather than a StateFlow: page turns write it on the main thread and the
     * pager resets its drag offset in the same frame, so there is never a one-frame flash of
     * the old page.
     */
    var state by mutableStateOf(ReaderState())
        private set

    private inline fun update(transform: (ReaderState) -> ReaderState) {
        state = transform(state)
    }

    private val bookSearch = ReaderSearch(viewModelScope, { book }, ::chapterTitleAt)
    val search: StateFlow<SearchState> = bookSearch.state

    private val annotations = ReaderAnnotations(
        bookId,
        library,
        settingsRepository,
        currentBook = { book },
        savedHighlights = { highlights.value },
        highlightColor = { settings.value.highlightColor },
        chapterTitleAt = ::chapterTitleAt,
    )

    private val viewport = MutableStateFlow<Viewport?>(null)
    private val savedPosition = MutableStateFlow<Position?>(null)

    var book: Book? = null
        private set
    private lateinit var resources: BookResources
    private var engine: PaginationEngine? = null
    private var chapterProgress: ChapterProgress? = null
    private var position: Position = Position.Start
    private var generation = 0
    private var sessionStart = 0L
    private var finishedMarked = false

    val toc: List<TocEntry> get() = book?.toc.orEmpty()
    internal val tocRows: List<ChapterTocRow> get() = chapterProgress?.rows.orEmpty()

    /** Library thumbnail of the cover for the contents header. */
    val coverFile: File? = library.coverFile(bookId).takeIf { it.exists() }

    init {
        viewModelScope.launch {
            val loaded = runCatching { library.load(bookId) }.getOrNull()
            if (loaded == null) {
                update { it.copy(loading = false, error = true) }
                return@launch
            }
            book = loaded
            chapterProgress = ChapterProgress(loaded)
            resources = library.resources(bookId)
            val entity = library.book(bookId).first()
            finishedMarked = entity?.isFinished == true
            position = Position.decode(entity?.position) ?: Position.Start
            update { it.copy(title = loaded.metadata.title) }
            library.markOpened(bookId)

            combine(
                settings.map { it.style }.distinctUntilChanged(),
                viewport.filterNotNull(),
            ) { style, vp -> style to vp }
                .distinctUntilChanged()
                .collectLatest { (style, vp) -> rebuild(loaded, style, vp) }
        }
        viewModelScope.launch {
            savedPosition.filterNotNull().debounce(400).collectLatest { pos ->
                book?.let { library.savePosition(bookId, pos, it.progressAt(pos)) }
            }
        }
        viewModelScope.launch {
            bookmarks.collect { refreshBookmarked() }
        }
        viewModelScope.launch {
            speech.state.map { it.current?.start.takeIf { _ -> it.bookId == bookId } }
                .distinctUntilChanged().filterNotNull()
                .collect(::followSpeech)
        }
    }

    // --- Read aloud ---

    /** Reading aloud, app-wide; [SpeechState.bookId] tells whether it is this book. */
    val speechState: StateFlow<SpeechState> = speech.state

    /** «Слушать»: reads from the current page, or resumes this book if it is already loaded. */
    fun listen() {
        val book = book ?: return
        val s = speech.state.value
        if (s.bookId == bookId && s.current != null) speech.toggle() else speech.start(bookId, book, readingPosition)
    }

    fun toggleSpeech() = speech.toggle()
    fun skipSpeech(delta: Int) = speech.skip(delta)
    fun setSpeechRate(rate: Float) = speech.setRate(rate)
    fun stopSpeech() = speech.stop()
    fun dismissNoVoice() = speech.dismissNoVoice()

    /** Turns to the spoken phrase once it leaves the screen (in Scroll the next page is on screen too). */
    private fun followSpeech(start: Position) {
        val page = state.page ?: return
        val next = state.next
        fun ends(p: Page, after: Page?) = after?.let { start < it.start } ?: (start.chapter == p.chapter)
        val afterNext = next?.let { engine?.peek(it.chapter)?.pages?.getOrNull(it.index + 1) }
        val shown = start >= page.start && ends(page, next) ||
            settings.value.pageTurn == PageTurn.Scroll && next != null && start >= next.start && ends(next, afterNext)
        if (!shown) goTo(start, record = false)
    }

    fun onViewport(v: Viewport) {
        viewport.value = v
    }

    private suspend fun rebuild(book: Book, style: ReaderStyle, vp: Viewport) {
        engine?.cancel()
        val spec = LayoutSpec(style, vp.width, vp.height, vp.density, vp.fontScale, book.metadata.language)
        val newEngine = PaginationEngine(
            book,
            Paginator(spec, typefaces, resources, ink),
            Paginator(spec, typefaces, resources, Ink()),
            viewModelScope,
        )
        engine = newEngine
        generation++
        newEngine.request(position.chapter.coerceIn(0, book.chapters.lastIndex))
        showPosition(position, highlight = state.highlight)
        newEngine.startCounting()
        newEngine.pageOffsets.collect { updateLabels() }
    }

    private suspend fun showPosition(target: Position, highlight: TextRange? = null) {
        val engine = engine ?: return
        val book = book ?: return
        val chapter = target.chapter.coerceIn(0, book.chapters.lastIndex)
        val pages = engine.pages(chapter)
        val index = pages.pageIndexOf(target)
        setCurrent(PageRef(chapter, index), keepPosition = target, highlight = highlight)
        scrollJump = ScrollJump(position, (scrollJump?.id ?: 0) + 1)
    }

    /** The exact reading position: a page start when paging, the top line when scrolling. */
    val readingPosition: Position get() = position

    /** Latest jump for the scroll mode, which keeps its own list and must follow jumps itself. */
    var scrollJump by mutableStateOf<ScrollJump?>(null)
        private set

    private fun setCurrent(ref: PageRef, keepPosition: Position? = null, highlight: TextRange? = null, reading: Boolean = false) {
        val engine = engine ?: return
        val page = engine.page(ref) ?: return
        if (reading) historyVisibility = historyVisibility.read(state.current, ref, engine.pageOffsets.value)
        position = keepPosition ?: page.start
        savedPosition.value = position
        engine.prefetchAround(ref.chapter)
        update {
            it.copy(
                loading = false,
                current = ref,
                page = page,
                prev = engine.prevRef(ref)?.let(engine::page),
                next = engine.nextRef(ref)?.let(engine::page),
                hasPrev = engine.prevRef(ref) != null,
                hasNext = engine.nextRef(ref) != null,
                highlight = highlight,
                layoutGeneration = generation,
            )
        }
        updateLabels()
        refreshBookmarked()
        fillNeighbours(ref)
        if (!finishedMarked && engine.nextRef(ref) == null && state.page != null) {
            finishedMarked = true
            viewModelScope.launch { library.setFinished(bookId, true) }
        }
    }

    /** Neighbouring chapters may still be laying out; fill prev/next as soon as they are ready. */
    private fun fillNeighbours(ref: PageRef) {
        val engine = engine ?: return
        val s = state
        if (s.next == null && ref.chapter + 1 < engine.chapterCount && engine.nextRef(ref) != null) {
            viewModelScope.launch {
                engine.pages(ref.chapter + 1)
                if (state.current == ref) update { it.copy(next = engine.nextRef(ref)?.let(engine::page)) }
            }
        }
        if (s.prev == null && ref.chapter > 0 && ref.index == 0) {
            viewModelScope.launch {
                engine.pages(ref.chapter - 1)
                if (state.current == ref) update { it.copy(prev = engine.prevRef(ref)?.let(engine::page)) }
            }
        }
    }

    private fun updateLabels() {
        val engine = engine ?: return
        val book = book ?: return
        val ref = state.current ?: return
        if (engine.page(ref) == null) return
        val offsets = engine.pageOffsets.value
        update {
            it.copy(
                chapterTitle = chapterTitleAt(position),
                currentTocIndex = chapterProgress?.currentRowAt(position),
                pagesLeftInChapter = pagesLeftAt(position, ref),
                globalPage = offsets?.let { o -> o[ref.chapter] + ref.index + 1 },
                totalPages = offsets?.last(),
                progress = book.progressAt(position),
            )
        }
    }

    /** Global page number for a location, when it can be known without extra layout work. */
    fun pageNumberOf(position: Position): Int? {
        val engine = engine ?: return null
        val offsets = engine.pageOffsets.value ?: return null
        val base = offsets.getOrNull(position.chapter) ?: return null
        val index = engine.pageIndexOf(position)
        return when {
            index != null -> base + index + 1
            position.block == 0 && position.offset == 0 -> base + 1
            else -> null
        }
    }

    fun chapterTitleAt(position: Position): String? = chapterProgress?.titleAt(position)

    /** Moves one page forward; returns false at the end of the book or while the next chapter lays out. */
    fun next(): Boolean {
        val engine = engine ?: return false
        val ref = state.current ?: return false
        val nextRef = engine.nextRef(ref) ?: return false
        if (engine.page(nextRef) != null) {
            setCurrent(nextRef, reading = true)
        } else {
            viewModelScope.launch {
                engine.pages(nextRef.chapter)
                setCurrent(nextRef, reading = true)
            }
        }
        return true
    }

    fun prev(): Boolean {
        val engine = engine ?: return false
        val ref = state.current ?: return false
        val prevRef = engine.prevRef(ref) ?: return false
        val resolved = if (prevRef.index == PaginationEngine.LAST) null else prevRef
        if (resolved != null && engine.page(resolved) != null) {
            setCurrent(resolved, reading = true)
        } else {
            viewModelScope.launch {
                val pages = engine.pages(prevRef.chapter)
                setCurrent(PageRef(prevRef.chapter, if (prevRef.index == PaginationEngine.LAST) pages.pages.lastIndex else prevRef.index), reading = true)
            }
        }
        return true
    }

    // --- Scroll mode ---

    val chapterCount: Int get() = book?.chapters?.size ?: 0

    suspend fun chapterPages(chapter: Int) = engine?.takeIf { chapter in 0 until chapterCount }?.pages(chapter)

    /**
     * Scroll mode reports the line at the top of the text and the page it is on. [jumpId] is the
     * last jump the list has shown: until it shows the latest one, its reports are stale and would
     * undo the jump.
     */
    fun onScrolledTo(page: Page, top: Position, jumpId: Int) {
        if (jumpId != (scrollJump?.id ?: 0)) return
        position = top
        savedPosition.value = top
        val ref = PageRef(page.chapter, page.index)
        if (state.current == ref) {
            updateLabels()
            return
        }
        val engine = engine ?: return
        if (state.current?.chapter != ref.chapter) engine.prefetchAround(ref.chapter)
        historyVisibility = historyVisibility.read(state.current, ref, engine.pageOffsets.value)
        // Neighbours too, so switching to page turning from here works at once.
        update {
            it.copy(
                current = ref,
                page = page,
                prev = engine.prevRef(ref)?.let(engine::page),
                next = engine.nextRef(ref)?.let(engine::page),
                hasPrev = engine.prevRef(ref) != null,
                hasNext = engine.nextRef(ref) != null,
            )
        }
        updateLabels()
        refreshBookmarked()
    }

    /**
     * Jumps to [target]. Jumps (contents, search, links, notes) go into the navigation history
     * so the reader can return with ↶ and come back with ↷, as in Apple Books.
     */
    fun goTo(target: Position, highlight: TextRange? = null, record: Boolean = true) {
        if (record) {
            val here = position
            history = NavHistory(back = history.back + here, forward = emptyList())
        }
        viewModelScope.launch {
            showPosition(target, highlight)
            historyVisibility = historyVisibility.jumped()
        }
    }

    internal var historyVisibility by mutableStateOf(HistoryVisibility())
        private set

    /** Pages after [page] before the next TOC target; null while the boundary's pagination is unknown. */
    fun pagesLeftAfter(page: Page): Int? = pagesLeftAt(chapterPositionOf(page), PageRef(page.chapter, page.index))

    internal fun chapterPositionOf(page: Page): Position = if (page === state.page) position else page.start

    private fun pagesLeftAt(position: Position, ref: PageRef): Int? {
        val engine = engine ?: return null
        return chapterProgress?.remainingPages(
            position, ref, engine.pageOffsets.value,
            pageIndexOf = engine::pageIndexOf,
            pageStartOf = engine::pageStartOf,
            pageCountOf = { chapter -> engine.peek(chapter)?.pages?.size },
        )
    }

    fun progressOf(page: Page): Float = book?.progressAt(page.start) ?: 0f

    /** Page numbers or positions the ↶ / ↷ marks jump to. */
    var history by mutableStateOf(NavHistory())
        private set

    fun historyBack() {
        val target = history.back.lastOrNull() ?: return
        val here = position
        history = NavHistory(back = history.back.dropLast(1), forward = history.forward + here)
        goTo(target, record = false)
    }

    fun historyForward() {
        val target = history.forward.lastOrNull() ?: return
        val here = position
        history = NavHistory(back = history.back + here, forward = history.forward.dropLast(1))
        goTo(target, record = false)
    }

    fun goToProgress(fraction: Float) {
        val target = book?.readerPositionAtProgress(fraction) ?: return
        if (target != position) goTo(target)
    }

    internal fun goToPage(number: Int) {
        val currentEngine = engine ?: return
        val ref = readerPageRef(number, currentEngine.pageOffsets.value) ?: return
        viewModelScope.launch {
            val target = currentEngine.pages(ref.chapter).pages.getOrNull(ref.index)?.start ?: return@launch
            if (engine === currentEngine && target != position) goTo(target)
        }
    }

    internal fun previewProgress(fraction: Float): ReaderProgressPreview {
        val target = book?.readerPositionAtProgress(fraction)
        return ReaderProgressPreview(target?.let(::chapterTitleAt), target?.let(::pageNumberOf))
    }

    fun clearHighlight() {
        if (state.highlight != null) update { it.copy(highlight = null) }
    }

    /** Resolves a tap at content-box coordinates on the current page. */
    fun tapTarget(page: Page, x: Float, y: Float): TapTarget? {
        val book = book ?: return null
        for (item in page.items) {
            if (item is PageItem.Image && x >= item.left && x <= item.left + item.width && y >= item.top && y <= item.top + item.height) {
                return TapTarget.Image(resources.file(item.resource))
            }
        }
        val hit = PageRenderer.hitTest(page, x, y) ?: return null
        val block = book.chapters.getOrNull(hit.chapter)?.blocks?.getOrNull(hit.block) as? Block.Text ?: return null
        // Superscript note labels are tiny; accept taps just around them.
        val span = block.spans.firstOrNull { s ->
            s.target != null && hit.offset >= s.start - 1 && hit.offset <= s.end && (s.has(Span.LINK) || s.has(Span.NOTE_REF))
        } ?: return null
        val target = span.target!!
        return when {
            // Notes with their own body (FB2 notes, EPUB asides) open in a popup; notes that are
            // ordinary text elsewhere in the book are a jump with ↶ back, as in Apple Books.
            span.has(Span.NOTE_REF) && book.footnotes.containsKey(target) -> TapTarget.Note(book.footnoteBlocks(target))
            span.has(Span.NOTE_REF) -> parseInternalTarget(target)?.let { TapTarget.Link(it) }
            target.contains("://") || target.startsWith("mailto:") -> TapTarget.External(target)
            else -> parseInternalTarget(target)?.let { TapTarget.Link(it) }
        }
    }

    fun imageFile(resource: String): File = resources.file(resource)

    // --- Bookmarks ---

    private fun currentRange(): Pair<Position, Position?>? = state.current?.let(::rangeOf)

    /** Whether [page] holds one of [marks]; callers pass the collected list so they redraw on changes. */
    fun isBookmarked(page: Page, marks: List<BookmarkEntity>): Boolean {
        val (start, end) = rangeOf(PageRef(page.chapter, page.index)) ?: return false
        return annotations.isBookmarked(marks, start, end)
    }

    private fun rangeOf(ref: PageRef): Pair<Position, Position?>? {
        val engine = engine ?: return null
        val page = engine.page(ref) ?: return null
        val end = engine.nextRef(ref)?.let { next ->
            engine.page(next)?.start ?: Position(next.chapter)
        }
        return page.start to end
    }

    private fun refreshBookmarked() {
        val (start, end) = currentRange() ?: return
        val marked = annotations.isBookmarked(bookmarks.value, start, end)
        if (marked != state.bookmarked) update { it.copy(bookmarked = marked) }
    }

    fun toggleBookmark() {
        val (start, end) = currentRange() ?: return
        val book = book ?: return
        val existing = annotations.bookmarksInRange(bookmarks.value, start, end)
        viewModelScope.launch {
            annotations.toggleBookmark(book, start, existing)
        }
    }

    fun deleteBookmark(id: Long) = viewModelScope.launch { annotations.deleteBookmark(id) }

    // --- Selection and highlights (v0.5) ---

    val highlights: StateFlow<List<HighlightEntity>> =
        library.highlights(bookId).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Text selected with a long press; lives on the current page only, as in Apple Books. */
    var selection by mutableStateOf<TextSpan?>(null)
        private set

    fun select(span: TextSpan?) {
        selection = span?.takeUnless { it.isEmpty }
    }

    /** The saved highlight covering [position], if any. */
    fun highlightAt(position: Position): HighlightEntity? = annotations.highlightAt(position)

    /** Saved highlights that share text with [span] («Убрать» removes them). */
    fun highlightsOverlapping(span: TextSpan): List<HighlightEntity> = annotations.highlightsOverlapping(span)

    fun spanOf(h: HighlightEntity): TextSpan? = annotations.spanOf(h)

    /** Plain text of [span]; paragraphs are joined with line breaks. */
    fun textOf(span: TextSpan): String = annotations.textOf(span)

    /** Text of the paragraph around [span] for the «Выделенное» list: up to ~40 characters before, the rest after. */
    fun contextOf(span: TextSpan): Pair<String, String> = annotations.contextOf(span)

    /**
     * «Выделить»: saves [span] in the last used colour, merged with any highlight it overlaps
     * (their notes are kept); returns the new highlight's id.
     */
    suspend fun addHighlight(span: TextSpan, note: String? = null): Long = annotations.addHighlight(span, note)

    /** «Выделить» on a selection; [then] receives the new highlight's id (to show its palette). */
    fun highlight(span: TextSpan, then: (Long) -> Unit = {}) = viewModelScope.launch { then(addHighlight(span)) }

    /** «Добавить заметку» on a fresh selection: highlights it and keeps the note (an empty note keeps just the highlight). */
    fun highlightWithNote(span: TextSpan, note: String) = viewModelScope.launch { addHighlight(span, note.takeIf { it.isNotBlank() }) }

    /** Palette choice: recolours [id] and becomes the colour of new highlights. */
    fun setHighlightColor(id: Long, color: String) = viewModelScope.launch { annotations.setHighlightColor(id, color) }

    /** «Убрать»: deletes every highlight sharing text with [span]. */
    fun removeHighlights(span: TextSpan) = viewModelScope.launch { annotations.removeHighlights(span) }

    fun deleteHighlight(id: Long) = viewModelScope.launch { annotations.deleteHighlight(id) }

    fun setHighlightNote(id: Long, note: String?) = viewModelScope.launch { annotations.setHighlightNote(id, note) }

    // --- Search ---

    fun search(query: String) = bookSearch.search(query)

    fun openSearchHit(hit: SearchHit) {
        goTo(hit.position, TextRange(hit.position.chapter, hit.position.block, hit.position.offset, hit.position.offset + hit.length))
    }

    // --- Settings passthrough ---

    /** Saves an edit to this book's own settings ([book]) or to the shared ones; closing the panel never cancels it. */
    fun saveSettings(book: Boolean, change: (ReaderSettings) -> ReaderSettings) = viewModelScope.launch {
        if (book) settingsRepository.updateBookReader(bookId, change) else settingsRepository.updateReader(change)
    }

    /** The panel's guide toggle edits where the line is set: this book if it keeps its own, else shared. */
    fun setGuideLine(on: Boolean) =
        saveSettings(BookOverrides.GUIDE_LINE in ownSettings.value.values) { it.copy(guideLine = on) }

    fun shareOwnSettings() = viewModelScope.launch { settingsRepository.shareBookReader(bookId) }
    fun resetOwnSettings() = viewModelScope.launch { settingsRepository.resetBookReader(bookId) }

    /** Original book file, for «Поделиться». */
    fun bookFile(context: Context): File? =
        File(context.filesDir, "books").listFiles { f -> f.name.startsWith("$bookId.") }?.firstOrNull()

    // --- Reading sessions (kept for Reading Goals) ---

    fun onResume() {
        sessionStart = System.currentTimeMillis()
    }

    fun onPause() {
        val start = sessionStart
        if (start == 0L) return
        sessionStart = 0L
        val duration = System.currentTimeMillis() - start
        viewModelScope.launch { library.recordSession(bookId, start, duration) }
        savedPosition.value?.let { pos -> book?.let { b -> viewModelScope.launch { library.savePosition(bookId, pos, b.progressAt(pos)) } } }
    }

    override fun onCleared() {
        engine?.cancel()
    }
}
