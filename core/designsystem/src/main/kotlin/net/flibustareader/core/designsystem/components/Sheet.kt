package net.flibustareader.core.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.flibustareader.core.designsystem.Flibusta
import net.flibustareader.core.designsystem.FlibustaType

/**
 * Full-height sheet as in Apple Books: starts below the status bar, 23 dp top corners,
 * no drag handle. Header content is supplied by [SheetHeader].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlibustaSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = Flibusta.colors.surface,
    scrimColor: Color = Color.Black.copy(alpha = 0.32f),
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 23.dp, topEnd = 23.dp),
        containerColor = containerColor,
        contentColor = Flibusta.colors.label,
        scrimColor = scrimColor,
        dragHandle = null,
        modifier = modifier,
    ) {
        Column(Modifier.fillMaxWidth(), content = content)
    }
}

/**
 * Sheet header row, 44 dp buttons at the edges.
 * [centered] puts the title in the middle («Поиск в книге», «Настройка темы»); otherwise it
 * leads as a large serif title («Коллекции»).
 */
@Composable
fun SheetHeader(
    title: String,
    modifier: Modifier = Modifier,
    centered: Boolean = true,
    titleStyle: TextStyle = if (centered) FlibustaType.headline else FlibustaType.sheetTitle,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        if (leading != null) Box(Modifier.align(Alignment.CenterStart)) { leading() }
        Text(
            title,
            style = titleStyle,
            color = Flibusta.colors.label,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = if (centered) {
                Modifier.align(Alignment.Center).padding(horizontal = 56.dp)
            } else {
                Modifier.align(Alignment.CenterStart).padding(start = if (leading != null) 56.dp else 20.dp, end = 120.dp)
            },
        )
        if (trailing != null) {
            Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) { trailing() }
        }
    }
}
