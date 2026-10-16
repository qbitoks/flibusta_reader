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
    @Test fun signInGoesOnlyToCatalogHostsAcrossRedirects() {
        val sent = mutableListOf<Pair<String, String?>>()
        val hops = listOf("https://mirror.example/b/1", "https://elsewhere.example/b/1", "https://books.example/b/2", null)
        val request = HttpsRequest { url ->
            val i = sent.size
            object : HttpURLConnection(url) {
                override fun connect() {}
                override fun disconnect() {}
                override fun usingProxy() = false
                override fun getResponseCode(): Int {
                    sent += url.host to getRequestProperty("Authorization")
                    return if (hops[i] != null) 302 else 200
                }
                override fun getHeaderField(name: String?) = if (name == "Location") hops[i] else null
                override fun getInputStream() = ByteArray(0).inputStream()
            }
        }
        val auth = BasicAuth("reader", "secret", listOf("Books.example", "mirror.example"))
        request.open("https://books.example/b/1", headers = mapOf("Authorization" to "Basic forged"), auth = auth) { }
        assertEquals(listOf("books.example", "mirror.example", "elsewhere.example", "books.example"), sent.map { it.first })
        val header = "Basic " + java.util.Base64.getEncoder().encodeToString("reader:secret".toByteArray())
        assertEquals(listOf(header, header, null, header), sent.map { it.second })
    }
    @Test fun signInNeverGoesOverPlainHttp() {
        val auth = BasicAuth("reader", "secret", listOf("books.example"))
        assertTrue(auth.appliesTo(URL("https://books.example/opds")))
        assertFalse(auth.appliesTo(URL("http://books.example/opds")))
        assertFalse(auth.appliesTo(URL("https://books.example.evil/opds")))
        assertFalse(auth.toString().contains("secret"))
    }
}
