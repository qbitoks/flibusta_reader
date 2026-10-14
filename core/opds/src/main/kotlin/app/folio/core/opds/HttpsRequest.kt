package app.folio.core.opds

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** Non-2xx answer. An [IOException], so callers can tell transient codes (5xx, 429) from the rest. */
class HttpStatusException(val code: Int) : IOException("Сервер ответил HTTP $code. Повторите попытку или смените источник.")

/** The final (non-redirect) response; [stream] is closed when the consumer returns. */
class HttpsResponse(
    val code: Int,
    val stream: InputStream,
    val contentLength: Long,
    val finalUrl: String,
    private val headers: (String) -> String?,
) {
    fun header(name: String): String? = headers(name)
}

class HttpsRequest(
    private val readTimeoutMs: Int = 30_000,
    private val connectTimeoutMs: Int = 20_000,
    private val connect: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
) {
    fun <T> read(address: String, consume: (InputStream, Long, String) -> T): T =
        open(address) { consume(it.stream, it.contentLength, it.finalUrl) }

    /**
     * Follows up to 6 redirects by hand, refusing any that leave HTTPS. [headers] are sent on every hop.
     * With a `Range` header, 416 is handed to the consumer instead of thrown (see [Resume]).
     */
    fun <T> open(address: String, headers: Map<String, String> = emptyMap(), consume: (HttpsResponse) -> T): T {
        var url = URL(address)
        val ranged = headers.keys.any { it.equals("Range", ignoreCase = true) }
        repeat(6) {
            if (Thread.currentThread().isInterrupted) throw InterruptedException("Операция отменена")
            require(url.protocol == "https") { "Источник должен использовать HTTPS" }
            val connection = connect(url)
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", "Folio/0.3 Android")
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            try {
                val code = connection.responseCode
                if (code in listOf(301, 302, 303, 307, 308)) {
                    url = URL(url, connection.getHeaderField("Location") ?: error("Пустое перенаправление"))
                } else {
                    val rangeMiss = ranged && code == 416
                    if (code !in 200..299 && !rangeMiss) throw HttpStatusException(code)
                    val stream = if (rangeMiss) connection.errorStream ?: ByteArrayInputStream(ByteArray(0)) else connection.inputStream
                    return stream.use { consume(HttpsResponse(code, it, connection.contentLengthLong, url.toString(), connection::getHeaderField)) }
                }
            } finally { connection.disconnect() }
        }
        error("Слишком много перенаправлений")
    }
}
