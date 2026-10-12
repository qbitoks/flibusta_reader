package net.flibustareader.core.model

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Compact hand-written binary format for [Book]. Reading it back is an order of
 * magnitude faster than re-parsing EPUB/FB2, which is what makes reopening instant.
 * Bump [VERSION] on any layout change: stale caches are then rejected and rebuilt.
 */
object BookCodec {
    const val VERSION = 1
    private const val MAGIC = 0x5245_4442 // "REDB"

    fun write(book: Book, output: OutputStream) {
        DataOutputStream(BufferedOutputStream(output, 64 * 1024)).run {
            writeInt(MAGIC)
            writeInt(VERSION)
            writeMetadata(book.metadata)
            writeInt(book.chapters.size)
            book.chapters.forEach { writeChapter(it) }
            writeInt(book.toc.size)
            book.toc.forEach { writeToc(it) }
            writeInt(book.footnotes.size)
            book.footnotes.forEach { (id, blocks) ->
                writeUTF(id)
                writeBlocks(blocks)
            }
            flush()
        }
    }

    fun read(input: InputStream): Book = DataInputStream(BufferedInputStream(input, 64 * 1024)).run {
        if (readInt() != MAGIC || readInt() != VERSION) throw IOException("Incompatible book cache")
        val metadata = readMetadata()
        val chapters = List(readInt()) { readChapter() }
        val toc = List(readInt()) { readToc() }
        val footnotes = HashMap<String, List<Block>>()
        repeat(readInt()) { footnotes[readUTF()] = readBlocks() }
        Book(metadata, chapters, toc, footnotes)
    }

    private fun DataOutputStream.writeMetadata(m: BookMetadata) {
        writeUTF(m.title)
        writeInt(m.authors.size)
        m.authors.forEach { writeUTF(it) }
        writeNullable(m.language)
        writeNullable(m.series)
        writeInt(m.seriesIndex ?: Int.MIN_VALUE)
        writeLongString(m.description)
        writeNullable(m.coverResource)
    }

    private fun DataInputStream.readMetadata() = BookMetadata(
        title = readUTF(),
        authors = List(readInt()) { readUTF() },
        language = readNullable(),
        series = readNullable(),
        seriesIndex = readInt().takeIf { it != Int.MIN_VALUE },
        description = readLongString(),
        coverResource = readNullable(),
    )

    private fun DataOutputStream.writeChapter(c: Chapter) {
        writeNullable(c.title)
        writeBlocks(c.blocks)
        writeInt(c.anchors.size)
        c.anchors.forEach { (k, v) ->
            writeUTF(k)
            writeInt(v)
        }
    }

    private fun DataInputStream.readChapter(): Chapter {
        val title = readNullable()
        val blocks = readBlocks()
        val anchors = HashMap<String, Int>()
        repeat(readInt()) { anchors[readUTF()] = readInt() }
        return Chapter(title, blocks, anchors)
    }

    private fun DataOutputStream.writeBlocks(blocks: List<Block>) {
        writeInt(blocks.size)
        for (b in blocks) when (b) {
            is Block.Text -> {
                writeByte(0)
                writeLongString(b.text)
                writeByte(b.kind.ordinal)
                writeByte(b.align.ordinal)
                writeInt(b.spans.size)
                for (s in b.spans) {
                    writeInt(s.start)
                    writeInt(s.end)
                    writeInt(s.flags)
                    writeNullable(s.target)
                }
            }
            is Block.Image -> {
                writeByte(1)
                writeUTF(b.resource)
                writeNullable(b.caption)
            }
            Block.Separator -> writeByte(2)
        }
    }

    private val textKinds = TextKind.entries
    private val aligns = Align.entries

    private fun DataInputStream.readBlocks(): List<Block> = List(readInt()) {
        when (val type = readByte().toInt()) {
            0 -> {
                val text = readLongString()!!
                val kind = textKinds[readByte().toInt()]
                val align = aligns[readByte().toInt()]
                val spans = List(readInt()) { Span(readInt(), readInt(), readInt(), readNullable()) }
                Block.Text(text, spans, kind, align)
            }
            1 -> Block.Image(readUTF(), readNullable())
            2 -> Block.Separator
            else -> throw IOException("Unknown block type $type")
        }
    }

    private fun DataOutputStream.writeToc(e: TocEntry) {
        writeUTF(e.title)
        writeInt(e.position.chapter)
        writeInt(e.position.block)
        writeInt(e.position.offset)
        writeInt(e.children.size)
        e.children.forEach { writeToc(it) }
    }

    private fun DataInputStream.readToc(): TocEntry = TocEntry(
        title = readUTF(),
        position = Position(readInt(), readInt(), readInt()),
        children = List(readInt()) { readToc() },
    )

    private fun DataOutputStream.writeNullable(value: String?) {
        writeBoolean(value != null)
        if (value != null) writeUTF(value)
    }

    private fun DataInputStream.readNullable(): String? = if (readBoolean()) readUTF() else null

    /** writeUTF is limited to 64 KB; paragraphs and annotations can be longer. */
    private fun DataOutputStream.writeLongString(value: String?) {
        if (value == null) {
            writeInt(-1)
            return
        }
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.readLongString(): String? {
        val size = readInt()
        if (size < 0) return null
        val bytes = ByteArray(size)
        readFully(bytes)
        return String(bytes, Charsets.UTF_8)
    }
}
