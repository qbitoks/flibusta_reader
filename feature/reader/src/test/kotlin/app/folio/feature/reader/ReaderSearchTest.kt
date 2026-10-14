package app.folio.feature.reader

import app.folio.core.model.Block
import app.folio.core.model.Book
import app.folio.core.model.BookMetadata
import app.folio.core.model.Chapter
import app.folio.core.model.Position
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import kotlin.coroutines.CoroutineContext

class ReaderSearchTest {
    @Test fun ignoresCaseAndYoWhileKeepingOriginalTextAndOffsets() {
        val hits = searchBook(book(Chapter("Глава", listOf(Block.Text("Вот ЁЖИК и ёжик.")))), normalizeSearchQuery("  ёЖиК  ")) { null }

        assertEquals(listOf(Position(0, 0, 4), Position(0, 0, 11)), hits.map { it.position })
        assertEquals("Вот ЁЖИК и ёжик.", hits.first().snippet)
        assertEquals(4, hits.first().matchStart)
        assertEquals(4, hits.first().matchLength)
        assertEquals(4, hits.first().length)
    }

    @Test fun keepsBlockAndChapterOrderAndSkipsOverlappingMatches() {
        val hits = searchBook(
            book(
                Chapter("First", listOf(Block.Image("cover"), Block.Text("aaaaaa"), Block.Separator, Block.Text("aaa"))),
                Chapter("Second", listOf(Block.Text("aaa"))),
            ),
            "aaa",
        ) { null }

        assertEquals(listOf(Position(0, 1, 0), Position(0, 1, 3), Position(0, 3, 0), Position(1, 0, 0)), hits.map { it.position })
    }

    @Test fun startsSnippetAtNearbyWordBoundaryAndFlattensLineBreaks() {
        val prefix = "zero one two three four five six seven eight nine "
        val suffix = "\n" + "z".repeat(100)
        val text = prefix + "match" + suffix
        val hit = searchBook(book(Chapter(null, listOf(Block.Text(text)))), "match") { null }.single()

        assertEquals("two three four five six seven eight nine match " + "z".repeat(69), hit.snippet)
        assertEquals(prefix.length - "zero one ".length, hit.matchStart)
        assertEquals(Position(0, 0, prefix.length), hit.position)
    }

    @Test fun retainsNearbySpacesWhenSnippetHasNoEarlierWordBoundary() {
        val hit = searchBook(book(Chapter(null, listOf(Block.Text("  match  ")))), "match") { null }.single()

        assertEquals(" match  ", hit.snippet)
        assertEquals(1, hit.matchStart)
    }

    @Test fun usesPositionTitleAndFallsBackToChapterTitle() {
        val hits = searchBook(book(Chapter("Chapter", listOf(Block.Text("hit hit")))), "hit") { position ->
            if (position.offset == 0) "Contents title" else null
        }

        assertEquals(listOf("Contents title", "Chapter"), hits.map { it.chapterTitle })
    }

    @Test fun stopsAtFiveHundredHitsAcrossChapters() {
        val hits = searchBook(
            book(Chapter("First", listOf(Block.Text("hit ".repeat(499)))), Chapter("Second", listOf(Block.Text("hit hit hit")))),
            "hit",
        ) { null }

        assertEquals(500, hits.size)
        assertEquals(Position(1, 0, 0), hits.last().position)
    }

    @Test fun publishesOriginalQueryAndFinishedResults() = runBlocking {
        val search = ReaderSearch(this, { book(Chapter("Глава", listOf(Block.Text("Ёжик")))) }) { null }
        search.search("  ёжик  ")

        val result = withTimeout(5_000) { search.state.first { !it.searching } }
        assertEquals("  ёжик  ", result.query)
        assertEquals(Position.Start, result.results.single().position)
    }

    @Test fun shortQueryCancelsQueuedSearchAndClearsResults() {
        val dispatcher = PausedDispatcher()
        val scope = CoroutineScope(Job() + dispatcher)
        try {
            val search = ReaderSearch(scope, { book(Chapter(null, listOf(Block.Text("match")))) }) { null }
            search.search("match")
            search.search(" ё ")
            dispatcher.runPending()

            assertEquals(SearchState(query = " ё "), search.state.value)
            assertFalse(search.state.value.searching)
        } finally {
            scope.cancel()
        }
    }

    @Test fun leavesSearchStateUntouchedUntilBookIsLoaded() {
        val scope = CoroutineScope(Job())
        try {
            val search = ReaderSearch(scope, { null }) { null }
            search.search("match")

            assertEquals(SearchState(), search.state.value)
        } finally {
            scope.cancel()
        }
    }

    private fun book(vararg chapters: Chapter) = Book(BookMetadata("Book", emptyList()), chapters.toList(), emptyList(), emptyMap())

    private class PausedDispatcher : CoroutineDispatcher() {
        private val pending = ArrayDeque<Runnable>()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            pending.addLast(block)
        }

        fun runPending() {
            while (pending.isNotEmpty()) pending.removeFirst().run()
        }
    }
}
