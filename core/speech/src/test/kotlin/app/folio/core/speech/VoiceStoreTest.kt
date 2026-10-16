package app.folio.core.speech

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.Locale

class VoiceStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun archive(vararg entries: Pair<String, String?>): File {
        val file = tmp.newFile("voice.tar.bz2")
        TarArchiveOutputStream(BZip2CompressorOutputStream(file.outputStream())).use { tar ->
            for ((name, text) in entries) {
                val bytes = text?.toByteArray() ?: ByteArray(0)
                tar.putArchiveEntry(TarArchiveEntry(name).apply { if (text != null) size = bytes.size.toLong() })
                tar.write(bytes)
                tar.closeArchiveEntry()
            }
        }
        return file
    }

    @Test fun unpacksWithoutTheTopFolderIntoACompleteVoice() {
        val file = archive(
            "vits-piper-ru_RU-irina-medium-int8/" to null,
            "vits-piper-ru_RU-irina-medium-int8/ru_RU-irina-medium.onnx" to "model",
            "vits-piper-ru_RU-irina-medium-int8/tokens.txt" to "a 1",
            "vits-piper-ru_RU-irina-medium-int8/espeak-ng-data/" to null,
            "vits-piper-ru_RU-irina-medium-int8/espeak-ng-data/ru_dict" to "dict",
        )
        val into = File(tmp.root, "out")
        unpack(file, into)
        val voice = checkNotNull(voiceFiles(into))
        assertEquals("ru_RU-irina-medium.onnx", voice.model.name)
        assertEquals("dict", File(voice.espeakData, "ru_dict").readText())
    }

    @Test fun refusesEntriesOutsideTheVoiceFolder() {
        val file = archive("voice/../../evil.txt" to "x")
        try {
            unpack(file, File(tmp.root, "out"))
            fail("Path traversal accepted")
        } catch (_: IOException) {
        }
        assertFalse(File(tmp.root.parentFile, "evil.txt").exists())
    }

    @Test fun incompleteVoiceIsNotInstalled() {
        val dir = tmp.newFolder("v")
        File(dir, "model.onnx").writeText("m")
        assertNull(voiceFiles(dir))
    }

    @Test fun voicesForLanguagePutTheBookRegionFirst() {
        val british = piperVoicesFor(Locale.forLanguageTag("en-GB"))
        assertTrue(british.all { it.language == "en" })
        assertEquals("en-GB", british.first().tag)
        assertTrue(piperVoicesFor(Locale.forLanguageTag("ru")).map { it.id }.contains("ru_RU-irina-medium"))
        assertTrue(piperVoicesFor(Locale.forLanguageTag("ja")).isEmpty())
    }

    @Test fun catalogUrlsAreHttpsInt8Archives() {
        assertTrue(PiperCatalog.all { it.url.startsWith("https://") && it.url.endsWith("-int8.tar.bz2") })
        assertEquals(PiperCatalog.size, PiperCatalog.map { it.id }.toSet().size)
    }

    @Test fun sileroModelIsCompleteWithItsOnnxFile() {
        val dir = tmp.newFolder("silero")
        assertFalse(complete(SileroModel, dir))
        File(dir, SileroModel.FILE).writeText("m")
        assertTrue(complete(SileroModel, dir))
        assertTrue(SileroModel.url.startsWith("https://"))
        assertEquals(OfflineVoices.size, OfflineVoices.map { it.id }.toSet().size)
    }
}
