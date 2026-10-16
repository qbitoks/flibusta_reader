package app.folio.feature.catalog

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
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MoreHoriz
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.folio.core.data.catalog.DownloadItem
import app.folio.core.data.catalog.NetworkErrors
import app.folio.core.data.db.DownloadState
import app.folio.core.designsystem.Folio
import app.folio.core.designsystem.FolioType
import app.folio.core.designsystem.components.CapsuleButton
import app.folio.core.designsystem.components.CircleButton
import app.folio.core.designsystem.components.FolioSheet
import app.folio.core.designsystem.components.PullDownMenu
import app.folio.core.opds.CatalogEntry

/**
 * A catalog book: cover, title, author, genres and details, one download capsule for the best format
 * that fills with progress and turns into «Читать», a ••• menu with every format, the description
 * and links to the author's other books or the series.
 */
@Composable
internal fun BookSheet(
    vm: CatalogViewModel,
    entry: CatalogEntry,
    downloads: Map<String, DownloadItem>,
    onOpen: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Folio.colors
    val formats = vm.formatsOf(entry).map { format -> format to downloads[entry.downloads.getValue(format)] }
    // Each format is a book of its own in the library: «Читать» opens the catalog's format, else the latest copy.
    val copies = formats.filter { (_, item) -> item?.readable == true }
    val read = copies.firstOrNull { it.first == formats.first().first } ?: copies.maxByOrNull { it.second!!.entity.updatedAt }
    val readable = read?.second
    val main = formats.filter { it.first in mainFormats }.ifEmpty { formats }
    // The capsule follows the download in progress (or the last failed one), else offers the best format.
    val started = formats.filter { (_, item) -> item != null && item.state != DownloadState.Done }
    val current = started.filter { it.second!!.state != DownloadState.Failed }.ifEmpty { started }
        .maxByOrNull { it.second!!.entity.updatedAt } ?: formats.firstOrNull()
    FolioSheet(onDismiss = onDismiss) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), contentAlignment = Alignment.CenterEnd) {
            CircleButton(Icons.Rounded.Close, stringResource(R.string.catalog_close), onDismiss)
        }
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
            Row(Modifier.padding(start = 32.dp, end = 24.dp, top = 4.dp)) {
                RemoteCover(entry.title, entry.author, entry.cover, Modifier.width(118.dp), elevation = 8.dp, titleSize = 12.sp)
                Column(Modifier.weight(1f).padding(start = 20.dp)) {
                    Text(entry.title, style = FolioType.bookTitle, color = c.label, maxLines = 5, overflow = TextOverflow.Ellipsis)
                    if (entry.author.isNotBlank()) {
                        val authorLink = entry.related.firstOrNull { "автор" in it.title.lowercase() || "author" in it.title.lowercase() }
                        Text(
                            entry.author,
                            style = FolioType.subheadline,
                            color = if (authorLink != null) c.label else c.secondaryLabel,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp)
                                .then(if (authorLink != null) Modifier.clip(RoundedCornerShape(6.dp)).clickable { vm.openLink(authorLink) } else Modifier),
                        )
                    }
                    if (entry.genres.isNotEmpty()) {
                        Text(entry.genres.joinToString(", "), style = FolioType.footnote, color = c.secondaryLabel, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                    }
                    val details = listOfNotNull(entry.year, entry.language?.uppercase(), entry.size?.let { fileSize(it) }).joinToString(" · ")
                    if (details.isNotEmpty()) Text(details, style = FolioType.footnote, color = c.secondaryLabel, modifier = Modifier.padding(top = 2.dp))
                }
            }

            Column(Modifier.padding(start = 21.dp, end = 21.dp, top = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (formats.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            if (readable != null) {
                                CapsuleButton(
                                    if (copies.size > 1) stringResource(R.string.catalog_read_copy, read.first) else stringResource(R.string.catalog_read),
                                    { onOpen(readable.entity.bookId!!) },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp), container = c.label, contentColor = c.background,
                                    leading = { Icon(Icons.AutoMirrored.Rounded.MenuBook, null, tint = c.background, modifier = Modifier.padding(end = 8.dp).size(20.dp)) },
                                )
                            } else if (current != null) {
                                DownloadCapsule(current.first, current.second, primary = true, { vm.download(entry, current.first) }, vm::pause, vm::resume)
                            }
                        }
                        val pending = current?.second?.takeIf { readable == null && it.state != DownloadState.Done }
                        when {
                            // One download at a time: × cancels it and brings back «Скачать» and •••.
                            pending != null -> RoundButton(Icons.Rounded.Close, stringResource(R.string.catalog_download_cancel, current.first)) { vm.cancel(entry, pending.id) }
                            formats.size > 1 -> FormatsMenu(
                                formats, { onOpen(it.entity.bookId!!) }, { vm.download(entry, it, fallBack = false) },
                                vm::pause, vm::resume, { vm.cancel(entry, it) },
                            )
                        }
                    }
                }
                val failed = current?.second?.takeIf { readable == null && it.state == DownloadState.Failed }
                if (failed != null) {
                    Text(errorText(failed.entity.error), style = FolioType.footnote, color = c.destructive, modifier = Modifier.padding(horizontal = 12.dp))
                    val signIn = LocalSignIn.current
                    if (failed.entity.error == NetworkErrors.LOGIN && signIn != null) {
                        CapsuleButton(stringResource(R.string.catalog_sign_in), { onDismiss(); signIn() }, modifier = Modifier.fillMaxWidth())
                    }
                }
                // Every format the catalog refused: the website may still give the book (after a sign-in).
                val refused = main.isNotEmpty() && main.all { (_, item) -> item?.state == DownloadState.Failed && NetworkErrors.isUnavailable(item.entity.error) }
                if (readable == null && refused) {
                    val uri = LocalUriHandler.current
                    val page = entry.web ?: entry.downloads.values.first()
                    CapsuleButton(
                        stringResource(R.string.catalog_open_web), { runCatching { uri.openUri(page) } },
                        modifier = Modifier.fillMaxWidth(),
                        leading = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, tint = c.label, modifier = Modifier.padding(end = 8.dp).size(20.dp)) },
                    )
                    Note(stringResource(R.string.catalog_open_web_hint))
                }
                val web = entry.web
                if (entry.downloads.isEmpty() && web != null) {
                    val uri = LocalUriHandler.current
                    CapsuleButton(
                        stringResource(R.string.catalog_open_web), { runCatching { uri.openUri(web) } },
                        modifier = Modifier.fillMaxWidth(), container = c.label, contentColor = c.background,
                        leading = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, tint = c.background, modifier = Modifier.padding(end = 8.dp).size(20.dp)) },
                    )
                }
                vm.downloadError?.let { Text(errorText(it), style = FolioType.footnote, color = c.destructive, modifier = Modifier.padding(horizontal = 12.dp)) }
                when {
                    entry.downloads.isEmpty() && entry.otherFormats.isNotEmpty() -> Note(stringResource(R.string.catalog_unreadable, entry.otherFormats.joinToString(", ")))
                    entry.downloads.isEmpty() && entry.web != null -> Note(stringResource(R.string.catalog_web_only))
                    entry.downloads.isEmpty() -> Note(stringResource(R.string.catalog_no_downloads))
                }
            }

            Description(entry.summary)

            if (entry.related.isNotEmpty()) {
                Text(stringResource(R.string.catalog_related), style = FolioType.sheetTitle, color = c.label, modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 28.dp, bottom = 6.dp))
                HorizontalDivider(color = c.separator, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 32.dp))
                entry.related.forEach { link ->
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.openLink(link) }.padding(start = 32.dp, end = 24.dp, top = 13.dp, bottom = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(folderIcon(link.title), null, tint = c.label, modifier = Modifier.size(22.dp))
                        Text(link.title, style = FolioType.body, color = c.label, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 14.dp))
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
    Text(text, style = FolioType.footnote, color = Folio.colors.secondaryLabel, modifier = Modifier.padding(horizontal = 12.dp))
}

/** Description clamped to six lines with an «ещё» to expand, as in Apple Books. */
@Composable
private fun Description(text: String) {
    val c = Folio.colors
    var expanded by remember(text) { mutableStateOf(false) }
    var overflows by remember(text) { mutableStateOf(false) }
    Column(Modifier.padding(start = 32.dp, end = 32.dp, top = 28.dp)) {
        Text(
            text.ifBlank { stringResource(R.string.catalog_no_description) },
            style = FolioType.callout,
            color = if (text.isBlank()) c.secondaryLabel else c.label,
            maxLines = if (expanded) Int.MAX_VALUE else 6,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
        )
        if (overflows && !expanded) {
            Text(
                stringResource(R.string.catalog_show_more), style = FolioType.callout.copy(fontWeight = FolioType.headline.fontWeight), color = c.label,
                modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(6.dp)).clickable { expanded = true }.padding(vertical = 2.dp),
            )
        }
    }
}

/** The round capsule-coloured button next to the main capsule: ••• or ×. */
@Composable
private fun RoundButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val c = Folio.colors
    Box(
        Modifier.padding(start = 10.dp).size(50.dp).clip(CircleShape).background(c.capsule)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = c.label, modifier = Modifier.size(22.dp))
    }
}

/**
 * Every format of the book: «Читать» for copies in the library, otherwise the same states as the capsule;
 * downloads in progress get «Отменить загрузку» at the bottom.
 */
@Composable
private fun FormatsMenu(
    formats: List<Pair<String, DownloadItem?>>,
    onRead: (DownloadItem) -> Unit,
    onDownload: (String) -> Unit,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onCancel: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val label = stringResource(R.string.catalog_formats)
    Box {
        RoundButton(Icons.Rounded.MoreHoriz, label) { open = true }
        PullDownMenu(open, { open = false }, width = 260.dp) {
            Header(label)
            formats.forEach { (format, item) ->
                if (item != null && item.readable) {
                    Item(stringResource(R.string.catalog_read_format, format), { onRead(item) }, icon = Icons.AutoMirrored.Rounded.MenuBook)
                } else {
                    val (text, icon, action) = downloadAction(format, item, { onDownload(format) }, onPause, onResume)
                    Item(text, action, icon = icon)
                }
            }
            val started = formats.filter { (_, item) -> item != null && item.state != DownloadState.Done }
            if (started.isNotEmpty()) Divider()
            started.forEach { (format, item) ->
                Item(stringResource(R.string.catalog_download_cancel, format), { onCancel(item!!.id) }, icon = Icons.Rounded.Close, destructive = true)
            }
        }
    }
}

/** What a format's download shows and does: «Скачать EPUB», progress (tap to pause), «пауза» or «повторить». */
@Composable
private fun downloadAction(
    format: String,
    item: DownloadItem?,
    onDownload: () -> Unit,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
): Triple<String, ImageVector, () -> Unit> = when (item?.state?.takeIf { it != DownloadState.Done }) {
    null -> Triple(stringResource(R.string.catalog_download, format), Icons.Rounded.Download, onDownload)
    DownloadState.Queued -> Triple(
        stringResource(if (item.entity.error == NetworkErrors.OFFLINE) R.string.catalog_download_waiting else R.string.catalog_download_queued, format),
        Icons.Rounded.Schedule, { onPause(item.id) },
    )
    DownloadState.Running -> {
        val fraction = item.fraction
        val text = when {
            fraction != null -> stringResource(R.string.catalog_download_percent, format, (fraction * 100).toInt())
            item.bytes > 0 -> stringResource(R.string.catalog_download_bytes, format, fileSize(item.bytes))
            else -> stringResource(R.string.catalog_download_preparing, format)
        }
        Triple(text, Icons.Rounded.Pause, { onPause(item.id) })
    }
    DownloadState.Paused -> Triple(stringResource(R.string.catalog_download_paused, format), Icons.Rounded.PlayArrow, { onResume(item.id) })
    DownloadState.Failed -> Triple(stringResource(R.string.catalog_download_retry, format), Icons.Rounded.Refresh, { onResume(item.id) })
    DownloadState.Done -> error("unreachable")
}

/** One format's download capsule; it fills with progress while the file comes. */
@Composable
private fun DownloadCapsule(
    format: String,
    item: DownloadItem?,
    primary: Boolean,
    onDownload: () -> Unit,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
) {
    val c = Folio.colors
    val state = item?.state?.takeIf { item.state != DownloadState.Done }
    val (text, icon, action) = downloadAction(format, item, onDownload, onPause, onResume)
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
            Text(text, style = FolioType.headline, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The real books; MOBI, DOCX, RTF, HTML, TXT and PDF (fixed pages) are conversions, never tried on a refusal. */
internal val mainFormats = setOf("EPUB", "FB2")
