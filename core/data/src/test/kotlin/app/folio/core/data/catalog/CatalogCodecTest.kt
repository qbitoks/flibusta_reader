package app.folio.core.data.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test fun formatDefaultsToEpubAndFlibustaTakesFb2() {
        assertEquals("FB2", CatalogPresets.flibustaStyle("f", "Ф", "https://f.example").format)
        assertEquals("EPUB", CatalogPresets.preset("iknigi")!!.catalog.format)
        val rows = CatalogCodec.decode("""[{"id":"a","title":"A","address":"https://a.org/opds"},{"id":"b","title":"B","address":"https://b.org/opds","format":"PDF"}]""")
        assertEquals(listOf("EPUB", "EPUB"), rows.map { it.format })
        assertTrue("format" !in CatalogCodec.encode(rows))
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

    @Test fun oldRowsDecodeAsFavoritesAndUnstarredRowsKeepTheFlag() {
        val old = CatalogCodec.decode("""[{"id":"c1","title":"Мой","address":"https://example.org/opds"}]""")
        assertTrue(old.single().favorite)
        val mine = Catalog("c1", "Мой", "https://example.org/opds", favorite = false)
        assertEquals(listOf(mine), CatalogCodec.decode(CatalogCodec.encode(listOf(mine))))
    }

    @Test fun directoryListsSavedRowsThenUncoveredPresets() {
        val mine = Catalog("c1", "Флибуста", "https://flibusta.is/opds")
        assertEquals(listOf(mine, CatalogPresets.preset("iknigi")!!.catalog.copy(favorite = false)), CatalogDirectory.all(listOf(mine)))
        // Same address as a saved row: the preset isn't listed twice.
        val copy = Catalog("c2", "iKnigi", "https://iknigi.net/opds")
        assertEquals(listOf(copy), CatalogDirectory.all(listOf(copy)))
    }

    @Test fun unstarringAnUneditedPresetDropsItsRow() {
        val gutenberg = CatalogPresets.preset("iknigi")!!.catalog
        val starred = CatalogDirectory.setFavorite(emptyList(), gutenberg.copy(favorite = false), true)
        assertEquals(listOf(gutenberg), starred)
        assertTrue(CatalogDirectory.setFavorite(starred, gutenberg, false).isEmpty())
    }

    @Test fun unstarringAnEditedPresetOrOwnCatalogKeepsTheRow() {
        val mirror = CatalogPresets.preset("iknigi")!!.catalog.copy(downloads = "https://mirror.example")
        val mine = Catalog("c1", "Мой", "https://example.org/opds")
        val list = CatalogDirectory.setFavorite(CatalogDirectory.setFavorite(listOf(mirror, mine), mirror, false), mine, false)
        assertEquals(listOf(mirror.copy(favorite = false), mine.copy(favorite = false)), list)
        assertTrue(CatalogDirectory.isEdited(mirror))
        assertFalse(CatalogDirectory.isPreset(mine))
    }

    @Test fun resetRestoresThePresetAndKeepsTheStar() {
        val preset = CatalogPresets.preset("iknigi")!!.catalog
        val mirror = preset.copy(downloads = "https://mirror.example")
        assertEquals(listOf(preset), CatalogDirectory.reset(listOf(mirror), "iknigi"))
        assertTrue(CatalogDirectory.reset(listOf(mirror.copy(favorite = false)), "iknigi").isEmpty())
    }

    @Test fun importUpdatesSavedRowsAndAppendsNewOnesAsFavorites() {
        val mine = Catalog("c1", "Старое зеркало", "https://old.example/opds", favorite = false)
        val sameId = Catalog("c1", "Новое зеркало", "https://new.example/opds")
        val sameAddress = Catalog("x", "Другое имя", "https://a.example/opds")
        val saved = listOf(mine, Catalog("c2", "A", "https://a.example/opds"))
        val added = Catalog("c3", "Новый", "https://n.example/opds", favorite = false)
        val result = CatalogDirectory.merge(saved, listOf(sameId, sameAddress, added))
        assertEquals(
            listOf(sameId.copy(favorite = false), Catalog("c2", "Другое имя", "https://a.example/opds"), added.copy(favorite = true)),
            result,
        )
    }
}
