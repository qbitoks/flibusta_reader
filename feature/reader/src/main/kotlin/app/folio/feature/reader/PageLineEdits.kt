package app.folio.feature.reader

import app.folio.core.data.settings.*

/** First edits start from what is drawn, rather than reviving disabled legacy overrides. */
internal fun ReaderSettings.editHeaderLine(change: (LineLayout) -> LineLayout): ReaderSettings = copy(
    headerLayout = change((if (headerLayout.customize) headerLayout else LineLayout.HEADER).copy(customize = true)),
    headerUnder = shownHeaderUnder,
)

internal fun ReaderSettings.editFooterLine(change: (LineLayout) -> LineLayout): ReaderSettings = copy(
    footerLayout = change((if (footerLayout.customize) footerLayout else LineLayout.FOOTER).copy(customize = true)),
    footer = shownFooter,
)

internal fun ReaderSettings.editFooterContent(change: (PageFooter) -> PageFooter): ReaderSettings =
    editFooterLine { it }.let { it.copy(footer = change(it.footer)) }

internal fun ReaderSettings.editLineBoundary(top: Boolean, value: FooterStyle): ReaderSettings {
    require(value != FooterStyle.None)
    return if (top) editHeaderLine { it }.copy(headerUnder = value)
        else editFooterContent { it.copy(style = value) }
}
