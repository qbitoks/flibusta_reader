package net.flibustareader.core.opds

import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class HttpsRequest(private val connect: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection }) {
    fun <T> read(address: String, consume: (InputStream, Long, String) -> T): T {
        var url = URL(address)
        repeat(6) {
            if (Thread.currentThread().isInterrupted) throw InterruptedException("Операция отменена")
            require(url.protocol == "https") { "Источник должен использовать HTTPS" }
            val connection = connect(url)
            connection.connectTimeout = 20_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", "FlibustaReader/0.2 Android")
            try {
                val code = connection.responseCode
                if (code in listOf(301, 302, 303, 307, 308)) {
                    url = URL(url, connection.getHeaderField("Location") ?: error("Пустое перенаправление"))
                } else {
                    require(code in 200..299) { "Сервер ответил HTTP $code. Повторите попытку или смените источник." }
                    return connection.inputStream.use { consume(it, connection.contentLengthLong, url.toString()) }
                }
            } finally { connection.disconnect() }
        }
        error("Слишком много перенаправлений")
    }
}
