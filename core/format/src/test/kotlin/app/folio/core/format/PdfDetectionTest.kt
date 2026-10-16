package app.folio.core.format

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PdfDetectionTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun detect(bytes: ByteArray) = BookParser.detect(tmp.newFile().apply { writeBytes(bytes) })

    @Test
    fun pdfByHeader() {
        val bytes = "%PDF-1.7\n%".toByteArray() + byteArrayOf(0xE2.toByte(), 0xE3.toByte(), 0xCF.toByte(), 0xD3.toByte(), 0, 1, 2) + "\n1 0 obj".toByteArray()
        assertEquals(BookFormat.Pdf, detect(bytes))
    }

    @Test
    fun pdfAfterLeadingJunk() {
        assertEquals(BookFormat.Pdf, detect(ByteArray(300) { 0 } + "%PDF-1.4\n".toByteArray()))
    }

    @Test
    fun textMentioningPdfStaysText() {
        assertEquals(BookFormat.Text, detect("Save it as %PDF later.\nSecond line.".toByteArray()))
    }

    @Test(expected = UnsupportedBookException::class)
    fun pdfIsNotReflowed() {
        BookParser.parse(tmp.newFile(), BookFormat.Pdf) { _, _ -> }
    }
}
