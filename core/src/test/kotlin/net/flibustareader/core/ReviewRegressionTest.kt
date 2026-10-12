package net.flibustareader.core

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.util.zip.ZipFile

class ReviewRegressionTest {
    private fun convert(body: String): Pair<String, String> {
        val dir = Files.createTempDirectory("review-fb2").toFile()
        try {
            val input = dir.resolve("book.fb2").apply { writeText("<FictionBook><body>$body</body></FictionBook>") }
            val output = dir.resolve("book.epub")
            Fb2Converter.convert(input, output)
            return ZipFile(output).use { zip ->
                fun read(name: String) = zip.getInputStream(zip.getEntry(name)).bufferedReader().readText()
                read("OEBPS/content.xhtml") to read("OEBPS/nav.xhtml")
            }
        } finally { dir.deleteRecursively() }
    }
    @Test fun deepFb2FailsWithControlledError() {
        try { convert("<section>".repeat(5000) + "<p>Text</p>" + "</section>".repeat(5000)); fail("Expected complexity limit") }
        catch (e: IllegalArgumentException) { assertEquals("Слишком сложная структура FB2", e.message) }
    }
    @Test fun generatedAnchorsAvoidEarlierAndLaterSourceIds() {
        val (html, nav) = convert("<section id=\"section-1\"><title><p>One</p></title></section><section><title><p>Two</p></title></section><section id=\"section-2\"><p>Three</p></section><section id=\"start\"><p>Four</p></section>")
        val ids = Regex(" id=\"([^\"]+)\"").findAll(html).map { it.groupValues[1] }.toList()
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(nav.contains("content.xhtml#section-3"))
    }
    @Test fun cdataAndCommentsMayMentionDoctype() {
        val (html, _) = convert("<!-- <!DOCTYPE is just text --><section><p><![CDATA[<!DOCTYPE sample>]]></p></section>")
        assertTrue(html.contains("&lt;!DOCTYPE sample&gt;"))
    }
    @Test fun inheritedXmlBaseResolvesAtLink() {
        val page = OpdsParser.parse("""<feed xmlns="http://www.w3.org/2005/Atom" xml:base="https://books.example/library/"><entry xml:base="../b/42/"><title>Book</title><link rel="http://opds-spec.org/acquisition/open-access" type="application/epub+zip" xml:base="files/" href="epub"/></entry><link rel="next" href="?page=2"/></feed>""", "https://books.example/opds/search")
        assertEquals("https://books.example/b/42/files/epub", page.entries.single().downloads["EPUB"])
        assertEquals("https://books.example/library/?page=2", page.next)
    }
    @Test fun inheritedHttpBaseDoesNotAllowDownloads() {
        val page = OpdsParser.parse("""<feed xmlns="http://www.w3.org/2005/Atom" xml:base="http://books.example/"><entry><link rel="http://opds-spec.org/acquisition" type="application/epub+zip" href="book"/></entry></feed>""", "https://books.example/")
        assertTrue(page.entries.single().downloads.isEmpty())
    }
}
