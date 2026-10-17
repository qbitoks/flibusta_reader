package app.folio.core.data.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogListCodecTest {
    @Test fun decodesAListFile() {
        val list = CatalogListCodec.decode("""{"version":1,"catalogs":[
            {"id":"f","title":"Ф","address":"https://f.example/opds","format":"FB2","description":"d","note":"n","russian":true,"extra":1},
            {"id":"g","title":"G","address":"https://g.example/"},
            {"id":"h","title":"H","address":"http://h.example/"}
        ]}""")!!
        assertEquals(listOf("f", "g"), list.map { it.catalog.id })
        assertEquals("FB2", list[0].catalog.format)
        assertEquals("n", list[0].note)
        assertEquals("", list[1].description)
    }

    @Test fun rejectsOtherVersionsJunkAndEmptyLists() {
        assertNull(CatalogListCodec.decode("""{"version":2,"catalogs":[{"id":"a","title":"A","address":"https://a/"}]}"""))
        assertNull(CatalogListCodec.decode("not json"))
        assertNull(CatalogListCodec.decode("""{"version":1,"catalogs":[]}"""))
    }

    @Test fun exportRoundTripsWithoutFavoritesOrSignIn() {
        val list = listOf(
            CatalogPresets.flibustaStyle("flibusta", "Флибуста", "https://flibusta.is", downloads = "https://mirror.example"),
            Catalog("c1", "Мой", "https://example.org/opds", favorite = false),
        )
        val text = CatalogListCodec.encode(list)
        assertTrue("favorite" !in text)
        assertEquals(list.map { it.copy(favorite = true) }, CatalogListCodec.decode(text)!!.map { it.catalog })
    }

    /** `catalogs.json` in folio-modules (checked out next to Folio) stays importable. */
    @Test fun modulesRepositoryListIsImportable() {
        val file = java.io.File("../../../folio-modules/catalogs.json")
        if (!file.exists()) return
        assertTrue(CatalogListCodec.decode(file.readText())!!.isNotEmpty())
    }
}
