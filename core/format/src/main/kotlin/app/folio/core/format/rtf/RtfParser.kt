package app.folio.core.format.rtf

import app.folio.core.format.UnsupportedBookException
import app.folio.core.format.splitChapters
import app.folio.core.model.Align
import app.folio.core.model.Block
import app.folio.core.model.Book
import app.folio.core.model.BookMetadata
import app.folio.core.model.Span
import app.folio.core.model.TextKind
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

/**
 * RTF text with paragraphs, bold/italic/strike/super/sub styles, alignment and
 * `\info` title/author. Pictures, tables layout, headers and fields are skipped;
 * 8-bit text follows `\ansicpg` and each font's `\fcharset` (Russian RTF is cp1251).
 */
internal class RtfParser(private val src: ByteArray) {

    private class State(
        var flags: Int = 0,
        var align: Align = Align.Default,
        var skip: Boolean = false,
        var uc: Int = 1,
        var charset: Charset? = null,
        var dest: String? = null,
        var fontSize: Int = 24,
        var outline: Int = -1,
    ) {
        fun copy() = State(flags, align, skip, uc, charset, dest, fontSize, outline)
    }

    private var pos = 0
    private var state = State()
    private val stack = ArrayList<State>()
    private var ansi: Charset = Charset.forName("windows-1252")
    private val fontCharsets = HashMap<Int, Charset>()
    private var fontInTable = -1
    private val pending = ByteArrayOutputStream()
    private var unicodeSkip = 0

    private val blocks = ArrayList<Block>()
    private val text = StringBuilder()
    private val spans = ArrayList<Span>()
    private var runStart = 0
    private var runFlags = 0
    private var paraAlign = Align.Default
    private var paraSize = 24
    private var paraOutline = -1
    private val info = HashMap<String, StringBuilder>()

    fun parse(): Book {
        if (!String(src, 0, minOf(src.size, 5), Charsets.ISO_8859_1).startsWith("{\\rtf")) throw UnsupportedBookException("Not RTF")
        while (pos < src.size) {
            val c = src[pos].toInt() and 0xFF
            when (c) {
                '{'.code -> { flushBytes(); pos++; stack += state.copy() }
                '}'.code -> { flushBytes(); pos++; endGroup() }
                '\\'.code -> control()
                '\r'.code, '\n'.code -> pos++
                else -> { pos++; byte(c) }
            }
        }
        flushBytes()
        endParagraph()
        val split = splitChapters(blocks)
        val title = info["title"]?.toString()?.trim().orEmpty()
        val author = info["author"]?.toString()?.trim()
        return Book(BookMetadata(title, listOfNotNull(author?.takeIf { it.isNotEmpty() })), split.chapters, split.toc, emptyMap())
    }

    private fun endGroup() {
        if (stack.isEmpty()) return
        val wasFontTable = state.dest == "fonttbl"
        state = stack.removeAt(stack.lastIndex)
        if (wasFontTable && state.dest != "fonttbl") fontInTable = -1
    }

    private fun control() {
        pos++
        if (pos >= src.size) return
        val c = src[pos].toInt() and 0xFF
        if (!(c.toChar().isLetter() && c < 128)) {
            pos++
            when (c.toChar()) {
                '\'' -> {
                    val hex = String(src, pos, minOf(2, src.size - pos), Charsets.ISO_8859_1)
                    pos += 2
                    val value = hex.toIntOrNull(16) ?: return
                    if (unicodeSkip > 0) { unicodeSkip--; return }
                    if (!state.skip) pending.write(value)
                }
                '*' -> state.skip = true
                '~' -> char(' ')
                '_' -> char('‑')
                '-' -> Unit
                '\\', '{', '}' -> byte(c)
                '\n', '\r' -> { flushBytes(); endParagraph() }
                else -> Unit
            }
            return
        }
        val start = pos
        while (pos < src.size && (src[pos].toInt() and 0xFF).let { it < 128 && it.toChar().isLetter() }) pos++
        val word = String(src, start, pos - start, Charsets.ISO_8859_1)
        var param: Int? = null
        if (pos < src.size && (src[pos] == '-'.code.toByte() || src[pos].toInt().toChar().isDigit())) {
            val ps = pos
            pos++
            while (pos < src.size && src[pos].toInt().toChar().isDigit()) pos++
            param = String(src, ps, pos - ps, Charsets.ISO_8859_1).toIntOrNull()
        }
        if (pos < src.size && src[pos] == ' '.code.toByte()) pos++
        if (word != "u" && unicodeSkip > 0) unicodeSkip = 0
        flushBytes()
        word(word, param)
    }

    private fun word(word: String, param: Int?) {
        val on = param != 0
        when (word) {
            "ansicpg" -> param?.let { cp -> codepage(cp)?.let { ansi = it } }
            "fonttbl" -> state.dest = "fonttbl"
            "f" -> if (state.dest == "fonttbl") fontInTable = param ?: -1 else state.charset = fontCharsets[param]
            "fcharset" -> if (fontInTable >= 0) charsetOf(param ?: 0)?.let { fontCharsets[fontInTable] = it }
            "colortbl", "stylesheet", "pict", "header", "headerl", "headerr", "headerf", "footer", "footerl", "footerr",
            "footerf", "fldinst", "object", "themedata", "colorschememapping", "latentstyles", "datastore",
            "xmlnstbl", "listtable", "listoverridetable", "rsidtbl", "generator", "footnote", "bkmkstart", "bkmkend",
            "pgdsctbl", "revtbl", "shppict", "nonshppict", "operator", "company",
            -> state.skip = true
            "info" -> state.dest = "info"
            "title", "author" -> if (state.dest == "info") { state.dest = word; info[word] = StringBuilder() }
            "par", "sect" -> endParagraph()
            "page" -> endParagraph()
            "line" -> char('\n')
            "tab" -> char(' ')
            "cell", "row" -> char(' ')
            "emdash" -> char('—')
            "endash" -> char('–')
            "bullet" -> char('•')
            "lquote" -> char('‘')
            "rquote" -> char('’')
            "ldblquote" -> char('“')
            "rdblquote" -> char('”')
            "uc" -> state.uc = param ?: 1
            "u" -> {
                var v = param ?: return
                if (v < 0) v += 65536
                char(v.toChar())
                unicodeSkip = state.uc
            }
            "pard" -> { state.align = Align.Default; state.outline = -1 }
            "plain" -> { state.flags = 0; state.fontSize = 24 }
            "b" -> flag(Span.BOLD, on)
            "i" -> flag(Span.ITALIC, on)
            "strike" -> flag(Span.STRIKE, on)
            "super" -> flag(Span.SUPERSCRIPT, on)
            "sub" -> flag(Span.SUBSCRIPT, on)
            "nosupersub" -> { flag(Span.SUPERSCRIPT, false); flag(Span.SUBSCRIPT, false) }
            "fs" -> state.fontSize = param ?: 24
            "outlinelevel" -> state.outline = param ?: -1
            "qc" -> state.align = Align.Center
            "qr" -> state.align = Align.End
            "qj" -> state.align = Align.Justify
            "ql" -> state.align = Align.Start
        }
    }

    private fun flag(flag: Int, on: Boolean) {
        state.flags = if (on) state.flags or flag else state.flags and flag.inv()
    }

    private fun byte(c: Int) {
        if (unicodeSkip > 0) { unicodeSkip--; return }
        if (!state.skip) pending.write(c)
    }

    private fun flushBytes() {
        if (pending.size() == 0) return
        val s = String(pending.toByteArray(), state.charset ?: ansi)
        pending.reset()
        s.forEach(::char)
    }

    private fun char(ch: Char) {
        if (state.skip) return
        val dest = state.dest
        if (dest == "title" || dest == "author") { info[dest]?.append(ch); return }
        if (dest == "info" || dest == "fonttbl") return
        if (text.isEmpty()) { paraAlign = state.align; paraSize = state.fontSize; paraOutline = state.outline }
        if (state.flags != runFlags) {
            closeRun()
            runFlags = state.flags
        }
        if (ch == ' ' && (text.isEmpty() || text.last() == ' ' || text.last() == '\n')) return
        text.append(ch)
    }

    private fun closeRun() {
        if (runFlags != 0 && text.length > runStart) spans += Span(runStart, text.length, runFlags)
        runStart = text.length
    }

    private fun endParagraph() {
        closeRun()
        val value = text.toString().trimEnd()
        if (value.isNotBlank()) {
            val bold = spans.any { it.has(Span.BOLD) && it.start == 0 && it.end >= value.length }
            val kind = when {
                paraOutline == 0 -> TextKind.Heading1
                paraOutline == 1 -> TextKind.Heading2
                paraOutline > 1 -> TextKind.Heading3
                // Converters write titles as short, bold, large or centered paragraphs.
                bold && value.length < 120 && paraSize >= 32 -> TextKind.Heading1
                bold && value.length < 120 && paraSize >= 28 -> TextKind.Heading2
                else -> TextKind.Body
            }
            val clipped = spans.mapNotNull { s -> minOf(s.end, value.length).takeIf { it > s.start }?.let { s.copy(end = it) } }
            blocks += Block.Text(value, if (kind.isHeading) emptyList() else clipped, kind, paraAlign)
        }
        text.setLength(0)
        spans.clear()
        runStart = 0
        runFlags = state.flags
    }

    private fun charsetOf(fcharset: Int): Charset? = when (fcharset) {
        0 -> null
        204 -> codepage(1251)
        238 -> codepage(1250)
        161 -> codepage(1253)
        162 -> codepage(1254)
        177 -> codepage(1255)
        178 -> codepage(1256)
        186 -> codepage(1257)
        163 -> codepage(1258)
        222 -> codepage(874)
        128 -> codepage(932)
        129 -> codepage(949)
        134 -> codepage(936)
        136 -> codepage(950)
        else -> null
    }

    private fun codepage(cp: Int): Charset? = runCatching {
        when (cp) {
            65001 -> Charsets.UTF_8
            932 -> Charset.forName("Shift_JIS")
            936 -> Charset.forName("GBK")
            949 -> Charset.forName("EUC-KR")
            950 -> Charset.forName("Big5")
            874 -> Charset.forName("x-windows-874")
            10000 -> Charset.forName("x-MacRoman")
            else -> Charset.forName("windows-$cp")
        }
    }.getOrNull()
}
