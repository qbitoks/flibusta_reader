package app.folio.feature.reader

import app.folio.core.model.Block
import app.folio.core.model.Book
import app.folio.core.model.BookMetadata
import app.folio.core.model.Chapter
import app.folio.core.model.Position
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderProgressTest {
    private fun book(vararg chapters: List<Block>) = Book(
        BookMetadata("Book", emptyList()), chapters.map { Chapter(null, it) }, emptyList(), emptyMap(),
    )

    @Test fun chapterBoundaryResolvesToTheNextChapter() {
        val book = book(listOf(Block.Text("abcd")), listOf(Block.Text("efgh")))
        assertEquals(Position(1), book.readerPositionAtProgress(0.5f))
        assertEquals(Position(1, 0, 2), book.readerPositionAtProgress(0.75f))
    }

    @Test fun endpointsClampToReadableContentEvenWithEmptyChapters() {
        val book = book(emptyList(), listOf(Block.Text(""), Block.Text("abcd")), emptyList())
        assertEquals(Position(1, 1), book.readerPositionAtProgress(-1f))
        assertEquals(Position(1, 1, 3), book.readerPositionAtProgress(1f))
        assertEquals(Position(1, 1, 3), book.readerPositionAtProgress(2f))
    }

    @Test fun imagesAndSeparatorsUseTheSameWeightsAsBookProgress() {
        val book = book(listOf(Block.Image("cover"), Block.Separator, Block.Text("ab")))
        assertEquals(Position(0, 1), book.readerPositionAtProgress(0.25f))
        assertEquals(Position(0, 2), book.readerPositionAtProgress(0.5f))
    }

    @Test fun emptyBooksAndNonFiniteProgressHaveNoTarget() {
        assertNull(book(emptyList()).readerPositionAtProgress(0.5f))
        assertNull(book(listOf(Block.Text("ab"))).readerPositionAtProgress(Float.NaN))
        assertNull(book(listOf(Block.Text("ab"))).readerPositionAtProgress(Float.POSITIVE_INFINITY))
    }
}
