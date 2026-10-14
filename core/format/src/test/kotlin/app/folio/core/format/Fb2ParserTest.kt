package app.folio.core.format

import app.folio.core.format.fb2.Fb2Parser
import app.folio.core.model.Block
import app.folio.core.model.BookCodec
import app.folio.core.model.Span
import app.folio.core.model.TextKind
import app.folio.core.model.footnoteBlocks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class Fb2ParserTest {

    private val sample = """<?xml version="1.0" encoding="%s"?>
<FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0" xmlns:l="http://www.w3.org/1999/xlink">
 <description>
  <title-info>
   <genre>prose</genre>
   <author><first-name>Лев</first-name><middle-name>Николаевич</middle-name><last-name>Толстой</last-name></author>
   <book-title>Война и мир</book-title>
   <annotation><p>Роман-эпопея.</p></annotation>
   <coverpage><image l:href="#cover.jpg"/></coverpage>
   <lang>ru</lang>
   <sequence name="Классика" number="3"/>
  </title-info>
 </description>
 <body>
  <title><p>Война и мир</p></title>
  <epigraph><p>Эпиграф</p><text-author>Автор</text-author></epigraph>
  <section id="part1">
   <title><p>Часть первая</p></title>
   <section id="ch1">
    <title><p>Глава I</p></title>
    <p>— Eh bien, mon prince. <strong>Gênes</strong> et <emphasis>Lucques</emphasis><a l:href="#n1" type="note">1</a>.</p>
    <empty-line/>
    <p>Второй абзац со <a l:href="#ch2">ссылкой</a>.</p>
   </section>
   <section id="ch2">
    <title><p>Глава II</p></title>
    <poem><stanza><v>Строка один</v><v>Строка два</v></stanza></poem>
    <p>Текст&nbsp;главы&hellip;</p>
   </section>
  </section>
 </body>
 <body name="notes">
  <title><p>Примечания</p></title>
  <section id="n1"><title><p>1</p></title><p>Генуя и Лукка.</p></section>
 </body>
 <binary id="cover.jpg" content-type="image/jpeg">AAECAw==</binary>
</FictionBook>"""

    private fun parse(xml: ByteArray, resources: MutableMap<String, ByteArray> = HashMap()) =
        Fb2Parser { id, bytes -> resources[id] = bytes }.parse(ByteArrayInputStream(xml))

    @Test
    fun parsesMetadataChaptersAndNotes() {
        val resources = HashMap<String, ByteArray>()
        val book = parse(sample.format("UTF-8").toByteArray(), resources)

        assertEquals("Война и мир", book.metadata.title)
        assertEquals(listOf("Лев Николаевич Толстой"), book.metadata.authors)
        assertEquals("ru", book.metadata.language)
        assertEquals("Классика", book.metadata.series)
        assertEquals(3, book.metadata.seriesIndex)
        assertEquals("Роман-эпопея.", book.metadata.description)
        assertEquals("cover.jpg", book.metadata.coverResource)
        assertEquals(4, resources["cover.jpg"]!!.size)

        // A single wrapper "part" section → chapters are split one level deeper; the part title gets its own page.
        assertEquals(listOf(null, "Часть первая", "Глава I", "Глава II"), book.chapters.map { it.title })
        assertEquals("Часть первая", book.toc.single().title)
        assertEquals(listOf("Глава I", "Глава II"), book.toc.single().children.map { it.title })

        val para = book.chapters[2].blocks.filterIsInstance<Block.Text>().first { it.kind == TextKind.Body }
        assertTrue(para.text.startsWith("— Eh bien, mon prince. Gênes et Lucques1."))
        val note = para.spans.single { it.has(Span.NOTE_REF) }
        assertEquals("n1", note.target)
        assertEquals("Генуя и Лукка.", (book.footnoteBlocks("n1").single() as Block.Text).text)
        assertTrue(para.spans.any { it.has(Span.BOLD) && para.text.substring(it.start, it.end) == "Gênes" })

        val link = book.chapters[2].blocks.filterIsInstance<Block.Text>().flatMap { it.spans }.single { it.has(Span.LINK) }
        assertEquals("@3:0:0", link.target)

        val poem = book.chapters[3].blocks.filterIsInstance<Block.Text>().single { it.kind == TextKind.Poem }
        assertEquals("Строка один\nСтрока два", poem.text)
        assertTrue(book.chapters[2].blocks.contains(Block.Separator))
    }

    @Test
    fun handlesWindows1251AndHtmlEntities() {
        val book = parse(sample.format("windows-1251").toByteArray(charset("windows-1251")))
        assertEquals("Война и мир", book.metadata.title)
        val text = book.chapters[3].blocks.filterIsInstance<Block.Text>().last().text
        assertEquals("Текст главы…", text)
    }

    @Test
    fun codecRoundTrip() {
        val book = parse(sample.format("UTF-8").toByteArray())
        val bytes = ByteArrayOutputStream().also { BookCodec.write(book, it) }.toByteArray()
        val copy = BookCodec.read(ByteArrayInputStream(bytes))
        assertEquals(book.metadata, copy.metadata)
        assertEquals(book.toc, copy.toc)
        assertEquals(book.chapters.map { it.blocks }, copy.chapters.map { it.blocks })
        assertEquals(book.footnotes, copy.footnotes)
        assertNotNull(copy.chapters[2].anchors["ch1"])
    }
    @Test
    fun rejectsDoctypeAndExternalEntities() {
        val xml = """<?xml version="1.0"?><!DOCTYPE FictionBook [<!ENTITY x SYSTEM "file:///etc/hosts">]><FictionBook><body><section><p>&x;</p></section></body></FictionBook>"""
        assertTrue(runCatching { parse(xml.toByteArray()) }.isFailure)
    }

    @Test
    fun rejectsNestingDeeperThanLimit() {
        val deep = "<section>".repeat(200) + "<p>Text</p>" + "</section>".repeat(200)
        val error = runCatching { parse("<FictionBook><body>$deep</body></FictionBook>".toByteArray()) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }
}
