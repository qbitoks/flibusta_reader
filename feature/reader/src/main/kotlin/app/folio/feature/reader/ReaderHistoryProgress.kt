package app.folio.feature.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.folio.core.designsystem.Folio
import app.folio.core.designsystem.FolioType

/** Equal reserved side slots keep progress centred even as history arrows expire. */
@Composable
internal fun ReaderHistoryProgress(vm: ReaderViewModel, percent: String, onPosition: () -> Unit) {
    val c = Folio.colors
    BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        val side = (maxWidth * 0.24f).coerceIn(48.dp, 88.dp)
        val visible = vm.historyVisibility.visible
        vm.history.back.lastOrNull()?.let { target ->
            AnimatedVisibility(visible, Modifier.align(Alignment.CenterStart).width(side), enter = fadeIn(), exit = fadeOut()) {
                HistoryArrow(vm.pageNumberOf(target), true, vm::historyBack)
            }
        }
        Row(Modifier.align(Alignment.Center).padding(horizontal = side).heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.reader_position_title), onClick = onPosition),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Text(percent, style = FolioType.footnote, color = c.secondaryLabel, maxLines = 2,
                overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.weight(1f, fill = false))
            Icon(Icons.Rounded.KeyboardArrowDown, null, tint = c.secondaryLabel, modifier = Modifier.size(16.dp))
        }
        vm.history.forward.lastOrNull()?.let { target ->
            AnimatedVisibility(visible, Modifier.align(Alignment.CenterEnd).width(side), enter = fadeIn(), exit = fadeOut()) {
                HistoryArrow(vm.pageNumberOf(target), false, vm::historyForward)
            }
        }
    }
}

@Composable
private fun HistoryArrow(page: Int?, back: Boolean, onClick: () -> Unit) {
    val description = page?.let { stringResource(if (back) R.string.reader_return_page else R.string.reader_forward_page, it) }
        ?: stringResource(if (back) R.string.reader_history_back else R.string.reader_history_forward)
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick)
        .semantics(mergeDescendants = true) { contentDescription = description },
        horizontalArrangement = if (back) Arrangement.Start else Arrangement.End,
        verticalAlignment = Alignment.CenterVertically) {
        @Composable fun Arrow() = Icon(if (back) Icons.AutoMirrored.Rounded.Undo else Icons.AutoMirrored.Rounded.Redo,
            null, tint = Folio.colors.label, modifier = Modifier.size(20.dp))
        if (back) Arrow()
        if (page != null) Text(page.toString(), style = FolioType.footnote, color = Folio.colors.label,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false).padding(horizontal = 4.dp))
        if (!back) Arrow()
    }
}
