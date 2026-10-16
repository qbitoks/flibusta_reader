package app.folio.core.format

import app.folio.core.format.mobi.MobiParser
import app.folio.core.model.Block
import app.folio.core.model.Book
import app.folio.core.model.Span
import app.folio.core.model.TextKind
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MoreFormatsTest {
    @get:Rule val tmp = TemporaryFolder()

    private val resources = HashMap<String, ByteArray>()

    private fun open(file: File, expected: BookFormat): Book {
        assertEquals(expected, BookParser.detect(file))
        return BookParser.parse(file, expected) { id, bytes -> resources[id] = bytes }
    }

    private fun Book.texts() = chapters.flatMap { it.blocks }.filterIsInstance<Block.Text>()

    private fun zip(name: String, vararg entries: Pair<String, ByteArray>) = tmp.newFile(name).apply {
        ZipOutputStream(outputStream()).use { z -> entries.forEach { (n, b) -> z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() } }
    }

    @Test
    fun zippedTextFromCatalog() {
        val book = open(zip("book.txt.zip", "Книга.txt" to "Первый абзац.\n\nВторой абзац.".toByteArray()), BookFormat.TextZip)
        assertEquals(listOf("Первый абзац.", "Второй абзац."), book.texts().map { it.text })
    }

    @Test
    fun zippedHtmlWithHeadingsImagesAndNotes() {
        val html = """
            <html><head><meta charset="windows-1251"><title>Повесть</title></head><body>
            <h1>Глава 1</h1><p>Текст<a href="#n1"><sup>1</sup></a>.</p><img src="img/pic.jpg">
            <h1>Глава 2</h1><p><b>Жирный</b> конец.</p>
            <aside epub:type="footnote" id="n1"><p>Сноска.</p></aside>
            </body></html>
        """.trimIndent().toByteArray(charset("windows-1251"))
        val book = open(zip("book.html.zip", "book/index.html" to html, "book/img/pic.jpg" to byteArrayOf(1, 2, 3)), BookFormat.HtmlZip)
        assertEquals("Повесть", book.metadata.title)
        assertEquals(listOf("Глава 1", "Глава 2"), book.toc.map { it.title })
        assertEquals(2, book.chapters.size)
        assertTrue(book.chapters[0].blocks.any { it is Block.Image && it.resource == "book/img/pic.jpg" })
        assertArrayEquals(byteArrayOf(1, 2, 3), resources["book/img/pic.jpg"])
        val note = book.texts().first { it.text.startsWith("Текст") }.spans.first { it.has(Span.NOTE_REF) }
        assertTrue(book.footnotes.containsKey(note.target))
    }

    @Test
    fun plainHtmlFile() {
        val file = tmp.newFile("page").apply { writeText("<!DOCTYPE html><html><body><p>Привет</p></body></html>") }
        assertEquals(listOf("Привет"), open(file, BookFormat.Html).texts().map { it.text })
    }

    @Test
    fun rtfWithCyrillicStylesAndInfo() {
        val rtf = """{\rtf1\ansi\ansicpg1251{\fonttbl{\f0\fcharset204 Times;}}{\info{\title Мой рассказ}{\author Иван}}""" +
            """{\pard\outlinelevel0\b Глава\b0\par}""" +
            """{\pard\f0 \'cf\'f0\'e8\'e2\'e5\'f2, {\i ${'\\'}u1084?${'\\'}u1080?${'\\'}u1088?}!\par}""" +
            """{\*\generator Word;}{\pict 0123abcd}}"""
        val book = open(tmp.newFile("story.rtf").apply { writeBytes(rtf.toByteArray(charset("windows-1251"))) }, BookFormat.Rtf)
        assertEquals("Мой рассказ", book.metadata.title)
        assertEquals(listOf("Иван"), book.metadata.authors)
        val texts = book.texts()
        assertEquals(listOf("Глава", "Привет, мир!"), texts.map { it.text })
        assertEquals(TextKind.Heading1, texts[0].kind)
        val italic = texts[1].spans.single { it.has(Span.ITALIC) }
        assertEquals("мир", texts[1].text.substring(italic.start, italic.end))
    }

    @Test
    fun zippedRtf() {
        val book = open(zip("b.rtf.zip", "b.rtf" to """{\rtf1\ansi Hello\par}""".toByteArray()), BookFormat.RtfZip)
        assertEquals(listOf("Hello"), book.texts().map { it.text })
    }

    @Test
    fun docxWithHeadingsStylesAndFootnotes() {
        val w = """xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main""""
        val document = """<w:document $w><w:body>
            <w:p><w:pPr><w:pStyle w:val="H1"/></w:pPr><w:r><w:t>Начало</w:t></w:r></w:p>
            <w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:t xml:space="preserve">Обычный </w:t></w:r><w:r><w:rPr><w:b/></w:rPr><w:t>жирный</w:t></w:r><w:r><w:footnoteReference w:id="1"/></w:r></w:p>
            </w:body></w:document>"""
        val styles = """<w:styles $w><w:style w:styleId="H1"><w:name w:val="heading 1"/></w:style></w:styles>"""
        val notes = """<w:footnotes $w><w:footnote w:type="separator" w:id="0"/><w:footnote w:id="1"><w:p><w:r><w:t>Примечание</w:t></w:r></w:p></w:footnote></w:footnotes>"""
        val core = """<cp:coreProperties xmlns:cp="c" xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>Доклад</dc:title><dc:creator>Анна</dc:creator></cp:coreProperties>"""
        val book = open(
            zip(
                "report.docx",
                "word/document.xml" to document.toByteArray(),
                "word/styles.xml" to styles.toByteArray(),
                "word/footnotes.xml" to notes.toByteArray(),
                "docProps/core.xml" to core.toByteArray(),
            ),
            BookFormat.Docx,
        )
        assertEquals("Доклад", book.metadata.title)
        assertEquals(listOf("Анна"), book.metadata.authors)
        val texts = book.texts()
        assertEquals(TextKind.Heading1, texts[0].kind)
        assertEquals("Обычный жирный1", texts[1].text)
        assertEquals(app.folio.core.model.Align.Center, texts[1].align)
        assertTrue(texts[1].spans.any { it.has(Span.BOLD) && texts[1].text.substring(it.start, it.end) == "жирный" })
        val ref = texts[1].spans.single { it.has(Span.NOTE_REF) }
        assertEquals("Примечание", (book.footnotes.getValue(ref.target!!).single() as Block.Text).text)
    }

    @Test
    fun mobiWithMetadataAndImages() {
        val html = """<html><body><h1>Глава</h1><p>Текст книги.</p><mbp:pagebreak/><img recindex="00001"></body></html>"""
        val file = tmp.newFile("book.mobi").apply { writeBytes(mobi(html.toByteArray(), image = byteArrayOf(9, 8, 7))) }
        val book = open(file, BookFormat.Mobi)
        assertEquals("Заглавие", book.metadata.title)
        assertEquals(listOf("Автор"), book.metadata.authors)
        assertEquals("rec:1", book.metadata.coverResource)
        assertEquals(listOf("Глава", "Текст книги."), book.texts().map { it.text })
        assertTrue(book.chapters.flatMap { it.blocks }.any { it is Block.Image && it.resource == "rec:1" })
        assertArrayEquals(byteArrayOf(9, 8, 7), resources["rec:1"])
    }

    @Test
    fun palmDocDecompression() {
        // "abcabcabc": literals, then a back-reference of 6 bytes at distance 3.
        val pair = (3 shl 3) or (6 - 3)
        val compressed = byteArrayOf('a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte(), (0x80 or (pair shr 8)).toByte(), pair.toByte(), 0xE1.toByte())
        assertEquals("abcabcabc a", String(MobiParser.palmDoc(compressed)))
    }

    /** A minimal uncompressed BOOKMOBI: record 0 header + EXTH, one text record, one image. */
    private fun mobi(text: ByteArray, image: ByteArray): ByteArray {
        fun exth(type: Int, value: ByteArray) = ByteArrayOutputStream().apply {
            DataOutputStream(this).apply { writeInt(type); writeInt(value.size + 8); write(value) }
        }.toByteArray()
        val records = exth(100, "Автор".toByteArray()) + exth(503, "Заглавие".toByteArray()) + exth(201, byteArrayOf(0, 0, 0, 0))
        val exthBlock = ByteArrayOutputStream().apply {
            DataOutputStream(this).apply { writeBytes("EXTH"); writeInt(12 + records.size); writeInt(3); write(records) }
        }.toByteArray()
        val headerLength = 0xE8
        val r0 = ByteArray(16 + headerLength)
        fun put16(at: Int, v: Int) { r0[at] = (v shr 8).toByte(); r0[at + 1] = v.toByte() }
        fun put32(at: Int, v: Int) { put16(at, v shr 16); put16(at + 2, v) }
        put16(0, 1); put32(4, text.size); put16(8, 1); put16(10, 4096)
        "MOBI".forEachIndexed { i, c -> r0[16 + i] = c.code.toByte() }
        put32(20, headerLength); put32(28, 65001); put32(0x6C, 2); put32(0x80, 0x40)
        val record0 = r0 + exthBlock
        val body = listOf(record0, text, image)
        val out = ByteArrayOutputStream()
        DataOutputStream(out).apply {
            write(ByteArray(60)); writeBytes("BOOKMOBI"); write(ByteArray(8)); writeShort(body.size)
            var offset = 78 + body.size * 8
            body.forEach { writeInt(offset); writeInt(0); offset += it.size }
            body.forEach { write(it) }
        }
        return out.toByteArray()
    }
}
