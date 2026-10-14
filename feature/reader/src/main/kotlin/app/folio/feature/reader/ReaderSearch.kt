package app.folio.feature.reader

import app.folio.core.model.Block
import app.folio.core.model.Book
import app.folio.core.model.Position
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SearchHit(
    val position: Position,
    val chapterTitle: String?,
    val snippet: String,
    val matchStart: Int,
    val matchLength: Int,
    val length: Int,
)

data class SearchState(val query: String = "", val results: List<SearchHit> = emptyList(), val searching: Boolean = false)

internal class ReaderSearch(
    private val scope: CoroutineScope,
    private val currentBook: () -> Book?,
    private val chapterTitleAt: (Position) -> String?,
) {
    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()
    private var job: Job? = null

    fun search(query: String) {
        job?.cancel()
        val book = currentBook() ?: return
        val q = normalizeSearchQuery(query)
        if (q.length < 2) {
            _state.value = SearchState(query = query)
            return
        }
        _state.value = SearchState(query = query, searching = true)
        job = scope.launch {
            val hits = withContext(Dispatchers.Default) { searchBook(book, q, chapterTitleAt) }
            _state.value = SearchState(query = query, results = hits, searching = false)
        }
    }
}

internal fun normalizeSearchQuery(query: String): String = normalizeSearchText(query.trim())

internal fun searchBook(book: Book, query: String, chapterTitleAt: (Position) -> String?): List<SearchHit> {
    val result = ArrayList<SearchHit>()
    outer@ for ((c, chapter) in book.chapters.withIndex()) {
        val title = chapter.title
        for ((b, block) in chapter.blocks.withIndex()) {
            if (block !is Block.Text) continue
            val text = normalizeSearchText(block.text)
            var from = 0
            while (true) {
                val at = text.indexOf(query, from)
                if (at < 0) break
                val start = (at - 40).coerceAtLeast(0).let { s -> block.text.lastIndexOf(' ', s).takeIf { it >= 0 && at - it < 60 }?.plus(1) ?: s }
                val end = (at + query.length + 70).coerceAtMost(block.text.length)
                val snippet = block.text.substring(start, end).replace('\n', ' ')
                result += SearchHit(Position(c, b, at), chapterTitleAt(Position(c, b, at)) ?: title, snippet, at - start, query.length, query.length)
                if (result.size >= MAX_HITS) break@outer
                from = at + query.length
            }
        }
    }
    return result
}

private fun normalizeSearchText(text: String): String = text.lowercase().replace('ё', 'е')

private const val MAX_HITS = 500
