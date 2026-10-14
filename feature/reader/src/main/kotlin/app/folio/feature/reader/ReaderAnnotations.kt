package app.folio.feature.reader

import app.folio.core.data.db.BookmarkEntity
import app.folio.core.data.db.HighlightEntity
import app.folio.core.data.library.LibraryRepository
import app.folio.core.data.settings.SettingsRepository
import app.folio.core.layout.TextSpan
import app.folio.core.model.Block
import app.folio.core.model.Book
import app.folio.core.model.Position
import app.folio.core.model.progressAt

internal class ReaderAnnotations(
    private val bookId: String,
    private val library: LibraryRepository,
    private val settingsRepository: SettingsRepository,
    private val currentBook: () -> Book?,
    private val savedHighlights: () -> List<HighlightEntity>,
    private val highlightColor: () -> String,
    private val chapterTitleAt: (Position) -> String?,
) {
    fun bookmarksInRange(bookmarks: List<BookmarkEntity>, start: Position, end: Position?): List<BookmarkEntity> = bookmarks.filter { bm ->
        val p = Position.decode(bm.position) ?: return@filter false
        p >= start && (end == null || p < end)
    }

    fun isBookmarked(bookmarks: List<BookmarkEntity>, start: Position, end: Position?): Boolean = bookmarks.any { bm ->
        val p = Position.decode(bm.position) ?: return@any false
        p >= start && (end == null || p < end)
    }

    suspend fun toggleBookmark(book: Book, start: Position, existing: List<BookmarkEntity>) {
        if (existing.isNotEmpty()) {
            existing.forEach { library.deleteBookmark(it.id) }
        } else {
            library.addBookmark(
                BookmarkEntity(
                    bookId = bookId,
                    position = start.encode(),
                    chapterTitle = chapterTitleAt(start),
                    snippet = snippetAt(book, start),
                    progress = book.progressAt(start),
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    suspend fun deleteBookmark(id: Long) = library.deleteBookmark(id)

    fun highlightAt(position: Position): HighlightEntity? = savedHighlights().firstOrNull { h ->
        val span = spanOf(h) ?: return@firstOrNull false
        position in span
    }

    fun highlightsOverlapping(span: TextSpan): List<HighlightEntity> = savedHighlights().filter { h ->
        val other = spanOf(h) ?: return@filter false
        other.start < span.end && span.start < other.end
    }

    fun spanOf(h: HighlightEntity): TextSpan? {
        val start = Position.decode(h.start) ?: return null
        val end = Position.decode(h.end) ?: return null
        return TextSpan(start, end)
    }

    fun textOf(span: TextSpan): String {
        val book = currentBook() ?: return ""
        val out = StringBuilder()
        for (c in span.start.chapter..span.end.chapter) {
            val blocks = book.chapters.getOrNull(c)?.blocks ?: continue
            val first = if (c == span.start.chapter) span.start.block else 0
            val last = if (c == span.end.chapter) span.end.block else blocks.lastIndex
            for (b in first..minOf(last, blocks.lastIndex)) {
                val text = (blocks[b] as? Block.Text)?.text ?: continue
                val from = if (c == span.start.chapter && b == span.start.block) span.start.offset else 0
                val to = if (c == span.end.chapter && b == span.end.block) span.end.offset else text.length
                if (to <= from) continue
                if (out.isNotEmpty()) out.append('\n')
                out.append(text, from.coerceIn(0, text.length), to.coerceIn(0, text.length))
            }
        }
        return out.toString().trim()
    }

    fun contextOf(span: TextSpan): Pair<String, String> {
        val blocks = currentBook()?.chapters?.getOrNull(span.start.chapter)?.blocks ?: return "" to ""
        val first = (blocks.getOrNull(span.start.block) as? Block.Text)?.text.orEmpty()
        val before = first.substring(0, span.start.offset.coerceIn(0, first.length))
        val last = (blocks.getOrNull(span.end.block) as? Block.Text)?.text.orEmpty()
        val after = if (span.end.chapter == span.start.chapter) last.substring(span.end.offset.coerceIn(0, last.length)) else ""
        val trimmed = if (before.length > 40) "…" + before.takeLast(40).substringAfter(' ') else before
        return trimmed to after.take(200)
    }

    suspend fun addHighlight(span: TextSpan, note: String? = null): Long {
        val book = currentBook() ?: return -1
        val overlapping = highlightsOverlapping(span)
        val merged = overlapping.mapNotNull(::spanOf).fold(span) { acc, s -> TextSpan(minOf(acc.start, s.start), maxOf(acc.end, s.end)) }
        val notes = (overlapping.mapNotNull { it.note } + listOfNotNull(note)).distinct().joinToString("\n\n").ifBlank { null }
        overlapping.forEach { library.deleteHighlight(it.id) }
        return library.addHighlight(
            HighlightEntity(
                bookId = bookId,
                start = merged.start.encode(),
                end = merged.end.encode(),
                text = textOf(merged),
                color = overlapping.firstOrNull()?.color ?: highlightColor(),
                note = notes,
                progress = book.progressAt(merged.start),
                createdAt = overlapping.minOfOrNull { it.createdAt } ?: System.currentTimeMillis(),
            ),
        )
    }

    suspend fun setHighlightColor(id: Long, color: String) {
        library.setHighlightColor(id, color)
        settingsRepository.setHighlightColor(color)
    }

    suspend fun removeHighlights(span: TextSpan) {
        highlightsOverlapping(span).forEach { library.deleteHighlight(it.id) }
    }

    suspend fun deleteHighlight(id: Long) = library.deleteHighlight(id)

    suspend fun setHighlightNote(id: Long, note: String?) = library.setHighlightNote(id, note?.takeIf { it.isNotBlank() })

    private fun snippetAt(book: Book, p: Position): String {
        val sb = StringBuilder()
        val blocks = book.chapters.getOrNull(p.chapter)?.blocks ?: return ""
        var i = p.block
        var offset = p.offset
        while (i < blocks.size && sb.length < 140) {
            val b = blocks[i]
            if (b is Block.Text) {
                if (sb.isNotEmpty()) sb.append(' ')
                sb.append(b.text, offset.coerceAtMost(b.text.length), b.text.length)
            }
            offset = 0
            i++
        }
        return sb.toString().replace('\n', ' ').take(140).trim()
    }
}
