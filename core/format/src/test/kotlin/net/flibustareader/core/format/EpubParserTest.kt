package net.flibustareader.core.format

import net.flibustareader.core.model.Align
import net.flibustareader.core.model.Block
import net.flibustareader.core.model.Span
import net.flibustareader.core.model.TextKind
import net.flibustareader.core.model.footnoteBlocks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EpubParserTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun epub(files: Map<String, String>): File {
        val file = tmp.newFile("book.epub")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("mimetype"))
            zip.write("application/epub+zip".toByteArray())
            files.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
            }
        }
        return file
    }

    private val container = """<?xml version="1.0"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
</container>"""

    private val opf = """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:title>Alice's Adventures</dc:title>
    <dc:creator>Lewis Carroll</dc:creator>
    <dc:language>en</dc:language>
    <meta name="calibre:series" content="Alice"/>
    <meta name="calibre:series_index" content="1.0"/>
  </metadata>
  <manifest>
    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
    <item id="c1" href="text/ch1.xhtml" media-type="application/xhtml+xml"/>
    <item id="c2" href="text/ch2.xhtml" media-type="application/xhtml+xml"/>
    <item id="css" href="style.css" media-type="text/css"/>
    <item id="img" href="images/cover%20art.jpg" media-type="image/jpeg" properties="cover-image"/>
  </manifest>
  <spine><itemref idref="c1"/><itemref idref="c2"/></spine>
</package>"""

    private val nav = """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><body>
<nav epub:type="toc"><ol>
  <li><a href="text/ch1.xhtml">Down the Rabbit-Hole</a></li>
  <li><a href="text/ch2.xhtml#pool">The Pool of Tears</a>
    <ol><li><a href="text/ch2.xhtml#deep">Deeper</a></li></ol></li>
</ol></nav></body></html>"""

    private val ch1 = """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
<head><link rel="stylesheet" href="../style.css"/></head>
<body>
  <h1>Chapter I</h1>
  <p class="first">Alice was   beginning to get <i>very</i> tired<a epub:type="noteref" href="#fn1">1</a>.</p>
  <p class="c">Centered&nbsp;text</p>
  <div class="hidden"><p>Invisible</p></div>
  <img src="../images/cover%20art.jpg" alt="Cover"/>
  <p>See <a href="ch2.xhtml#deep">later</a> and <a href="https://example.com">web</a><sup><a href="ch2.xhtml#n2">2</a></sup>.</p>
  <aside epub:type="footnote" id="fn1"><p>A footnote.</p></aside>
</body></html>"""

    private val ch2 = """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml"><body>
  <h2 id="pool">Chapter II</h2>
  <p>First line<br/>second line</p>
  <p id="deep">Deep paragraph</p>
  <p id="n2">2. Endnote text.</p>
  <h3>Next section</h3>
</body></html>"""

    private val css = "@charset \"utf-8\"; p.c { text-align: center } .hidden{display:none} /* c */ @media print { p { font-weight: bold } } .first { font-weight: 700 }"

    @Test
    fun parsesStructure() {
        val resources = HashMap<String, ByteArray>()
        val file = epub(
            mapOf(
                "META-INF/container.xml" to container,
                "OEBPS/content.opf" to opf,
                "OEBPS/nav.xhtml" to nav,
                "OEBPS/text/ch1.xhtml" to ch1,
                "OEBPS/text/ch2.xhtml" to ch2,
                "OEBPS/style.css" to css,
                "OEBPS/images/cover art.jpg" to "JPEG",
            ),
        )
        assertEquals(BookFormat.Epub, BookParser.detect(file))
        val book = BookParser.parse(file, BookFormat.Epub) { id, bytes -> resources[id] = bytes }

        assertEquals("Alice's Adventures", book.metadata.title)
        assertEquals(listOf("Lewis Carroll"), book.metadata.authors)
        assertEquals("Alice", book.metadata.series)
        assertEquals(1, book.metadata.seriesIndex)
        assertEquals("OEBPS/images/cover art.jpg", book.metadata.coverResource)
        assertTrue(resources.containsKey("OEBPS/images/cover art.jpg"))

        assertEquals(listOf("Down the Rabbit-Hole", "The Pool of Tears"), book.toc.map { it.title })
        assertEquals(1, book.toc[1].position.chapter)
        assertEquals(2, book.toc[1].children.single().position.block)
        assertEquals("Down the Rabbit-Hole", book.chapters[0].title)

        val blocks = book.chapters[0].blocks
        val heading = blocks[0] as Block.Text
        assertEquals(TextKind.Heading1, heading.kind)
        val first = blocks[1] as Block.Text
        assertEquals("Alice was beginning to get very tired1.", first.text)
        assertTrue(first.spans.any { it.has(Span.BOLD) && it.start == 0 })
        assertTrue(first.spans.any { it.has(Span.ITALIC) && first.text.substring(it.start, it.end) == "very" })
        val noteRef = first.spans.single { it.has(Span.NOTE_REF) }
        assertEquals("A footnote.", (book.footnoteBlocks(noteRef.target!!).single() as Block.Text).text)

        val centered = blocks[2] as Block.Text
        assertEquals("Centered text", centered.text)
        assertEquals(Align.Center, centered.align)
        assertTrue(blocks.none { it is Block.Text && it.text == "Invisible" })
        assertEquals(Block.Image("OEBPS/images/cover art.jpg", "Cover"), blocks[3])

        val links = (blocks[4] as Block.Text).spans
        assertEquals("@1:2:0", links.single { it.has(Span.LINK) && it.target?.startsWith("@") == true }.target)
        assertTrue(links.any { it.target == "https://example.com" })
        val endnote = links.single { it.has(Span.NOTE_REF) }
        assertEquals(listOf("2. Endnote text."), book.footnoteBlocks(endnote.target!!).map { (it as Block.Text).text })

        assertEquals("First line\nsecond line", (book.chapters[1].blocks[1] as Block.Text).text)
        assertEquals(blocks.size, 5)
    }
}
