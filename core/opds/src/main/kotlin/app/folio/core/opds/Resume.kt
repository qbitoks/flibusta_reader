package app.folio.core.opds

import java.io.IOException

/**
 * How to continue a partial download from the server's answer to `Range: bytes=<have>-`
 * (sent with `If-Range`, so a changed file comes back whole as 200).
 */
sealed interface Resume {
    /** Append the body to the bytes on disk; [total] is the full size, -1 if unknown. */
    data class Append(val total: Long) : Resume
    /** The body is the whole file: truncate and write from the start. */
    data class Restart(val total: Long) : Resume
    /** The file on disk is already complete. */
    data object Complete : Resume
    /** The server can't continue from here (416 with another size): drop the partial file and ask again. */
    data object Discard : Resume

    companion object {
        fun plan(have: Long, code: Int, contentRange: String?, contentLength: Long): Resume {
            val known = contentLength.takeIf { it >= 0 } ?: -1L
            if (have <= 0 || code in 200..299 && code != 206) return Restart(known)
            val range = contentRange?.let(::parseRange)
            return when (code) {
                206 -> when {
                    range == null -> throw IOException("Сервер не указал, с какого места продолжает загрузку")
                    range.start == 0L -> Restart(range.total.takeIf { it >= 0 } ?: known)
                    range.start != have -> throw IOException("Сервер продолжил загрузку не с того места")
                    else -> Append(range.total.takeIf { it >= 0 } ?: if (known >= 0) have + known else -1L)
                }
                416 -> if (range?.total == have) Complete else Discard
                else -> throw HttpStatusException(code)
            }
        }

        private data class Range(val start: Long, val total: Long)

        // "bytes 100-999/1000"; the start or the total may be an asterisk.
        private fun parseRange(value: String): Range? {
            val match = Regex("""bytes\s+(?:(\d+)-\d+|\*)/(\d+|\*)""", RegexOption.IGNORE_CASE).find(value.trim()) ?: return null
            val start = match.groupValues[1].toLongOrNull() ?: -1L
            val total = match.groupValues[2].toLongOrNull() ?: -1L
            return Range(start, total)
        }
    }
}

/** First bytes of a download: an HTML page instead of a book (error or captcha page served with 200). */
object DownloadCheck {
    fun looksLikeHtml(head: ByteArray): Boolean {
        val text = head.toString(Charsets.ISO_8859_1).trimStart('﻿', 'ï', '»', '¿', ' ', '\n', '\r', '\t').lowercase()
        if (text.startsWith("pk")) return false
        return text.startsWith("<!doctype html") || text.startsWith("<html") ||
            (text.contains("<html") && !text.contains("fictionbook"))
    }
}
