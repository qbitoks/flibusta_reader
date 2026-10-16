package app.folio.core.format.mobi

import app.folio.core.format.MAX_UNPACKED_BYTES
import app.folio.core.format.ResourceSink
import app.folio.core.format.UnsupportedBookException
import app.folio.core.format.html.HtmlBookParser
import app.folio.core.format.text.TextParser
import app.folio.core.model.Book
import app.folio.core.model.BookMetadata
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

/**
 * Mobipocket (MOBI, AZW, plain PalmDOC): a Palm database whose text records hold HTML,
 * compressed with PalmDOC LZ77. HUFF/CDIC compression and DRM are not supported.
 * KF8-only files (AZW3) are read as the same HTML stream, with rougher structure.
 */
internal class MobiParser(private val data: ByteArray, private val resources: ResourceSink) {

    private val offsets: IntArray

    init {
        if (data.size < 78) throw UnsupportedBookException("MOBI is too short")
        val count = u16(76)
        offsets = IntArray(count) { u32(78 + it * 8) }
        if (count == 0 || offsets.any { it < 0 || it > data.size }) throw UnsupportedBookException("Broken MOBI records")
    }

    fun parse(): Book {
        val r0 = offsets[0]
        val compression = u16(r0)
        val textRecords = u16(r0 + 8)
        if (compression != 1 && compression != 2) throw UnsupportedBookException("MOBI compression $compression is not supported")
        val isMobi = ascii(r0 + 16, 4) == "MOBI"
        if (u16(r0 + 12) != 0 && isMobi) throw UnsupportedBookException("MOBI is DRM-protected")

        val headerLength = if (isMobi) u32(r0 + 20) else 0
        val encoding = if (isMobi) u32(r0 + 28) else 1252
        val extraFlags = if (isMobi && headerLength >= 0xE4 && r0 + 0xF4 <= data.size) u16(r0 + 0xF2) else 0
        val firstImage = if (isMobi) u32(r0 + 0x6C) else -1

        val raw = ByteArrayOutputStream()
        for (i in 1..textRecords) {
            if (i >= offsets.size) break
            var record = record(i)
            record = record.copyOf(record.size - trailingSize(record, extraFlags))
            raw.write(if (compression == 2) palmDoc(record) else record)
            if (raw.size() > MAX_UNPACKED_BYTES) throw UnsupportedBookException("Book is larger than 50 MiB")
        }
        val bytes = raw.toByteArray()
        if (!isMobi) return TextParser.parse(bytes)

        val charset = if (encoding == 65001) Charsets.UTF_8 else Charset.forName("windows-1252")
        val exth = if (u32(r0 + 0x80) and 0x40 != 0) exth(r0 + 16 + headerLength) else emptyMap()
        val fullName = run {
            val off = u32(r0 + 0x54)
            val len = u32(r0 + 0x58)
            if (off > 0 && r0 + off + len <= data.size) String(data, r0 + off, len, charset) else ""
        }
        fun text(key: Int) = exth[key]?.map { String(it, charset).trim() }?.filter { it.isNotEmpty() }.orEmpty()
        val cover = exth[201]?.firstOrNull()?.let { if (it.size >= 4) be32(it, 0) else null }
            ?.let { "rec:${it + 1}" }
            ?.takeIf { firstImage > 0 }

        val html = String(bytes, charset)
            .replace(pageBreak, "")
            // <img recindex="00012"> → a resource the HTML parser can name.
            .replace(recIndex) { "src=\"rec:${it.groupValues[1].toInt()}\"" }
        fun image(path: String): ByteArray? {
            val index = path.removePrefix("rec:").toIntOrNull() ?: return null
            val record = firstImage + index - 1
            return if (firstImage > 0 && record in offsets.indices) record(record) else null
        }
        val book = HtmlBookParser.parse(html, resources, ::image, metadata = {
            BookMetadata(
                title = text(503).firstOrNull() ?: fullName,
                authors = text(100),
                language = null,
                description = text(103).firstOrNull()?.let { org.jsoup.Jsoup.parse(it).text() },
                coverResource = cover,
            )
        })
        cover?.let { id -> image(id)?.let { resources.put(id, it) } }
        return book
    }

    private fun record(i: Int): ByteArray {
        val end = if (i + 1 < offsets.size) offsets[i + 1] else data.size
        return data.copyOfRange(offsets[i], maxOf(offsets[i], end))
    }

    private fun exth(at: Int): Map<Int, List<ByteArray>> {
        if (at + 12 > data.size || ascii(at, 4) != "EXTH") return emptyMap()
        val count = u32(at + 8)
        val result = HashMap<Int, MutableList<ByteArray>>()
        var p = at + 12
        repeat(count) {
            if (p + 8 > data.size) return result
            val type = u32(p)
            val len = u32(p + 4)
            if (len < 8 || p + len > data.size) return result
            result.getOrPut(type) { ArrayList() } += data.copyOfRange(p + 8, p + len)
            p += len
        }
        return result
    }

    private fun u16(at: Int) = ((data[at].toInt() and 0xFF) shl 8) or (data[at + 1].toInt() and 0xFF)
    private fun u32(at: Int) = be32(data, at)
    private fun ascii(at: Int, len: Int) = if (at + len <= data.size) String(data, at, len, Charsets.ISO_8859_1) else ""

    companion object {
        private val pageBreak = Regex("<mbp:pagebreak[^>]*>", RegexOption.IGNORE_CASE)
        private val recIndex = Regex("recindex=\"?0*(\\d+)\"?", RegexOption.IGNORE_CASE)

        /** True for a Palm database holding a MOBI or PalmDOC book. */
        fun matches(head: ByteArray, size: Int): Boolean {
            if (size < 68) return false
            val type = String(head, 60, 8, Charsets.ISO_8859_1)
            return type == "BOOKMOBI" || type == "TEXtREAd"
        }

        private fun be32(b: ByteArray, at: Int) =
            ((b[at].toInt() and 0xFF) shl 24) or ((b[at + 1].toInt() and 0xFF) shl 16) or
                ((b[at + 2].toInt() and 0xFF) shl 8) or (b[at + 3].toInt() and 0xFF)

        /** Size of the trailing entries that [flags] says follow the text of each record. */
        internal fun trailingSize(record: ByteArray, flags: Int): Int {
            val size = record.size
            var num = 0
            var test = flags shr 1
            while (test != 0) {
                if (test and 1 != 0) {
                    var result = 0
                    var shift = 0
                    var p = size - num
                    while (p > 0) {
                        val v = record[p - 1].toInt() and 0xFF
                        result = result or ((v and 0x7F) shl shift)
                        shift += 7
                        p--
                        if (v and 0x80 != 0 || shift >= 28) break
                    }
                    num += result
                }
                test = test shr 1
            }
            if (flags and 1 != 0 && size - num - 1 >= 0) num += (record[size - num - 1].toInt() and 0x3) + 1
            return num.coerceIn(0, size)
        }

        internal fun palmDoc(input: ByteArray): ByteArray {
            var out = ByteArray(maxOf(4096, input.size * 2))
            var len = 0
            fun put(b: Int) {
                if (len == out.size) out = out.copyOf(out.size * 2)
                out[len++] = b.toByte()
            }
            var i = 0
            while (i < input.size) {
                val c = input[i++].toInt() and 0xFF
                when {
                    c == 0 || c in 0x09..0x7F -> put(c)
                    c in 1..8 -> repeat(c) { if (i < input.size) put(input[i++].toInt() and 0xFF) }
                    c in 0x80..0xBF -> {
                        if (i >= input.size) break
                        val pair = ((c shl 8) or (input[i++].toInt() and 0xFF)) and 0x3FFF
                        val dist = pair shr 3
                        if (dist == 0 || dist > len) continue
                        repeat((pair and 7) + 3) { put(out[len - dist].toInt() and 0xFF) }
                    }
                    else -> { put(' '.code); put(c xor 0x80) }
                }
            }
            return out.copyOf(len)
        }
    }
}
