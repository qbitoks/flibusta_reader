package app.folio.core.speech

import java.util.Locale

/**
 * The Silero v5 CIS base model (MIT), converted to ONNX and int8 by `scripts/silero/export_onnx.py`:
 * one file for all [SileroSpeakers], 48 kHz. Hosted as a Folio GitHub release asset.
 */
object SileroModel : OfflineVoice {
    override val id = "silero-v5-cis-base-int8"
    override val folder = "silero"
    override val url = "https://github.com/qbitoks/folio/releases/download/voices-silero-v5/silero-v5-cis-base-int8.onnx"
    override val bytes = 64_693_958L
    const val FILE = "model.onnx"
    const val SAMPLE_RATE = 48_000

    /** The model reads Russian only here; its other CIS speakers are not offered. */
    fun speaks(locale: Locale) = locale.language == "ru"
}

/** A Russian speaker of [SileroModel]; [index] is its id in the model. */
data class SileroSpeaker(val key: String, val index: Long) {
    val name: String get() = key.removePrefix("ru_").replaceFirstChar { it.titlecase(Locale.ROOT) }
}

/** The model's `ru_*` speakers (indices from Silero's `v5_cis_base` speaker table). */
val SileroSpeakers = listOf(
    "aigul", "albina", "alexandr", "alfia", "alfia2", "bogdan", "dmitriy", "eduard", "ekaterina", "gamat",
    "igor", "karina", "kejilgan", "kermen", "marat", "miyau", "nurgul", "oksana", "onaoy", "ramilia",
    "roman", "safarhuja", "saida", "sibday", "vika", "zara", "zhadyra", "zhazira", "zinaida",
).mapIndexed { i, name -> SileroSpeaker("ru_$name", 20L + i) }

val DefaultSileroSpeaker = SileroSpeakers.first { it.key == "ru_dmitriy" }

fun sileroSpeaker(key: String?): SileroSpeaker? = SileroSpeakers.firstOrNull { it.key == key }

/** Silero's symbol table: the token id of a character is its index here. */
private const val SYMBOLS = "|||!'+,-.:;?hабвгдежзийклмнопрстуфхцчшщъыьэюяёєіїјўґғҕҗҙқҝҡңҥҫүұҳҷҹһӑӗәӝӟӣӥӧөӯӱӳӵӏ—… "

/** Longest phrase fed to the model at once; longer ones are split (its position table holds ~60 s of audio). */
internal const val SILERO_MAX_CHARS = 350

/**
 * Token ids for [text]: lower-cased, numbers spelled out in Russian, dashes and ellipses unified,
 * characters the model has no symbol for dropped; wrapped in the model's start (2) and end (1) ids.
 */
internal fun sileroTokens(text: String): LongArray {
    val clean = sileroText(text)
    val ids = ArrayList<Long>(clean.length + 2)
    ids += 2L
    for (c in clean) ids += SYMBOLS.indexOf(c).toLong()
    ids += 1L
    return ids.toLongArray()
}

/** [text] as the model reads it (see [sileroTokens]): only characters from its symbol table, single spaces. */
internal fun sileroText(text: String): String = spellNumbers(text.lowercase(Locale.ROOT))
    .replace('–', '—').replace('‑', '-').replace("...", "…")
    .map { c -> if (c.isWhitespace()) ' ' else c }
    .filter { c -> SYMBOLS.indexOf(c) >= 3 }
    .joinToString("")
    .replace(Regex(" {2,}"), " ")
    .trim()

/** Splits a phrase longer than [max] characters at the last comma or space that fits. */
internal fun sileroChunks(text: String, max: Int = SILERO_MAX_CHARS): List<String> {
    val chunks = mutableListOf<String>()
    var rest = text.trim()
    while (rest.length > max) {
        val window = rest.substring(0, max)
        val cut = listOf(window.lastIndexOf(", "), window.lastIndexOf("; "), window.lastIndexOf(" — "))
            .maxOrNull()?.takeIf { it > max / 3 }?.plus(1)
            ?: window.lastIndexOf(' ').takeIf { it > 0 }
            ?: max
        chunks += rest.substring(0, cut).trim()
        rest = rest.substring(cut).trim()
    }
    if (rest.isNotEmpty()) chunks += rest
    return chunks
}
