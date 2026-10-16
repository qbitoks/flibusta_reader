package app.folio.core.speech

import app.folio.core.model.Block
import app.folio.core.model.Book
import app.folio.core.model.BookMetadata
import app.folio.core.model.Chapter
import app.folio.core.model.Position
import app.folio.core.model.TextKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class SpeechTextTest {
    private val book = Book(
        BookMetadata("Книга", emptyList(), language = "ru"),
        listOf(
            Chapter("Один", listOf(
                Block.Text("Глава первая", kind = TextKind.Heading1),
                Block.Text("Было утро. Шёл дождь!  Кто там?"),
                Block.Image("cover"),
                Block.Text("val x = 1", kind = TextKind.Preformatted),
            )),
            Chapter("Два", listOf(Block.Separator, Block.Text("Конец."))),
        ),
        emptyList(), emptyMap(),
    )

    @Test
    fun `phrases cover the book in order with exact positions`() {
        val all = book.utterancesFrom(Position.Start).toList()
        assertEquals(listOf("Глава первая", "Было утро.", "Шёл дождь!", "Кто там?", "Конец."), all.map { it.text })
        val rain = all[2]
        assertEquals(Position(0, 1, 11), rain.start)
        assertEquals(Position(0, 1, 21), rain.end)
        assertEquals(Position(1, 1, 0), all.last().start)
    }

    @Test
    fun `start inside a sentence begins at that sentence`() {
        val first = book.utterancesFrom(Position(0, 1, 14)).first()
        assertEquals("Шёл дождь!", first.text)
    }

    @Test
    fun `previous phrase crosses blocks and chapters`() {
        val all = book.utterancesFrom(Position.Start).toList()
        assertEquals("Глава первая", previousUtterance(book, all[1])?.text)
        assertEquals("Кто там?", previousUtterance(book, all.last())?.text)
        assertNull(previousUtterance(book, all.first()))
    }

    @Test
    fun `long sentences are split at spaces`() {
        val text = (1..400).joinToString(" ") { "слово" }
        val ranges = sentenceRanges(text)
        assertTrue(ranges.size > 1)
        assertTrue(ranges.all { it.last - it.first + 1 <= MAX_UTTERANCE })
        assertEquals(text, ranges.joinToString(" ") { text.substring(it.first, it.last + 1) })
    }

    @Test
    fun `book language picks the locale`() {
        assertEquals("ru", book.speechLocale().language)
        val none = Book(BookMetadata("x", emptyList()), emptyList(), emptyList(), emptyMap())
        assertEquals(Locale.getDefault(), none.speechLocale())
    }
}
