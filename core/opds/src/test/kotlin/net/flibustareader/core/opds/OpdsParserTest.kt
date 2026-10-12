package net.flibustareader.core.opds

import org.junit.Assert.*
import org.junit.Test

class OpdsParserTest {
    @Test fun resolvesNavigationDownloadsAndPagination() {
        val page = OpdsParser.parse("""<feed xmlns="http://www.w3.org/2005/Atom"><title>Книги</title><link rel="next" href="?page=2"/><entry><id>book:1</id><title>Потоп</title><author><name>Сенкевич</name></author><content>Описание</content><link rel="http://opds-spec.org/acquisition/open-access" type="application/epub+zip" href="/b/1/epub"/><link rel="http://opds-spec.org/acquisition" type="application/fb2+zip" href="/b/1/fb2"/></entry><entry><id>author:2</id><title>Автор</title><link type="application/atom+xml;profile=opds-catalog" href="/opds/author/2"/></entry></feed>""", "https://flibusta.is/opds/search")
        assertEquals("Книги", page.title)
        assertEquals("Сенкевич", page.entries[0].author)
        assertEquals("https://flibusta.is/b/1/epub", page.entries[0].downloads["EPUB"])
        assertEquals("https://flibusta.is/b/1/fb2", page.entries[0].downloads["FB2"])
        assertEquals("https://flibusta.is/opds/author/2", page.entries[1].navigation)
        assertEquals("https://flibusta.is/opds/search?page=2", page.next)
    }
    @Test fun catalogWithoutTextsDoesNotInventDownloads() {
        val page = OpdsParser.parse("""<feed xmlns="http://www.w3.org/2005/Atom"><entry><id>1</id><title>Книга</title><link type="text/html" href="/b/1"/></entry></feed>""", "https://flisland.net/opds")
        assertTrue(page.entries.single().downloads.isEmpty())
    }
    @Test fun acceptsFlibustaEntriesWithoutIdAndIgnoresRelatedAuthorLinks() {
        val page = OpdsParser.parse("""<feed xmlns="http://www.w3.org/2005/Atom"><entry><title>Книга</title><author><name>Автор</name></author><link rel="related" type="application/atom+xml" href="/opds/author/1"/><link rel="http://opds-spec.org/acquisition/open-access" type="application/epub+zip" href="/b/1/epub"/></entry></feed>""", "https://flibusta.is/opds")
        assertEquals("https://flibusta.is/b/1/epub", page.entries.single().id)
        assertNull(page.entries.single().navigation)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsHtmlInsteadOfFeed() {
        OpdsParser.parse("<html><body>Ошибка</body></html>", "https://flibusta.is/opds")
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsDoctype() {
        OpdsParser.parse("<!DOCTYPE feed [<!ENTITY x SYSTEM 'file:///secret'>]><feed>&x;</feed>", "https://flibusta.is")
    }
}
