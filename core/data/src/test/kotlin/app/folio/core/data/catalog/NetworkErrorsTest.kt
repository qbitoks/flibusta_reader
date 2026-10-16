package app.folio.core.data.catalog

import app.folio.core.opds.DownloadCheck
import app.folio.core.opds.HttpStatusException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkErrorsTest {
    @Test fun refusalsAreClassified() {
        assertEquals(NetworkErrors.LOGIN, NetworkErrors.classify(HttpStatusException(401, "https://lib.example/b/1"), "https://lib.example/b/1/fb2"))
        assertEquals("elsewhere:www.litres.ru", NetworkErrors.classify(HttpStatusException(403, "https://www.litres.ru/gettrial/?art=1"), "https://iknigi.net/get_file.php?f=fb2"))
        assertEquals("http:404", NetworkErrors.classify(HttpStatusException(404, "https://lib.example/b/1"), "https://lib.example/b/1"))
        assertEquals("http:503", NetworkErrors.classify(HttpStatusException(503, "https://other.example/"), "https://lib.example/b/1"))
    }

    @Test fun onlyRefusalsAreUnavailable() {
        listOf(NetworkErrors.LOGIN, NetworkErrors.NOT_BOOK, "elsewhere:x.org", "http:403").forEach { assertTrue(it, NetworkErrors.isUnavailable(it)) }
        listOf(null, NetworkErrors.OFFLINE, NetworkErrors.TIMEOUT, "http:503", NetworkErrors.IMPORT).forEach { assertFalse("$it", NetworkErrors.isUnavailable(it)) }
    }

    @Test fun signInPageIsRecognised() {
        assertTrue(DownloadCheck.asksToSignIn("<html><form><input type=\"password\" name=\"pass\"></form></html>".toByteArray()))
        assertFalse(DownloadCheck.asksToSignIn("<html><body>MySQL Fatal Error</body></html>".toByteArray()))
    }
}
