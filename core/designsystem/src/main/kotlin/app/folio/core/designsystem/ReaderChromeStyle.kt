package app.folio.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.folio.core.data.settings.ChromeStyle

/** Shared typography for the page and its preset preview. */
@Composable
fun readerChromeTextStyle(style: ChromeStyle, palette: ReaderPalette, bookFont: String): TextStyle {
    val family = remember(bookFont) { ReaderFont.of(bookFont).composeFamily }
    return when (style) {
        ChromeStyle.Quiet -> TextStyle(fontFamily = UiSans, fontSize = 12.sp, lineHeight = 16.sp,
            color = lerp(palette.background, palette.text, 0.58f))
        ChromeStyle.Classic -> TextStyle(fontFamily = UiSans, fontSize = 15.sp, lineHeight = 20.sp, color = palette.secondary)
        ChromeStyle.Book -> TextStyle(fontFamily = family, fontSize = 13.sp, lineHeight = 18.sp,
            color = lerp(palette.background, palette.text, 0.64f), fontFeatureSettings = "tnum, smcp")
        ChromeStyle.Clear -> TextStyle(fontFamily = UiSans, fontSize = 14.sp, lineHeight = 19.sp,
            fontWeight = FontWeight.Medium, color = lerp(palette.background, palette.text, 0.85f))
    }.copy(fontFeatureSettings = if (style == ChromeStyle.Book) "tnum, smcp" else "tnum")
}

fun readerChromeSeparator(style: ChromeStyle): String = when (style) {
    ChromeStyle.Quiet -> "  "
    ChromeStyle.Classic, ChromeStyle.Book -> " · "
    ChromeStyle.Clear -> " / "
}
