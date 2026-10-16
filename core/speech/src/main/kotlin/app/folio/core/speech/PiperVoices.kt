package app.folio.core.speech

import java.util.Locale

/** One downloadable Piper voice (sherpa-onnx `tts-models` release, int8: ~20 MB, quality close to fp32). */
data class PiperVoice(
    /** Piper name, e.g. `ru_RU-irina-medium`; also the folder under `files/voices/piper/`. */
    val id: String,
    val speaker: String,
    val bytes: Long,
) {
    /** BCP 47 tag of the voice, e.g. `ru-RU`. */
    val tag: String get() = id.substringBefore('-').replace('_', '-')
    val language: String get() = id.substringBefore('_')
    val url: String get() = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-$id-int8.tar.bz2"
}

/** The voices offered for download; single-speaker, medium quality where Piper has one. */
val PiperCatalog = listOf(
    PiperVoice("ru_RU-irina-medium", "Irina", 21_149_417),
    PiperVoice("ru_RU-denis-medium", "Denis", 21_058_905),
    PiperVoice("ru_RU-dmitri-medium", "Dmitri", 21_129_441),
    PiperVoice("ru_RU-ruslan-medium", "Ruslan", 21_127_907),
    PiperVoice("uk_UA-lada-x_low", "Lada", 13_332_976),
    PiperVoice("en_US-amy-medium", "Amy", 21_028_122),
    PiperVoice("en_US-lessac-medium", "Lessac", 20_969_179),
    PiperVoice("en_US-ryan-medium", "Ryan", 21_083_446),
    PiperVoice("en_GB-alan-medium", "Alan", 21_103_831),
    PiperVoice("en_GB-jenny_dioco-medium", "Jenny", 20_950_036),
    PiperVoice("de_DE-thorsten-medium", "Thorsten", 20_949_833),
    PiperVoice("fr_FR-siwis-medium", "Siwis", 20_914_888),
    PiperVoice("es_ES-davefx-medium", "Davefx", 21_171_632),
    PiperVoice("it_IT-paola-medium", "Paola", 21_143_212),
    PiperVoice("pl_PL-gosia-medium", "Gosia", 21_109_262),
)

fun piperVoice(id: String): PiperVoice? = PiperCatalog.firstOrNull { it.id == id }

/** Catalog voices that speak [locale]'s language, the book's region first. */
fun piperVoicesFor(locale: Locale): List<PiperVoice> =
    PiperCatalog.filter { it.language == locale.language }.sortedByDescending { it.tag.equals(locale.toLanguageTag(), ignoreCase = true) }
