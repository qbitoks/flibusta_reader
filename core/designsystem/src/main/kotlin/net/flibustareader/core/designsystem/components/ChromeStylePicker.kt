package net.flibustareader.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.flibustareader.core.data.settings.ChromeStyle
import net.flibustareader.core.designsystem.Flibusta
import net.flibustareader.core.designsystem.FlibustaType
import net.flibustareader.core.designsystem.R
import net.flibustareader.core.designsystem.ReaderPalette
import net.flibustareader.core.designsystem.readerChromeTextStyle

@Composable
fun ChromeStylePicker(
    selected: ChromeStyle,
    header: Boolean,
    palette: ReaderPalette,
    bookFont: String,
    onSelect: (ChromeStyle) -> Unit,
) {
    val c = Flibusta.colors
    val fontScale = LocalDensity.current.fontScale
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ChromeStyle.entries.forEach { preset ->
            val shape = RoundedCornerShape(16.dp)
            val textStyle = readerChromeTextStyle(preset, palette, bookFont)
            val label = stringResource(when (preset) {
                ChromeStyle.Quiet -> R.string.chrome_quiet
                ChromeStyle.Classic -> R.string.chrome_classic
                ChromeStyle.Book -> R.string.chrome_book
                ChromeStyle.Clear -> R.string.chrome_clear
            })
            Column(
                Modifier.width(170.dp * fontScale.coerceAtLeast(1f)).clip(shape)
                    .background(c.card)
                    .border(BorderStroke(if (preset == selected) 2.dp else 0.5.dp,
                        if (preset == selected) c.label else c.separator), shape)
                    .selectable(preset == selected, role = Role.RadioButton) { onSelect(preset) }
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth().height((70.dp * fontScale.coerceAtLeast(1f))).clip(RoundedCornerShape(8.dp))
                        .background(palette.background).padding(6.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    if (header) Text(stringResource(R.string.chrome_preview_chapter), style = textStyle,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    repeat(2) {
                        Box(Modifier.fillMaxWidth(if (it == 0) 1f else 0.75f).height(2.dp)
                            .background(palette.text.copy(alpha = 0.15f)))
                    }
                    if (!header) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("12:34", style = textStyle)
                        Text("42", style = textStyle)
                        ReaderBatteryLabel(78, false, palette, textStyle)
                    }
                }
                Text(label, style = FlibustaType.subheadline, color = c.label, maxLines = 1)
            }
        }
    }
}
