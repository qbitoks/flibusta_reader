package net.flibustareader.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.flibustareader.core.data.settings.*
import net.flibustareader.core.designsystem.*
import net.flibustareader.core.designsystem.components.ReaderBatteryLabel

/** Schematic page, using the same line typography as the reader itself. */
@Composable
internal fun PageSettingsPreview(settings: ReaderSettings, palette: ReaderPalette) {
    val headerStyle = readerChromeTextStyle(settings.chromeStyles.header, palette, settings.style.font)
    val footerStyle = readerChromeTextStyle(settings.chromeStyles.footer, palette, settings.style.font)
    val footer = settings.footer
    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp))
        .background(palette.background).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        if (settings.header != PageHeader.None) {
            Text(stringResource(if (settings.header == PageHeader.Book) R.string.settings_preview_book else R.string.settings_preview_chapter),
                style = headerStyle, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(5) { i ->
                Box(Modifier.fillMaxWidth(if (i == 4) 0.65f else 1f).height(3.dp)
                    .background(palette.text.copy(alpha = 0.14f)))
            }
        }
        if (!footer.isEmpty) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { if (footer.clock) Text("12:34", style = footerStyle) }
                val page = when (footer.page) {
                    FooterPage.None -> ""
                    FooterPage.Number -> "42"
                    FooterPage.NumberOfTotal -> stringResource(R.string.reader_page_of, 42, 586)
                    FooterPage.Percent -> stringResource(R.string.reader_percent, 7)
                }
                val remaining = if (footer.pagesLeft) pluralStringResource(R.plurals.reader_pages_left, 3, 3) else ""
                Text(listOf(page, remaining).filter { it.isNotBlank() }.joinToString(readerChromeSeparator(settings.chromeStyles.footer)),
                    style = footerStyle, textAlign = TextAlign.Center, modifier = Modifier.weight(2f))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    if (footer.battery) ReaderBatteryLabel(78, false, palette, footerStyle)
                }
            }
        }
    }
}
