package net.flibustareader.core

import java.nio.file.Files
import java.util.zip.ZipFile
import org.junit.Assert.*
import org.junit.Test

class Fb2ConverterTest {
    @Test fun convertsStructureNotesAndImagesToEpub() {
        val dir = Files.createTempDirectory("fb2-test").toFile()
        try {
            val input = dir.resolve("book.fb2")
            input.writeText("""<FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0" xmlns:l="http://www.w3.org/1999/xlink"><description><title-info><book-title>Книга &amp; мир</book-title><author><first-name>Иван</first-name><last-name>Автор</last-name></author><lang>ru</lang></title-info></description><body><section id="chapter"><title><p>Глава 1</p></title><p>Текст <emphasis>курсивом</emphasis> <a l:href="#note">1</a></p><image l:href="#picture"/></section></body><body name="notes"><section id="note"><p>Сноска</p></section></body><binary id="picture" content-type="image/png">aGVsbG8=</binary></FictionBook>""")
            val output = dir.resolve("book.epub")
            assertEquals("Книга & мир", Fb2Converter.convert(input, output))
            ZipFile(output).use { zip ->
                val mime = zip.entries().nextElement()
                assertEquals("mimetype", mime.name)
                assertEquals(0, mime.method)
                fun read(name: String) = zip.getInputStream(zip.getEntry(name)).bufferedReader().readText()
                assertEquals("application/epub+zip", read("mimetype"))
                val html = read("OEBPS/content.xhtml")
                assertTrue(html.contains("<em>курсивом</em>"))
                assertTrue(html.contains("href=\"#note\""))
                assertTrue(html.contains("id=\"note\""))
                assertTrue(html.contains("images/0.png"))
                assertEquals("hello", read("OEBPS/images/0.png"))
                assertTrue(read("OEBPS/nav.xhtml").contains("content.xhtml#chapter"))
                assertTrue(read("OEBPS/package.opf").contains("Книга &amp; мир"))
            }
        } finally { dir.deleteRecursively() }
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsWrongFormat() {
        val dir = Files.createTempDirectory("fb2-test").toFile()
        try {
            val input = dir.resolve("bad.fb2").apply { writeText("<html>not a book</html>") }
            Fb2Converter.convert(input, dir.resolve("out.epub"))
        } finally { dir.deleteRecursively() }
    }
}
