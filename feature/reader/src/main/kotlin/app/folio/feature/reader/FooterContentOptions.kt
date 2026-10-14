package app.folio.feature.reader

import app.folio.core.data.settings.FooterPage

internal fun nextFooterPage(page: FooterPage): FooterPage = when (page) {
    FooterPage.NumberOfTotal -> FooterPage.Percent
    FooterPage.Percent -> FooterPage.Number
    FooterPage.Number -> FooterPage.None
    FooterPage.None -> FooterPage.NumberOfTotal
}

/** Keep the row stable as live values change; reserve readable cells for larger system text. */
internal fun footerContentColumns(widthDp: Float, fontScale: Float): Int =
    if (widthDp >= 4 * 64 * fontScale.coerceAtLeast(1f) + 12) 4 else 2
