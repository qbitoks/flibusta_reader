package app.folio.feature.catalog

import app.folio.core.data.catalog.DownloadItem
import app.folio.core.data.db.DownloadState

/**
 * How the book sheet splits a book's formats: [capsule] is the last format the user picked (a download started
 * or a copy in the library), else the catalog's; the ••• menu lists the [rest] of the real books and the
 * [conversions] under «Другие форматы», and offers to cancel every download in [started].
 */
internal class BookFormats(
    val capsule: Pair<String, DownloadItem?>,
    val rest: List<Pair<String, DownloadItem?>>,
    val conversions: List<Pair<String, DownloadItem?>>,
    val started: List<Pair<String, DownloadItem?>>,
) {
    val hasMenu: Boolean get() = rest.isNotEmpty() || conversions.isNotEmpty() || started.isNotEmpty()
}

/** [formats] come catalog's format first; null when the book has none. */
internal fun bookFormats(formats: List<Pair<String, DownloadItem?>>): BookFormats? {
    if (formats.isEmpty()) return null
    // A finished download whose book was deleted from the library is no longer a choice.
    val picked = formats.filter { (_, item) -> item != null && (item.state != DownloadState.Done || item.readable) }
    val capsule = picked.maxByOrNull { it.second!!.entity.createdAt } ?: formats.first()
    val others = formats - capsule
    // Without EPUB or FB2 there is nothing better to set the conversions apart from.
    val grouped = formats.any { it.first in mainFormats }
    return BookFormats(
        capsule = capsule,
        rest = if (grouped) others.filter { it.first in mainFormats } else others,
        conversions = if (grouped) others.filter { it.first !in mainFormats } else emptyList(),
        started = formats.filter { (_, item) -> item != null && item.state != DownloadState.Done },
    )
}

/** The real books; MOBI, DOCX, RTF, HTML, TXT and PDF (fixed pages) are conversions, never tried on a refusal. */
internal val mainFormats = setOf("EPUB", "FB2")
