package app.folio.feature.reader

import app.folio.core.layout.ChapterPages
import app.folio.core.layout.Page
import app.folio.core.layout.Paginator
import app.folio.core.model.Book
import app.folio.core.model.Position
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import java.util.concurrent.ConcurrentHashMap

/** A page addressed by chapter and index within the chapter. */
data class PageRef(val chapter: Int, val index: Int)

/**
 * Lazily paginates chapters for one [Paginator] (i.e. one set of reading settings).
 *
 * Display pagination runs on a single background thread in request order, so the chapter
 * being read is always laid out first and neighbours right after. Global page numbers are
 * computed separately on another thread and appear once the whole book is counted.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PaginationEngine(
    private val book: Book,
    val paginator: Paginator,
    private val counter: Paginator,
    private val scope: CoroutineScope,
) {
    private val layoutDispatcher = Dispatchers.Default.limitedParallelism(1)
    private val countDispatcher = Dispatchers.Default.limitedParallelism(1)
    private val chapters = ConcurrentHashMap<Int, Deferred<ChapterPages>>()

    /**
     * Start of every page, per chapter. Unlike [chapters] it is never evicted, so page numbers
     * stay known for far-away positions (↶ / ↷ marks, contents, bookmarks).
     */
    private val pageStarts = ConcurrentHashMap<Int, List<Position>>()

    /** First global page index of each chapter, plus the total; null until counting finishes. */
    private val _pageOffsets = MutableStateFlow<IntArray?>(null)
    val pageOffsets: StateFlow<IntArray?> = _pageOffsets

    val chapterCount: Int get() = book.chapters.size

    fun request(chapter: Int): Deferred<ChapterPages> = chapters.getOrPut(chapter) {
        scope.async(layoutDispatcher) {
            paginator.paginate(chapter, book.chapters[chapter]).also { pageStarts[chapter] = it.pages.map(Page::start) }
        }
    }

    fun peek(chapter: Int): ChapterPages? = chapters[chapter]?.takeIf { it.isCompleted && !it.isCancelled }?.getCompleted()

    suspend fun pages(chapter: Int): ChapterPages = request(chapter).await()

    /** Index of the page containing [position] within its chapter; null if the chapter was never laid out. */
    fun pageIndexOf(position: Position): Int? {
        peek(position.chapter)?.let { return it.pageIndexOf(position) }
        val starts = pageStarts[position.chapter] ?: return null
        val i = starts.binarySearch(position)
        return if (i >= 0) i else (-i - 2).coerceAtLeast(0)
    }

    /** Cached start of the page containing [position], including evicted chapters. */
    fun pageStartOf(position: Position): Position? {
        val index = pageIndexOf(position) ?: return null
        return pageStarts[position.chapter]?.getOrNull(index)
    }

    fun page(ref: PageRef): Page? = peek(ref.chapter)?.pages?.getOrNull(ref.index)

    fun prefetchAround(chapter: Int) {
        if (chapter + 1 < chapterCount) request(chapter + 1)
        if (chapter - 1 >= 0) request(chapter - 1)
        // Keep memory bounded: only a small window of laid-out chapters stays alive.
        chapters.keys.filter { it < chapter - 2 || it > chapter + 2 }.forEach { chapters.remove(it)?.cancel() }
    }

    /** Reference to the page after [ref], crossing chapters; null at the end of the book. */
    fun nextRef(ref: PageRef): PageRef? {
        val pages = peek(ref.chapter) ?: return null
        if (ref.index + 1 < pages.pages.size) return PageRef(ref.chapter, ref.index + 1)
        return if (ref.chapter + 1 < chapterCount) PageRef(ref.chapter + 1, 0) else null
    }

    /** Reference to the page before [ref]; for a previous chapter its last page (needs it laid out). */
    fun prevRef(ref: PageRef): PageRef? {
        if (ref.index > 0) return PageRef(ref.chapter, ref.index - 1)
        if (ref.chapter == 0) return null
        val prev = peek(ref.chapter - 1) ?: return PageRef(ref.chapter - 1, LAST)
        return PageRef(ref.chapter - 1, prev.pages.lastIndex)
    }

    fun startCounting() {
        scope.launch(countDispatcher) {
            val offsets = IntArray(chapterCount + 1)
            var total = 0
            for (i in 0 until chapterCount) {
                offsets[i] = total
                total += countOf(i)
                yield()
            }
            offsets[chapterCount] = total
            _pageOffsets.value = offsets
        }
    }

    private val counted = HashMap<Int, Int>()

    private fun countOf(chapter: Int): Int = counted.getOrPut(chapter) {
        peek(chapter)?.pages?.size ?: counter.paginate(chapter, book.chapters[chapter]).pages
            .also { pages -> pageStarts.putIfAbsent(chapter, pages.map(Page::start)) }.size
    }

    fun cancel() {
        chapters.values.forEach { it.cancel() }
        chapters.clear()
    }

    companion object {
        /** Placeholder index meaning "last page of that chapter", resolved once it is laid out. */
        const val LAST = Int.MAX_VALUE
    }
}
