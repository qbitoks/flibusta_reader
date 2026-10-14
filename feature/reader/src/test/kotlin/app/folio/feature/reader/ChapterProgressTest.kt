package app.folio.feature.reader

import app.folio.core.model.Block
import app.folio.core.model.Book
import app.folio.core.model.BookMetadata
import app.folio.core.model.Chapter
import app.folio.core.model.Position
import app.folio.core.model.TocEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChapterProgressTest {
    @Test fun nestedSectionEndsAtTheNextNavigationTargetWithinOnePhysicalChapter() {
        val progress = ChapterProgress(book(1, listOf(TocEntry("Part", Position.Start, listOf(
            TocEntry("First", at(20)), TocEntry("Second", at(80)),
        )))))
        val pages = Pages(listOf(listOf(at(0), at(30), at(60), at(90))))

        assertEquals("First", progress.titleAt(at(30)))
        assertEquals(1, progress.currentRowAt(at(30)))
        assertEquals(1, pages.remaining(progress, at(30), PageRef(0, 1)))
    }

    @Test fun midPageEndIncludesThatPageButExactPageStartEndExcludesIt() {
        val pages = Pages(listOf(listOf(at(0), at(30), at(60), at(90))))
        val exact = ChapterProgress(book(1, listOf(TocEntry("First", at(0)), TocEntry("Second", at(60)))))
        val midPage = ChapterProgress(book(1, listOf(TocEntry("First", at(0)), TocEntry("Second", at(80)))))

        assertEquals(1, pages.remaining(exact, at(0), PageRef(0, 0)))
        assertEquals(2, pages.remaining(midPage, at(0), PageRef(0, 0)))
        assertEquals(0, pages.remaining(exact, at(30), PageRef(0, 1)))
    }

    @Test fun sectionCanSpanPhysicalChaptersAndFinalSectionEndsAtBookEnd() {
        val progress = ChapterProgress(book(3, listOf(TocEntry("First", at(0)), TocEntry("Final", Position(2, 0, 20)))))
        val pages = Pages(List(3) { chapter -> listOf(Position(chapter), Position(chapter, 0, 50)) })

        assertEquals(3, pages.remaining(progress, at(50), PageRef(0, 1)))
        assertEquals(1, pages.remaining(progress, Position(2, 0, 20), PageRef(2, 0)))
        assertEquals(0, pages.remaining(progress, Position(2, 0, 50), PageRef(2, 1)))
    }

    @Test fun nextBoundaryIsTheNextGreaterPositionRegardlessOfHierarchyOrTreeOrder() {
        val progress = ChapterProgress(book(1, listOf(
            TocEntry("Later", at(80)),
            TocEntry("Part", at(0), listOf(TocEntry("Child", at(20)))),
        )))
        val pages = Pages(listOf(listOf(at(0), at(30), at(60), at(90))))

        assertEquals("Later", progress.titleAt(at(90)))
        assertEquals(1, pages.remaining(progress, at(30), PageRef(0, 1)))
    }

    @Test fun allLevelsAreNavigableAndDeepestDuplicateTargetWins() {
        val deep = TocEntry("Level 3", at(0))
        val progress = ChapterProgress(book(1, listOf(
            TocEntry("Level 0", at(0), listOf(TocEntry("Level 1", at(0), listOf(TocEntry("Level 2", at(0), listOf(deep)))))),
            TocEntry("Same target sibling", at(0)),
            TocEntry("Next", at(60)),
        )))

        assertEquals(listOf(0, 1, 2, 3, 0, 0), progress.rows.map { it.depth })
        assertEquals("Level 3", progress.titleAt(at(30)))
        assertEquals(3, progress.currentRowAt(at(30)))
        assertEquals(at(60), progress.sectionAt(at(30))?.end)
    }

    @Test fun textBeforeTheFirstTocTargetHasNoCurrentChapterOrHighlightedRow() {
        val progress = ChapterProgress(book(1, listOf(TocEntry("First", at(60)))))
        val pages = Pages(listOf(listOf(at(0), at(30), at(60), at(90))))

        assertNull(progress.titleAt(at(30)))
        assertNull(progress.currentRowAt(at(30)))
        assertNull(pages.remaining(progress, at(30), PageRef(0, 1)))
    }

    @Test fun emptyTocUsesPhysicalChapterTitlesAndBoundaries() {
        val progress = ChapterProgress(book(2, emptyList()))
        val pages = Pages(List(2) { chapter -> listOf(Position(chapter), Position(chapter, 0, 50)) })

        assertEquals(listOf("Physical 0", "Physical 1"), progress.rows.map { it.entry.title })
        assertEquals("Physical 1", progress.titleAt(Position(1, 0, 20)))
        assertEquals(1, progress.currentRowAt(Position(1, 0, 20)))
        assertEquals(1, pages.remaining(progress, Position.Start, PageRef(0, 0)))
    }

    @Test fun crossingATocTargetOnTheSamePageUpdatesChapterTitleAndCurrentRow() {
        val progress = ChapterProgress(book(1, listOf(TocEntry("First", at(0)), TocEntry("Second", at(20)), TocEntry("Third", at(80)))))
        val pages = Pages(listOf(listOf(at(0), at(30), at(60), at(90))))
        val ref = PageRef(0, 0)

        assertEquals("First", progress.titleAt(at(10)))
        assertEquals(0, progress.currentRowAt(at(10)))
        assertEquals(0, pages.remaining(progress, at(10), ref))
        assertEquals("Second", progress.titleAt(at(20)))
        assertEquals(1, progress.currentRowAt(at(20)))
        assertEquals(2, pages.remaining(progress, at(20), ref))
    }

    @Test fun unknownEndpointPaginationStaysUnknownUntilItsCachedPageStartsAreReady() {
        val progress = ChapterProgress(book(2, listOf(TocEntry("First", at(0)), TocEntry("Next", Position(1, 0, 20)))))
        val pages = Pages(listOf(listOf(at(0), at(50)), listOf(Position(1), Position(1, 0, 50))))

        assertNull(pages.remaining(progress, at(0), PageRef(0, 0), knownChapters = setOf(0)))
        assertNull(pages.remaining(progress, at(0), PageRef(0, 0), offsetsReady = false))
        assertEquals(2, pages.remaining(progress, at(0), PageRef(0, 0)))
    }

    @Test fun localSectionCanBeCountedBeforeGlobalPaginationFinishes() {
        val progress = ChapterProgress(book(2, listOf(TocEntry("First", at(0)), TocEntry("Next", at(80)))))
        val pages = Pages(listOf(listOf(at(0), at(30), at(60), at(90)), listOf(Position(1))))

        assertEquals(2, pages.remaining(progress, at(0), PageRef(0, 0), offsetsReady = false))
    }

    private fun at(offset: Int) = Position(0, 0, offset)

    private fun book(chapterCount: Int, toc: List<TocEntry>) = Book(
        BookMetadata("Book", emptyList()),
        List(chapterCount) { Chapter("Physical $it", listOf(Block.Text("x".repeat(100)))) },
        toc,
        emptyMap(),
    )

    private class Pages(private val starts: List<List<Position>>) {
        private val offsets = IntArray(starts.size + 1).also { result ->
            starts.forEachIndexed { chapter, pages -> result[chapter + 1] = result[chapter] + pages.size }
        }

        fun remaining(
            progress: ChapterProgress,
            position: Position,
            current: PageRef,
            knownChapters: Set<Int> = starts.indices.toSet(),
            offsetsReady: Boolean = true,
        ): Int? = progress.remainingPages(
            position, current, offsets.takeIf { offsetsReady },
            pageIndexOf = { target ->
                starts.getOrNull(target.chapter)?.takeIf { target.chapter in knownChapters }
                    ?.indexOfLast { it <= target }?.coerceAtLeast(0)
            },
            pageStartOf = { target ->
                starts.getOrNull(target.chapter)?.takeIf { target.chapter in knownChapters }
                    ?.lastOrNull { it <= target }
            },
            pageCountOf = { chapter -> starts.getOrNull(chapter)?.takeIf { chapter in knownChapters }?.size },
        )
    }
}
