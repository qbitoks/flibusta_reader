package app.folio.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.folio.core.data.settings.ChromeStyle
import app.folio.core.data.settings.LineAlign
import app.folio.core.data.settings.LineLayout
import app.folio.core.data.settings.PageHeader
import app.folio.core.data.settings.PageTurn
import app.folio.core.data.settings.ReaderSettings
import app.folio.core.designsystem.ReaderPalette
import app.folio.core.designsystem.readerChromeTextStyle

/** A reader line as drawn: its visual preset with «Свои параметры» applied. */
@Immutable
internal class LineLook(val style: TextStyle, val layout: LineLayout, val ruleColor: Color) {
    fun text(value: String): String = if (layout.uppercase) value.uppercase() else value

    val textAlign: TextAlign get() = when (layout.align) {
        LineAlign.Start -> TextAlign.Start
        LineAlign.End -> TextAlign.End
        LineAlign.Center, LineAlign.Spread -> TextAlign.Center
    }

    val alignment: Alignment get() = when (layout.align) {
        LineAlign.Start -> Alignment.CenterStart
        LineAlign.End -> Alignment.CenterEnd
        LineAlign.Center, LineAlign.Spread -> Alignment.Center
    }

    /** Side room of the title: centred titles keep clear of the corners, aligned ones line up with the text. */
    fun titleMargin(centred: Dp, textMargin: Dp): Dp = if (layout.align == LineAlign.Center) centred else maxOf(centred, textMargin)
}

@Composable
internal fun lineLook(preset: ChromeStyle, layout: LineLayout, palette: ReaderPalette, font: String): LineLook {
    val base = readerChromeTextStyle(preset, palette, font)
    val scale = layout.sizePct / 100f
    val opacity = layout.opacityPct / 100f
    val style = base.copy(
        fontSize = base.fontSize * scale,
        lineHeight = base.lineHeight * scale,
        color = base.color.copy(alpha = base.color.alpha * opacity),
        letterSpacing = if (layout.uppercase) 0.06.em else base.letterSpacing,
    )
    return LineLook(style, layout, palette.text.copy(alpha = 0.16f * opacity))
}

@Composable
internal fun headerLook(settings: ReaderSettings, palette: ReaderPalette): LineLook =
    lineLook(settings.chromeStyles.header, settings.shownHeaderLayout, palette, settings.style.font)

@Composable
internal fun footerLook(settings: ReaderSettings, palette: ReaderPalette): LineLook =
    lineLook(settings.chromeStyles.footer, settings.shownFooterLayout, palette, settings.style.font)

/** Hairline between a line and the text. */
@Composable
internal fun LineRule(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(0.5.dp).background(color))
}

/** Room a rule above the bottom line adds to it. */
internal val FooterRuleSpace = 8.dp

/** Space the lines take from the text area; changing it re-paginates, as a font change does. */
internal fun textInsets(settings: ReaderSettings, headerLineHeight: Dp, footerLineHeight: Dp): Pair<Dp, Dp> {
    val header = settings.shownHeaderLayout
    val footer = settings.shownFooterLayout
    if (settings.pageTurn == PageTurn.Scroll) return (
        if (settings.header == PageHeader.None) 12.dp else lineBlockGeometry(header, headerLineHeight).textInset
    ) to (if (settings.shownFooter.isEmpty) 20.dp else lineBlockGeometry(footer, footerLineHeight).textInset)
    // Without a line above, the text starts right under the status bar area; without anything below, it runs to the bottom.
    val top = if (settings.header == PageHeader.None) 12.dp
        else (headerLineHeight + 16.dp).coerceAtLeast(32.dp) + 22.dp + header.edge.dp + header.gap.dp
    val bottom = if (settings.shownFooter.isEmpty) 20.dp
        else footerLineHeight + 48.dp + footer.edge.dp + footer.gap.dp + (if (footer.rule) FooterRuleSpace else 0.dp)
    return top to bottom
}
