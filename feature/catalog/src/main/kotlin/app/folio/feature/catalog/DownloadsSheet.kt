package app.folio.feature.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.folio.core.data.catalog.DownloadItem
import app.folio.core.data.catalog.NetworkErrors
import app.folio.core.data.db.DownloadState
import app.folio.core.designsystem.Folio
import app.folio.core.designsystem.FolioType
import app.folio.core.designsystem.components.ActionPill
import app.folio.core.designsystem.components.CircleButton
import app.folio.core.designsystem.components.FolioSheet
import app.folio.core.designsystem.components.GlassCapsule
import app.folio.core.designsystem.components.GroupedCard
import app.folio.core.designsystem.components.ProgressBar
import app.folio.core.designsystem.components.SheetHeader

/**
 * «Загрузки»: every catalog download with its progress and state; pause, resume, retry,
 * remove, «Читать» when done. Laid out like «Коллекции»: large title, grouped card.
 */
@Composable
internal fun DownloadsSheet(vm: CatalogViewModel, downloads: List<DownloadItem>, onOpen: (String) -> Unit, onDismiss: () -> Unit) {
    val c = Folio.colors
    FolioSheet(onDismiss = onDismiss, containerColor = c.groupedBackground) {
        SheetHeader(
            stringResource(R.string.catalog_downloads),
            centered = false,
            trailing = {
                if (downloads.any { it.state == DownloadState.Done }) {
                    GlassCapsule(stringResource(R.string.catalog_downloads_clear), vm::clearFinished)
                    Spacer(Modifier.width(12.dp))
                }
                CircleButton(Icons.Rounded.Close, stringResource(R.string.catalog_close), onDismiss)
            },
        )
        if (downloads.isEmpty()) {
            EmptyBlock(stringResource(R.string.catalog_downloads_empty), stringResource(R.string.catalog_downloads_empty_text), Modifier.navigationBarsPadding())
            return@FolioSheet
        }
        LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding()) {
            item {
                GroupedCard(Modifier.padding(top = 8.dp)) {
                    downloads.forEachIndexed { i, item ->
                        DownloadRow(vm, item, onOpen)
                        if (i != downloads.lastIndex) HorizontalDivider(color = c.separator, thickness = 0.5.dp, modifier = Modifier.padding(start = 78.dp))
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.catalog_downloads_note), style = FolioType.footnote, color = c.secondaryLabel,
                    modifier = Modifier.padding(start = 36.dp, end = 36.dp, top = 10.dp, bottom = 24.dp),
                )
            }
        }
    }
}

@Composable
private fun DownloadRow(vm: CatalogViewModel, item: DownloadItem, onOpen: (String) -> Unit) {
    val c = Folio.colors
    val e = item.entity
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        RemoteCover(e.title, e.author, e.coverUrl, Modifier.width(46.dp), elevation = 3.dp, titleSize = 7.sp)
        Column(Modifier.weight(1f).padding(start = 16.dp, end = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(e.title, style = FolioType.headline, color = c.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (e.author.isNotBlank()) Text(e.author, style = FolioType.subheadline, color = c.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (item.state == DownloadState.Running || item.state == DownloadState.Paused) {
                ProgressBar(item.fraction ?: if (item.state == DownloadState.Running) null else 0f, Modifier.padding(top = 6.dp, bottom = 2.dp))
            }
            Text(
                status(item),
                style = FolioType.footnote,
                color = if (item.state == DownloadState.Failed) c.destructive else c.secondaryLabel,
                maxLines = 2,
            )
        }
        when {
            item.readable -> ActionPill(stringResource(R.string.catalog_read), { onOpen(e.bookId!!) })
            item.state == DownloadState.Queued || item.state == DownloadState.Running ->
                CircleButton(Icons.Rounded.Pause, stringResource(R.string.catalog_pause), { vm.pause(item.id) }, size = 36.dp)
            item.state == DownloadState.Paused ->
                CircleButton(Icons.Rounded.PlayArrow, stringResource(R.string.catalog_resume), { vm.resume(item.id) }, size = 36.dp)
            item.state == DownloadState.Failed ->
                CircleButton(Icons.Rounded.Refresh, stringResource(R.string.catalog_retry), { vm.resume(item.id) }, size = 36.dp)
        }
        Icon(
            Icons.Rounded.Close, stringResource(R.string.catalog_remove), tint = c.tertiaryLabel,
            modifier = Modifier.padding(start = 6.dp).size(30.dp).clip(CircleShape).clickable { vm.remove(item.id) }.padding(6.dp),
        )
    }
}

@Composable
private fun status(item: DownloadItem): String {
    val e = item.entity
    val sizes = if (item.total > 0) stringResource(R.string.catalog_status_of, fileSize(item.bytes), fileSize(item.total)) else fileSize(item.bytes)
    return when (item.state) {
        DownloadState.Queued -> when (e.error) {
            null -> stringResource(R.string.catalog_status_queued)
            NetworkErrors.OFFLINE -> stringResource(R.string.catalog_status_waiting)
            else -> stringResource(R.string.catalog_status_retrying, errorText(e.error))
        }
        DownloadState.Running -> if (item.bytes == 0L) stringResource(R.string.catalog_status_preparing) else "${e.format} · $sizes"
        DownloadState.Paused -> if (item.bytes == 0L) stringResource(R.string.catalog_status_paused_empty) else stringResource(R.string.catalog_status_paused, sizes)
        DownloadState.Failed -> errorText(e.error)
        DownloadState.Done -> if (item.inLibrary) {
            stringResource(R.string.catalog_status_done, listOf(e.format, fileSize(item.total.coerceAtLeast(0))).joinToString(" · "))
        } else {
            stringResource(R.string.catalog_status_removed)
        }
    }
}
