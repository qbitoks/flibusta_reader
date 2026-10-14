package app.folio.feature.reader

import app.folio.core.model.Book
import app.folio.core.model.Position
import app.folio.core.model.TocEntry

internal data class ChapterTocRow(val entry: TocEntry, val depth: Int)
internal data class ChapterSection(val title: String?, val start: Position, val end: Position, val row: Int?)

internal class ChapterProgress(private val book: Book) {
    private val bookEnd = Position(book.chapters.size)

    val rows: List<ChapterTocRow> = buildList {
        fun flatten(entries: List<TocEntry>, depth: Int) {
            entries.forEach {
                add(ChapterTocRow(it, depth))
                flatten(it.children, depth + 1)
            }
        }
        val toc = book.toc.ifEmpty {
            book.chapters.mapIndexed { index, chapter ->
                TocEntry(chapter.title ?: book.metadata.title, Position(index))
            }
        }
        flatten(toc, 0)
    }

    /** Navigation order can differ from source order; deeper entries win a shared target. */
    private val sections: List<ChapterSection> = rows.withIndex()
        .sortedWith(compareBy({ it.value.entry.position }, { it.value.depth }, { it.index }))
        .groupBy { it.value.entry.position }
        .values.map { it.last() }
        .let { targets ->
            buildList {
                val first = targets.firstOrNull()?.value?.entry?.position
                if (first != null && first > Position.Start) {
                    add(ChapterSection(null, Position.Start, first, null))
                }
                targets.forEachIndexed { index, target ->
                    val entry = target.value.entry
                    add(ChapterSection(entry.title, entry.position, targets.getOrNull(index + 1)?.value?.entry?.position ?: bookEnd, target.index))
                }
            }
        }

    private val starts = sections.map { it.start }

    fun currentRowAt(position: Position): Int? = sectionAt(position)?.row

    fun titleAt(position: Position): String? = sectionAt(position)?.title

    fun sectionAt(position: Position): ChapterSection? {
        if (position < Position.Start || position >= bookEnd) return null
        val index = starts.binarySearch(position).let { if (it >= 0) it else -it - 2 }
        return sections.getOrNull(index)
    }

    /** Counts pages after the current one that intersect the section's [start, end) range. */
    fun remainingPages(
        position: Position,
        current: PageRef,
        pageOffsets: IntArray?,
        pageIndexOf: (Position) -> Int?,
        pageStartOf: (Position) -> Position?,
        pageCountOf: (Int) -> Int?,
    ): Int? {
        val end = sectionAt(position)?.takeIf { it.row != null }?.end ?: return null
        fun firstExcludedPage(): Int? {
            val index = pageIndexOf(end) ?: return null
            val start = pageStartOf(end) ?: return null
            return index + if (start < end) 1 else 0
        }
        val remaining = when {
            end.chapter == current.chapter -> (firstExcludedPage() ?: return null) - current.index - 1
            end == Position(current.chapter + 1) -> {
                val count = pageCountOf(current.chapter)
                    ?: pageOffsets?.let { it[current.chapter + 1] - it[current.chapter] }
                    ?: return null
                count - current.index - 1
            }
            else -> {
                val offsets = pageOffsets ?: return null
                val endPage = if (end == bookEnd) offsets.last() else offsets[end.chapter] + (firstExcludedPage() ?: return null)
                endPage - offsets[current.chapter] - current.index - 1
            }
        }
        return remaining.coerceAtLeast(0)
    }
}
