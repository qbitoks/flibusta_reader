package app.folio.core.opds

import org.junit.Assert.*
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

class HttpsRequestTest {
    private class Response(url: URL, private val code: Int, private val redirect: String?) : HttpURLConnection(url) {
        var closed = false
        override fun connect() {}
        override fun disconnect() { closed = true }
        override fun usingProxy() = false
        override fun getResponseCode() = code
        override fun getHeaderField(name: String?) = if (name == "Location") redirect else null
        override fun getInputStream() = """<feed xmlns="http://www.w3.org/2005/Atom"><entry><link type="application/atom+xml" href="author/42"/></entry></feed>""".byteInputStream()
    }
    @Test fun redirectsSupplyFinalPathAndHostToParserAndCloseConnections() {
        val responses = mutableListOf<Response>()
        val request = HttpsRequest { url ->
            Response(url, if (responses.size < 2) 302 else 200, when (responses.size) { 0 -> "/mirror/new"; 1 -> "https://mirror.example/catalog/new"; else -> null }).also { responses += it }
        }
        val page = request.read("https://books.example/opds/new") { stream, _, finalUrl -> OpdsParser.parse(stream.bufferedReader().readText(), finalUrl) }
        assertEquals("https://mirror.example/catalog/author/42", page.entries.single().navigation)
        assertTrue(responses.all { it.closed && !it.instanceFollowRedirects })
    }
    @Test fun rejectsHttpRedirectBeforeConnecting() {
        var calls = 0
        try {
            HttpsRequest { url -> calls++; Response(url, 302, "http://books.example/new") }.read("https://books.example/") { _, _, _ -> Unit }
            fail("HTTP redirect accepted")
        } catch (_: IllegalArgumentException) { assertEquals(1, calls) }
    }
    @Test fun limitsRedirectLoops() {
        var calls = 0
        try {
            HttpsRequest { url -> calls++; Response(url, 302, "/loop") }.read("https://books.example/") { _, _, _ -> Unit }
            fail("Redirect loop accepted")
        } catch (_: IllegalStateException) { assertEquals(6, calls) }
    }
}
