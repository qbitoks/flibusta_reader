package app.folio.core.format

import app.folio.core.format.text.TextParser
import app.folio.core.model.Block
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.charset.Charset

class TextParserTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun texts(bytes: ByteArray) = TextParser.parse(bytes).chapters.flatMap { it.blocks }.map { (it as Block.Text).text }

    @Test
    fun detectsAnyTextFileButNotBinary() {
        val conf = tmp.newFile("nginx.conf").apply { writeText("server {\n    listen 443;\n}\n") }
        val noExt = tmp.newFile("README").apply { writeText("Привет") }
        val png = tmp.newFile("a.bin").apply { writeBytes(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 0, 0, 13)) }
        assertEquals(BookFormat.Text, BookParser.detect(conf))
        assertEquals(BookFormat.Text, BookParser.detect(noExt))
        assertNull(BookParser.detect(png))
        assertNull(BookParser.detect(tmp.newFile("empty")))
    }

    @Test
    fun proseJoinsWrappedLines() {
        val text = "Первая строка абзаца,\nего продолжение.\n\nВторой абзац.\n    Третий с отступом.\n"
        assertEquals(listOf("Первая строка абзаца, его продолжение.", "Второй абзац.", "Третий с отступом."), texts(text.toByteArray()))
    }

    @Test
    fun configKeepsLinesAndIndentation() {
        val text = "[main]\nkey = value\nother: 1\n\nserver {\n    listen 443;\n}\n"
        assertEquals(listOf("[main]\nkey = value\nother: 1", "server {\n    listen 443;\n}"), texts(text.toByteArray()))
    }

    @Test
    fun decodesWindows1251AndBom() {
        assertEquals(listOf("Война и мир."), texts("Война и мир.".toByteArray(Charset.forName("windows-1251"))))
        assertEquals(listOf("Текст."), texts(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "Текст.".toByteArray()))
        val utf16 = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "Текст.".toByteArray(Charsets.UTF_16LE)
        assertTrue(TextParser.looksLikeText(utf16, utf16.size))
        assertEquals(listOf("Текст."), texts(utf16))
    }

    @Test
    fun longTextIsSplitIntoChapters() {
        val text = (1..5000).joinToString("\n\n") { "Абзац номер $it, в котором есть немного текста." }
        val book = TextParser.parse(text.toByteArray())
        assertTrue(book.chapters.size > 1)
        assertEquals(book.chapters.size, book.toc.size)
    }
}
