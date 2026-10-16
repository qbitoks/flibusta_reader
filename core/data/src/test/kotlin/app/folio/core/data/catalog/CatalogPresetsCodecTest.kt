package app.folio.core.data.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class CatalogPresetsCodecTest {
    @Test fun decodesTheModulesRepositoryList() {
        val list = CatalogPresetsCodec.decode("""{"version":1,"catalogs":[
            {"id":"f","title":"Ф","address":"https://f.example/opds","format":"FB2","description":"d","note":"n","russian":true,"extra":1},
            {"id":"g","title":"G","address":"https://g.example/","description":"e","russian":false},
            {"id":"bad","title":"B","address":"http://plain.example/"},
            {"id":"f","title":"dup","address":"https://dup.example/"}]}""")!!
        assertEquals(listOf("f", "g"), list.map { it.catalog.id })
        assertEquals("FB2", list[0].catalog.format)
        assertEquals("n", list[0].note)
        assertEquals(false, list[1].russian)
        assertEquals(Catalog.DEFAULT_FORMAT, list[1].catalog.format)
    }

    @Test fun unknownVersionsAndJunkKeepTheCurrentList() {
        assertNull(CatalogPresetsCodec.decode("""{"version":2,"catalogs":[{"id":"a","title":"A","address":"https://a/"}]}"""))
        assertNull(CatalogPresetsCodec.decode("not json"))
        assertNull(CatalogPresetsCodec.decode("""{"version":1,"catalogs":[]}"""))
    }

    /** The bundled list and the repository's `catalogs.json` (checked out next to Folio) describe the same catalogs. */
    @Test fun repositoryListMatchesTheBundledOne() {
        val file = File("../../../folio-modules/catalogs.json").takeIf { it.isFile } ?: return
        val remote = CatalogPresetsCodec.decode(file.readText())!!
        assertEquals(CatalogPresets.bundled, remote)
    }
}
