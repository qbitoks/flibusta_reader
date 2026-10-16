package app.folio.core.format.text

import app.folio.core.model.Align
import app.folio.core.model.Block
import app.folio.core.model.Book
import app.folio.core.model.BookMetadata
import app.folio.core.model.Chapter
import app.folio.core.model.TocEntry
import app.folio.core.model.Position
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Any plain-text file: prose, configs, logs, source code, Markdown (shown as is).
 * Prose paragraphs are joined; line-oriented text keeps every line and its indentation.
 * The title stays blank so the importer falls back to the file name.
 */
object TextParser {

    private const val CHAPTER_CHARS = 64 * 1024

    fun parse(bytes: ByteArray): Book {
        val text = decode(bytes).replace("\r\n", "\n").replace('\r', '\n').replace("\t", "    ")
        val lines = text.split('\n')
        val blocks = if (isProse(lines)) proseBlocks(lines) else lineBlocks(lines)
        val chapters = chunk(blocks)
        return Book(
            metadata = BookMetadata(title = "", authors = emptyList()),
            chapters = chapters,
            toc = if (chapters.size > 1) {
                chapters.mapIndexed { i, c ->
                    val first = (c.blocks.first() as Block.Text).text.lineSequence().first().trim()
                    TocEntry(if (first.length > 60) first.take(60).trimEnd() + "…" else first, Position(i))
                }
            } else {
                emptyList()
            },
            footnotes = emptyMap(),
        )
    }

    /** True for text-looking bytes: a Unicode BOM, or no NULs and almost no control characters. */
    fun looksLikeText(head: ByteArray, size: Int): Boolean {
        if (size <= 0) return false
        if (bom(head, size) != null) return true
        var control = 0
        for (i in 0 until size) {
            val b = head[i].toInt() and 0xFF
            if (b == 0) return false
            if (b < 0x20 && b != 0x09 && b != 0x0A && b != 0x0D && b != 0x0C && b != 0x1B) control++
        }
        return control * 100 <= size
    }

    internal fun decode(bytes: ByteArray): String {
        bom(bytes, bytes.size)?.let { (charset, skip) -> return String(bytes, skip, bytes.size - skip, charset) }
        val utf8 = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            utf8.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (e: CharacterCodingException) {
            // Old Russian .txt files are mostly windows-1251.
            String(bytes, Charset.forName("windows-1251"))
        }
    }

    private fun bom(b: ByteArray, size: Int): Pair<Charset, Int>? = when {
        size >= 3 && b[0] == 0xEF.toByte() && b[1] == 0xBB.toByte() && b[2] == 0xBF.toByte() -> Charsets.UTF_8 to 3
        size >= 2 && b[0] == 0xFF.toByte() && b[1] == 0xFE.toByte() -> Charsets.UTF_16LE to 2
        size >= 2 && b[0] == 0xFE.toByte() && b[1] == 0xFF.toByte() -> Charsets.UTF_16BE to 2
        else -> null
    }

    /**
     * Prose has long lines or wrapped paragraphs ending in sentence punctuation;
     * configs and code have short lines full of `=`, `:`, braces and indentation.
     */
    internal fun isProse(lines: List<String>): Boolean {
        val nonBlank = lines.filter { it.isNotBlank() }
        if (nonBlank.isEmpty()) return true
        val code = nonBlank.count { line ->
            val t = line.trim()
            t.endsWith('{') || t.endsWith('}') || t.endsWith(';') || t.startsWith('#') || t.startsWith("//") ||
                t.startsWith('[') || t.startsWith('<') || Regex("^[\\w.\\-]+\\s*[=:]").containsMatchIn(t) ||
                line.startsWith("        ")
        }
        val longLines = nonBlank.count { it.length > 120 }
        return code * 3 < nonBlank.size || longLines * 3 > nonBlank.size
    }

    /** Paragraphs split by blank lines or an indented first line; wrapped lines joined. */
    private fun proseBlocks(lines: List<String>): List<Block> {
        val blocks = ArrayList<Block>()
        val paragraph = StringBuilder()
        fun flush() {
            val t = paragraph.toString().trim()
            if (t.isNotEmpty()) blocks += Block.Text(t)
            paragraph.setLength(0)
        }
        for (line in lines) {
            when {
                line.isBlank() -> flush()
                line.first().isWhitespace() -> { flush(); paragraph.append(line.trim()) }
                else -> { if (paragraph.isNotEmpty()) paragraph.append(' '); paragraph.append(line.trim()) }
            }
        }
        flush()
        return blocks
    }

    /** One block per run of non-blank lines, keeping line breaks and indentation. */
    private fun lineBlocks(lines: List<String>): List<Block> {
        val blocks = ArrayList<Block>()
        val group = ArrayList<String>()
        fun flush() {
            if (group.isNotEmpty()) blocks += Block.Text(group.joinToString("\n") { it.trimEnd() }, align = Align.Start)
            group.clear()
        }
        for (line in lines) {
            if (line.isBlank()) flush() else group += line
            if (group.size >= 40) flush()
        }
        flush()
        return blocks
    }

    /** Long files split into chapters so pagination stays fast. */
    private fun chunk(blocks: List<Block>): List<Chapter> {
        if (blocks.isEmpty()) return listOf(Chapter(null, listOf(Block.Text(" "))))
        val chapters = ArrayList<Chapter>()
        var current = ArrayList<Block>()
        var chars = 0
        for (block in blocks) {
            current += block
            chars += (block as Block.Text).text.length
            if (chars >= CHAPTER_CHARS) {
                chapters += Chapter(null, current)
                current = ArrayList()
                chars = 0
            }
        }
        if (current.isNotEmpty()) chapters += Chapter(null, current)
        return chapters
    }
}
