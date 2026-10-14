package app.folio.core.data.catalog

import app.folio.core.data.library.ImportException
import app.folio.core.opds.HttpStatusException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** A download problem that isn't a network hiccup; [code] is one of [NetworkErrors]' codes. */
class DownloadException(val code: String, message: String) : IOException(message)

/**
 * Stable error codes shown by the UI (mapped to strings there) and stored with downloads:
 * `offline`, `timeout`, `dropped`, `http:<code>`, `not_book`, `too_big`, `import`, `other:<message>`.
 */
object NetworkErrors {
    const val OFFLINE = "offline"
    const val TIMEOUT = "timeout"
    const val DROPPED = "dropped"
    const val NOT_BOOK = "not_book"
    const val TOO_BIG = "too_big"
    const val IMPORT = "import"

    fun code(e: Throwable): String = when (e) {
        is DownloadException -> e.code
        is ImportException -> IMPORT
        is HttpStatusException -> "http:${e.code}"
        is UnknownHostException -> OFFLINE
        is SocketTimeoutException -> TIMEOUT
        is ConnectException, is SocketException, is SSLException -> DROPPED
        is IOException -> DROPPED
        else -> "other:" + (e.message ?: e.javaClass.simpleName)
    }

    /** Worth trying again later: the network or the server, not the request or the file. */
    fun isTransient(e: Throwable): Boolean = when (e) {
        is DownloadException -> e.code == DROPPED
        is ImportException -> false
        is HttpStatusException -> e.code == 408 || e.code == 425 || e.code == 429 || e.code in 500..599
        is IOException -> true
        else -> false
    }
}
