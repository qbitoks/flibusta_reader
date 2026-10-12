package net.flibustareader.core.format

import net.flibustareader.core.format.epub.EpubParser
import net.flibustareader.core.format.fb2.Fb2Parser
import net.flibustareader.core.model.Book
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

/** Receives binary resources (images) extracted while parsing. */
fun interface ResourceSink {
    fun put(id: String, bytes: ByteArray)
}

enum class BookFormat(val extension: String) {
    Epub("epub"),
    Fb2("fb2"),
    Fb2Zip("fb2.zip"),
}

class UnsupportedBookException(message: String) : IOException(message)

object BookParser {

    fun parse(file: File, format: BookFormat, resources: ResourceSink): Book = when (format) {
        BookFormat.Epub -> EpubParser(file, resources).parse()
        BookFormat.Fb2 -> file.inputStream().use { Fb2Parser(resources).parse(it) }
        BookFormat.Fb2Zip -> ZipFile(file).use { zip ->
            val entry = zip.entries().asSequence().firstOrNull { !it.isDirectory && it.name.endsWith(".fb2", ignoreCase = true) }
                ?: throw UnsupportedBookException("No .fb2 inside archive")
            zip.getInputStream(entry).use { Fb2Parser(resources).parse(it) }
        }
    }

    /**
     * Detects the format by content rather than by name: files shared from messengers
     * and browsers often arrive without a meaningful extension.
     */
    fun detect(file: File): BookFormat? {
        val head = ByteArray(512)
        val read = file.inputStream().use { it.read(head) }
        if (read <= 0) return null
        if (head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()) {
            return try {
                ZipFile(file).use { zip ->
                    when {
                        zip.getEntry("META-INF/container.xml") != null -> BookFormat.Epub
                        zip.entries().asSequence().any { it.name.endsWith(".fb2", ignoreCase = true) } -> BookFormat.Fb2Zip
                        else -> null
                    }
                }
            } catch (e: IOException) {
                null
            }
        }
        val text = String(head, 0, read, Charsets.ISO_8859_1)
        return if (text.contains("<FictionBook")) BookFormat.Fb2 else null
    }
}
