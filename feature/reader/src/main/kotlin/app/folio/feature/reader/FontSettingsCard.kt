package app.folio.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.folio.core.data.settings.BookOverrides
import app.folio.core.data.settings.TextSet
import app.folio.core.designsystem.Folio
import app.folio.core.designsystem.FolioType
import app.folio.core.designsystem.ReaderFont
import app.folio.core.designsystem.components.GroupedCard

/** Font group: one scrolling row of «Аа» tiles drawn in each font; bold sits in the header to save a row. */
@Composable
internal fun FontSettingsCard(text: TextSet, own: Boolean, update: (TextSet) -> Unit) {
    Column {
        SettingsGroupHeader(stringResource(R.string.settings_font), own = own) {
            BoldButton(text.boldText) { update(text.copy(boldText = it)) }
        }
        GroupedCard {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReaderFont.entries.forEach { font ->
                    FontTile(font, text.boldText, font.key == ReaderFont.of(text.font).key) {
                        update(text.copy(font = font.key))
                    }
                }
            }
        }
    }
}

/** A choice, unlike the cyclic spacing tiles: the shared tile with «Аа» drawn in the font. */
@Composable
private fun FontTile(font: ReaderFont, bold: Boolean, selected: Boolean, onClick: () -> Unit) =
    ChoiceTile(font.displayName, selected, Folio.colors.card, Modifier.width(84.dp), onClick = onClick) {
        Text("Аа", color = Folio.colors.label, maxLines = 1, style = FolioType.body.copy(fontFamily = font.composeFamily,
            fontSize = 24.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal))
    }

/** Compact bold toggle; 44 dp touch target around a smaller filled/outlined glyph. */
@Composable
private fun BoldButton(on: Boolean, onChange: (Boolean) -> Unit) {
    val c = Folio.colors
    val label = stringResource(R.string.settings_bold_text)
    val shape = RoundedCornerShape(10.dp)
    Box(Modifier.padding(start = 12.dp).size(44.dp)
        .toggleable(on, role = Role.Switch, onValueChange = onChange)
        .semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(34.dp).clip(shape).background(if (on) c.label else c.fill), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.settings_bold_glyph), color = if (on) c.card else c.label,
                style = FolioType.body.copy(fontWeight = FontWeight.Bold))
        }
    }
}
