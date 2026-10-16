package app.folio.feature.catalog

import app.folio.core.data.catalog.DownloadItem
import app.folio.core.data.db.DownloadEntity
import app.folio.core.data.db.DownloadState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class BookFormatsTest {
    private val flibusta = listOf("FB2", "EPUB", "MOBI", "RTF", "HTML", "TXT")

    private fun item(format: String, state: DownloadState, at: Long, inLibrary: Boolean = true) = DownloadItem(
        DownloadEntity(
            id = format, link = format, format = format, title = "", author = "", coverUrl = null, state = state.name,
            bookId = if (state == DownloadState.Done) format else null, createdAt = at, updatedAt = at,
        ),
        bytes = 0, total = -1, inLibrary = inLibrary,
    )

    private fun formats(vararg items: DownloadItem) = flibusta.map { f -> f to items.firstOrNull { it.entity.format == f } }

    @Test
    fun nothingPickedOffersCatalogFormatAndGroupsConversions() {
        val choice = bookFormats(formats())!!
        assertEquals("FB2", choice.capsule.first)
        assertEquals(listOf("EPUB"), choice.rest.map { it.first })
        assertEquals(listOf("MOBI", "RTF", "HTML", "TXT"), choice.conversions.map { it.first })
    }

    @Test
    fun capsuleFollowsLatestPickAndLeavesTheMenu() {
        val choice = bookFormats(formats(item("FB2", DownloadState.Done, 1), item("TXT", DownloadState.Running, 2)))!!
        assertEquals("TXT", choice.capsule.first)
        assertEquals(listOf("FB2", "EPUB"), choice.rest.map { it.first })
        assertEquals(listOf("MOBI", "RTF", "HTML"), choice.conversions.map { it.first })
        assertEquals(listOf("TXT"), choice.started.map { it.first })
    }

    @Test
    fun deletedCopyIsNoLongerThePick() {
        val choice = bookFormats(formats(item("EPUB", DownloadState.Done, 1), item("TXT", DownloadState.Done, 2, inLibrary = false)))!!
        assertEquals("EPUB", choice.capsule.first)
    }

    @Test
    fun withoutRealBooksNothingIsSetApart() {
        val choice = bookFormats(listOf("PDF" to null, "TXT" to null))!!
        assertEquals("PDF", choice.capsule.first)
        assertEquals(listOf("TXT"), choice.rest.map { it.first })
        assertEquals(emptyList<String>(), choice.conversions.map { it.first })
    }

    @Test
    fun singleFormatHasMenuOnlyWhileDownloading() {
        assertFalse(bookFormats(listOf("EPUB" to null))!!.hasMenu)
        assert(bookFormats(listOf("EPUB" to item("EPUB", DownloadState.Paused, 1)))!!.hasMenu)
        assertNull(bookFormats(emptyList()))
    }
}
