package app.folio.core.opds

import org.junit.Assert.*
import org.junit.Test

class CatalogCapabilitiesTest {
    @Test fun atomFeedDeclaresSearchNewPopularAndFacets() {
        val page = OpdsParser.parse(
            """<feed xmlns="http://www.w3.org/2005/Atom" xmlns:opds="http://opds-spec.org/2010/catalog"><title>Каталог</title>
            <link href="/opds-opensearch.xml" rel="search" type="application/opensearchdescription+xml"/>
            <link href="/opds/search?query={searchTerms}" rel="search" type="application/atom+xml"/>
            <link href="/opds/new_books" rel="http://opds-spec.org/sort/new" type="application/atom+xml"/>
            <link href="/opds/top" rel="http://opds-spec.org/sort/popular" type="application/atom+xml"/>
            <link href="/opds?lang=ru" rel="http://opds-spec.org/facet" title="Русский" opds:facetGroup="Язык" opds:activeFacet="true"/>
            <link href="/opds?lang=en" rel="http://opds-spec.org/facet" title="English" opds:facetGroup="Язык"/>
            </feed>""",
            "https://books.example/opds",
        )
        assertEquals(SearchLink("https://books.example/opds/search?query={searchTerms}", isDescription = false), page.search)
        assertEquals("https://books.example/opds/new_books", page.newBooks)
        assertEquals("https://books.example/opds/top", page.popular)
        assertEquals(listOf(Facet("Русский", "https://books.example/opds?lang=ru", true), Facet("English", "https://books.example/opds?lang=en", false)), page.facets.single().facets)
    }

    @Test fun httpLinksAreUpgradedToHttps() {
        val page = OpdsParser.parse(
            """<feed xmlns="http://www.w3.org/2005/Atom"><link href="http://lib.example/searchopds?ask={searchTerms}" rel="search" type="application/opensearchdescription+xml"/><entry><title>Жанры</title><link href="http://lib.example/g/opds" type="application/atom+xml"/></entry><entry><title>FTP</title><link href="ftp://lib.example/x" type="application/atom+xml"/></entry></feed>""",
            "https://lib.example/opds",
        )
        assertEquals("https://lib.example/g/opds", page.entries[0].navigation)
        assertEquals(SearchLink("https://lib.example/searchopds?ask={searchTerms}", isDescription = false), page.search)
        assertNull(page.entries[1].navigation)
    }

    @Test fun openSearchDescriptionPicksAtomTemplate() {
        val xml = """<OpenSearchDescription xmlns="http://a9.com/-/spec/opensearch/1.1/"><Url type="text/html" template="http://www.example.org/search/?query={searchTerms}"/><Url type="application/atom+xml" template="http://m.example.org/ebooks/search.opds/?query={searchTerms}"/></OpenSearchDescription>"""
        assertEquals("https://m.example.org/ebooks/search.opds/?query={searchTerms}", OpenSearch.template(xml, "https://www.example.org/osd.xml"))
        assertNull(OpenSearch.template("""<OpenSearchDescription xmlns="http://a9.com/-/spec/opensearch/1.1/"><Url type="text/html" template="https://x.org/?q={searchTerms}"/></OpenSearchDescription>""", "https://x.org/"))
    }

    @Test fun fillsOpenSearchAndRfc6570Templates() {
        assertEquals("https://x.org/s?q=%D0%BB%D0%B5%D0%BC%20%D1%81", SearchTemplate.fill("https://x.org/s?q={searchTerms}", " лем с "))
        assertEquals("https://x.org/s?q=a", SearchTemplate.fill("https://x.org/s?q={searchTerms}&page={startPage?}", "a"))
        assertEquals("https://x.org/opds/search?query=dune", SearchTemplate.fill("https://x.org/opds/search{?query,language,mode}", "dune"))
    }

    @Test fun opds2NavigationGroupsPublicationsAndSearch() {
        val page = OpdsParser.parse(
            """{"metadata":{"title":"Open"},"links":[{"href":"/opds/search{?query,mode}","rel":"search","templated":true,"type":"application/opds+json"},{"href":"/opds/?page=2","rel":"next"}],
            "navigation":[{"href":"/opds/art","title":"Art","type":"application/opds+json"}],
            "groups":[{"metadata":{"title":"Trending"},"links":[{"href":"/opds/trending","rel":["self","http://opds-spec.org/sort/popular"]}],"publications":[{"metadata":{"title":"X"},"links":[]}]}],
            "publications":[{"metadata":{"title":"Dune","author":[{"name":"Frank Herbert"}],"language":["en"],"published":"1965-08-01","description":"<p>Spice</p>"},
              "links":[{"href":"/b/1.epub","type":"application/epub+zip","rel":"http://opds-spec.org/acquisition/open-access"},{"href":"/works/1","type":"text/html","rel":"alternate"}],
              "images":[{"href":"/c/1.jpg","type":"image/jpeg"}]},
             {"metadata":{"title":"Lent"},"links":[{"href":"/loan/2","type":"application/opds-publication+json","rel":"http://opds-spec.org/acquisition/borrow"},{"href":"/works/2","type":"text/html","rel":"alternate"}]}],
            "facets":[{"metadata":{"title":"Availability"},"links":[{"title":"All","href":"/opds/","properties":{"active":true}},{"title":"Open","href":"/opds/?mode=open"}]}]}""",
            "https://ol.example/opds/",
        )
        assertEquals("Open", page.title)
        assertEquals(SearchLink("https://ol.example/opds/search{?query,mode}", isDescription = false), page.search)
        assertEquals("https://ol.example/opds/?page=2", page.next)
        assertEquals(listOf("Art", "Dune", "Lent", "Trending"), page.entries.map { it.title })
        assertEquals("https://ol.example/opds/art", page.entries[0].navigation)
        val dune = page.entries[1]
        assertEquals("Frank Herbert", dune.author)
        assertEquals("https://ol.example/b/1.epub", dune.downloads["EPUB"])
        assertEquals("https://ol.example/c/1.jpg", dune.cover)
        assertEquals("1965", dune.year)
        assertNull(dune.web)
        val lent = page.entries[2]
        assertTrue(lent.isBook && lent.downloads.isEmpty())
        assertEquals("https://ol.example/works/2", lent.web)
        assertEquals("https://ol.example/opds/trending", page.entries[3].navigation)
        assertTrue(page.facets.single().facets.first().active)
    }

    @Test fun repairsRawQuotesInLinkTitle() {
        val page = OpdsParser.parse(
            """<feed xmlns="http://www.w3.org/2005/Atom"><entry><title>Книга</title><link type="text/html" href="/b/1" rel="alternate" title="Книги серии "Метро" автора" /><link type="application/fb2+zip" href="/b/1/fb2" rel="http://opds-spec.org/acquisition" /></entry></feed>""",
            "https://lib.example/opds",
        )
        assertEquals("https://lib.example/b/1/fb2", page.entries.single().downloads["FB2"])
    }

    @Test fun atomAlternateIsNavigationWhenNothingElse() {
        val page = OpdsParser.parse(
            """<feed xmlns="http://www.w3.org/2005/Atom"><entry><title>Éditions</title><link type="application/atom+xml;profile=opds-catalog;kind=acquisition" rel="alternate" href="/?core=documents&amp;site=a"/><link type="text/html" rel="alternate" href="https://books.example/a"/></entry></feed>""",
            "https://opds.example/",
        )
        assertEquals("https://opds.example/?core=documents&site=a", page.entries.single().navigation)
    }

    @Test(expected = IllegalArgumentException::class) fun rejectsUnrelatedJson() {
        OpdsParser.parse("""{"error":"nope"}""", "https://x.org/")
    }
}
