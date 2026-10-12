package net.flibustareader.feature.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.flibustareader.core.data.catalog.DownloadItem
import net.flibustareader.core.data.catalog.NetworkErrors
import net.flibustareader.core.data.db.DownloadState
import net.flibustareader.core.designsystem.Flibusta
import net.flibustareader.core.designsystem.FlibustaType
import net.flibustareader.core.designsystem.components.CapsuleButton
import net.flibustareader.core.designsystem.components.CircleButton
import net.flibustareader.core.designsystem.components.FlibustaSheet
import net.flibustareader.core.opds.CatalogEntry

/**
 * A catalog book: cover, title, author, genres and details, a download capsule per format that
 * fills with progress, «Читать» once a format is in the library, the description and links to
 * the author's other books or the series.
 */
@Composable
internal fun BookSheet(
    vm: CatalogViewModel,
    entry: CatalogEntry,
    downloads: Map<String, DownloadItem>,
    onOpen: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Flibusta.colors
    val formats = entry.downloads.entries.map { (format, link) -> format to downloads[link] }
    val readable = formats.firstNotNullOfOrNull { (_, item) -> item?.takeIf { it.readable } }
    FlibustaSheet(onDismiss = onDismiss) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), contentAlignment = Alignment.CenterEnd) {
            CircleButton(Icons.Rounded.Close, stringResource(R.string.catalog_close), onDismiss)
        }
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
            Row(Modifier.padding(start = 32.dp, end = 24.dp, top = 4.dp)) {
                RemoteCover(entry.title, entry.author, entry.cover, Modifier.width(118.dp), elevation = 8.dp, titleSize = 12.sp)
                Column(Modifier.weight(1f).padding(start = 20.dp)) {
                    Text(entry.title, style = FlibustaType.bookTitle, color = c.label, maxLines = 5, overflow = TextOverflow.Ellipsis)
                    if (entry.author.isNotBlank()) {
                        val authorLink = entry.related.firstOrNull { "автор" in it.title.lowercase() || "author" in it.title.lowercase() }
                        Text(
                            entry.author,
                            style = FlibustaType.subheadline,
                            color = if (authorLink != null) c.label else c.secondaryLabel,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp)
                                .then(if (authorLink != null) Modifier.clip(RoundedCornerShape(6.dp)).clickable { vm.openLink(authorLink) } else Modifier),
                        )
                    }
                    if (entry.genres.isNotEmpty()) {
                        Text(entry.genres.joinToString(", "), style = FlibustaType.footnote, color = c.secondaryLabel, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                    }
                    val details = listOfNotNull(entry.year, entry.language?.uppercase(), entry.size?.let { fileSize(it) }).joinToString(" · ")
                    if (details.isNotEmpty()) Text(details, style = FlibustaType.footnote, color = c.secondaryLabel, modifier = Modifier.padding(top = 2.dp))
                }
            }

            Column(Modifier.padding(start = 21.dp, end = 21.dp, top = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (readable != null) {
                    CapsuleButton(
                        stringResource(R.string.catalog_read), { onOpen(readable.entity.bookId!!) },
                        modifier = Modifier.fillMaxWidth(), container = c.label, contentColor = c.background,
                        leading = { Icon(Icons.AutoMirrored.Rounded.MenuBook, null, tint = c.background, modifier = Modifier.padding(end = 8.dp).size(20.dp)) },
                    )
                }
                // Once one format is in the library, «Читать» replaces the download buttons.
                formats.filter { readable == null }.forEach { (format, item) ->
                    DownloadCapsule(
                        format, item,
                        primary = readable == null && format == formats.first().first,
                        onDownload = { vm.download(entry, format) },
                        onPause = { item?.let { vm.pause(it.id) } },
                        onResume = { item?.let { vm.resume(it.id) } },
                    )
                    if (item?.state == DownloadState.Failed) {
                        Text(errorText(item.entity.error), style = FlibustaType.footnote, color = c.destructive, modifier = Modifier.padding(horizontal = 12.dp))
                    }
                }
                vm.downloadError?.let { Text(errorText(it), style = FlibustaType.footnote, color = c.destructive, modifier = Modifier.padding(horizontal = 12.dp)) }
                when {
                    entry.downloads.isEmpty() && entry.otherFormats.isNotEmpty() -> Note(stringResource(R.string.catalog_unreadable, entry.otherFormats.joinToString(", ")))
                    entry.downloads.isEmpty() -> Note(stringResource(R.string.catalog_no_downloads))
                    entry.downloads.size > 1 && readable == null -> Note(stringResource(R.string.catalog_formats_hint))
                }
            }

            Description(entry.summary)

            if (entry.related.isNotEmpty()) {
                Text(stringResource(R.string.catalog_related), style = FlibustaType.sheetTitle, color = c.label, modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 28.dp, bottom = 6.dp))
                HorizontalDivider(color = c.separator, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 32.dp))
                entry.related.forEach { link ->
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.openLink(link) }.padding(start = 32.dp, end = 24.dp, top = 13.dp, bottom = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(folderIcon(link.title), null, tint = c.label, modifier = Modifier.size(22.dp))
                        Text(link.title, style = FlibustaType.body, color = c.label, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 14.dp))
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.tertiaryLabel, modifier = Modifier.size(22.dp))
                    }
                    HorizontalDivider(color = c.separator, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 32.dp))
                }
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = FlibustaType.footnote, color = Flibusta.colors.secondaryLabel, modifier = Modifier.padding(horizontal = 12.dp))
}

/** Description clamped to six lines with an «ещё» to expand, as in Apple Books. */
@Composable
private fun Description(text: String) {
    val c = Flibusta.colors
    var expanded by remember(text) { mutableStateOf(false) }
    var overflows by remember(text) { mutableStateOf(false) }
    Column(Modifier.padding(start = 32.dp, end = 32.dp, top = 28.dp)) {
        Text(
            text.ifBlank { stringResource(R.string.catalog_no_description) },
            style = FlibustaType.callout,
            color = if (text.isBlank()) c.secondaryLabel else c.label,
            maxLines = if (expanded) Int.MAX_VALUE else 6,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
        )
        if (overflows && !expanded) {
            Text(
                stringResource(R.string.catalog_show_more), style = FlibustaType.callout.copy(fontWeight = FlibustaType.headline.fontWeight), color = c.label,
                modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(6.dp)).clickable { expanded = true }.padding(vertical = 2.dp),
            )
        }
    }
}

/**
 * One format's download: «Скачать EPUB», then the capsule fills with progress (tap to pause),
 * «пауза» (tap to resume) or «повторить» after a failure.
 */
@Composable
private fun DownloadCapsule(
    format: String,
    item: DownloadItem?,
    primary: Boolean,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
) {
    val c = Flibusta.colors
    val state = item?.state?.takeIf { item.state != DownloadState.Done }
    val text: String
    val icon: ImageVector
    val action: () -> Unit
    when (state) {
        null -> { text = stringResource(R.string.catalog_download, format); icon = Icons.Rounded.Download; action = onDownload }
        DownloadState.Queued -> {
            text = stringResource(if (item.entity.error == NetworkErrors.OFFLINE) R.string.catalog_download_waiting else R.string.catalog_download_queued, format)
            icon = Icons.Rounded.Schedule; action = onPause
        }
        DownloadState.Running -> {
            val fraction = item.fraction
            text = when {
                fraction != null -> stringResource(R.string.catalog_download_percent, format, (fraction * 100).toInt())
                item.bytes > 0 -> stringResource(R.string.catalog_download_bytes, format, fileSize(item.bytes))
                else -> stringResource(R.string.catalog_download_preparing, format)
            }
            icon = Icons.Rounded.Pause; action = onPause
        }
        DownloadState.Paused -> { text = stringResource(R.string.catalog_download_paused, format); icon = Icons.Rounded.PlayArrow; action = onResume }
        DownloadState.Failed -> { text = stringResource(R.string.catalog_download_retry, format); icon = Icons.Rounded.Refresh; action = onResume }
        DownloadState.Done -> error("unreachable")
    }
    val filled = primary && state == null
    val container = if (filled) c.label else c.capsule
    val content = if (filled) c.background else c.label
    Box(
        Modifier.fillMaxWidth().heightIn(min = 50.dp).clip(CircleShape).background(container)
            .clickable(role = Role.Button, onClick = action),
        contentAlignment = Alignment.Center,
    ) {
        val fraction = item?.fraction
        if (state != null && fraction != null) {
            Box(Modifier.matchParentSize()) {
                Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(c.label.copy(alpha = 0.14f)))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 20.dp, vertical = 13.dp)) {
            Icon(icon, null, tint = content, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, style = FlibustaType.headline, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
