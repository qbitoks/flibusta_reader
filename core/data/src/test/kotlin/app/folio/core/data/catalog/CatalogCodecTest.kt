package app.folio.core.data.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogCodecTest {
    @Test fun roundTripsCatalogsAndSkipsBrokenRows() {
        val list = listOf(
            CatalogPresets.flibustaStyle("flibusta", "Флибуста", "https://flibusta.is", downloads = "https://flisland.net"),
            Catalog("c1", "Gutenberg", "https://www.gutenberg.org/ebooks.opds/"),
        )
        assertEquals(list, CatalogCodec.decode(CatalogCodec.encode(list)))
        assertEquals(listOf(list[1]), CatalogCodec.decode("""[{"id":"x"},{"id":"c1","title":"Gutenberg","address":"https://www.gutenberg.org/ebooks.opds/"}]"""))
        assertTrue(CatalogCodec.decode("not json").isEmpty())
    }

    @Test fun flibustaStyleKeepsTheOldAddressesAndDropsASameHostMirror() {
        val c = CatalogPresets.flibustaStyle("f", "Флибуста", "https://flibusta.is", downloads = "https://flibusta.is")
        assertEquals("https://flibusta.is/opds", c.address)
        assertEquals("https://flibusta.is/opds/new/0/new", c.newBooks)
        assertEquals("https://flibusta.is/opds/search?searchType=authors&searchTerm={searchTerms}", c.searchAuthors)
        assertEquals(null, c.downloads)
    }

    @Test fun presetsAreHttpsAndUnique() {
        val all = CatalogPresets.all.map { it.catalog }
        assertTrue(all.all { it.address.startsWith("https://") })
        assertEquals(all.size, all.map { it.id }.toSet().size)
    }
}
