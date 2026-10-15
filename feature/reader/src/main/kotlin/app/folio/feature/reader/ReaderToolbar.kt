package app.folio.feature.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.folio.core.designsystem.Folio
import app.folio.core.designsystem.FolioType
import app.folio.core.designsystem.ReaderPalette
import app.folio.core.designsystem.components.glass

/** Consume blank panel taps so they cannot reach the reading surface below. */
internal fun Modifier.readerPanelInput(): Modifier = pointerInput(Unit) { detectTapGestures { } }

@Composable
internal fun ReaderTopBar(
    title: String, author: String, palette: ReaderPalette, topInset: Dp,
    onClose: () -> Unit, onShare: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().glass(RectangleShape, tint = palette.background.copy(alpha = 0.92f))
        .readerPanelInput().padding(top = topInset).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val shareDescription = stringResource(R.string.reader_share)
        Column(Modifier.weight(1f).heightIn(min = 48.dp).clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = shareDescription, onClick = onShare)
            .semantics { contentDescription = "$title. $author. $shareDescription" }
            .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.Start) {
            Text(title, style = FolioType.headline, color = Folio.colors.label,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (author.isNotBlank()) Text(author, style = FolioType.subheadline, color = Folio.colors.secondaryLabel,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        RoundButton(Icons.Rounded.Close, stringResource(R.string.reader_close),
            palette.menuCapsule(), Folio.colors.label, size = 48.dp, onClick = onClose)
    }
}
