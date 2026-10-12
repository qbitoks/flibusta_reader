package net.flibustareader.feature.catalog

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import net.flibustareader.core.data.catalog.DownloadItem
import net.flibustareader.core.data.catalog.NetworkErrors
import net.flibustareader.core.data.db.DownloadState
import net.flibustareader.core.designsystem.BookCover
import net.flibustareader.core.designsystem.Flibusta
import net.flibustareader.core.designsystem.FlibustaType
import net.flibustareader.core.designsystem.components.CapsuleButton
import net.flibustareader.core.opds.CatalogEntry
import java.io.File

/** Loads (and caches) catalog images; provided by [CatalogScreen]. */
internal val LocalCoverLoader = staticCompositionLocalOf<suspend (String) -> File?> { { null } }

/** A catalog cover: the generated cover until the image arrives, then the image. */
@Composable
internal fun RemoteCover(
    title: String,
    author: String?,
    url: String?,
    modifier: Modifier = Modifier,
    elevation: Dp = 6.dp,
    titleSize: TextUnit = 13.sp,
) {
    val loader = LocalCoverLoader.current
    val file by produceState<File?>(null, url) { value = url?.let { loader(it) } }
    BookCover(title, author?.ifBlank { null }, file, modifier, elevation = elevation, titleSize = titleSize)
}

/** Round author photo, or initials on a grey disc. */
@Composable
internal fun AuthorAvatar(name: String, url: String?, size: Dp = 44.dp) {
    val c = Flibusta.colors
    val loader = LocalCoverLoader.current
    val file by produceState<File?>(null, url) { value = url?.let { loader(it) } }
    Box(Modifier.size(size).clip(CircleShape).background(c.fill), contentAlignment = Alignment.Center) {
        val initials = name.split(' ', '(', ')').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }
        Text(initials, style = FlibustaType.headline, color = c.secondaryLabel)
        if (file != null) AsyncImage(file, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

@Composable
internal fun errorText(code: String?): String {
    val http = code?.removePrefix("http:")?.takeIf { code.startsWith("http:") }?.toIntOrNull()
    return when {
        code == null -> ""
        code == NetworkErrors.OFFLINE -> stringResource(R.string.catalog_error_offline)
        code == NetworkErrors.TIMEOUT -> stringResource(R.string.catalog_error_timeout)
        code == NetworkErrors.DROPPED -> stringResource(R.string.catalog_error_dropped)
        code == NetworkErrors.NOT_BOOK -> stringResource(R.string.catalog_error_not_book)
        code == NetworkErrors.TOO_BIG -> stringResource(R.string.catalog_error_too_big)
        code == NetworkErrors.IMPORT -> stringResource(R.string.catalog_error_import)
        http != null && http >= 500 -> stringResource(R.string.catalog_error_server, http)
        http != null -> stringResource(R.string.catalog_error_http, http)
        else -> code.removePrefix("other:")
    }
}

@Composable
internal fun fileSize(bytes: Long): String = Formatter.formatShortFileSize(LocalContext.current, bytes)

/** «FB2 · EPUB · 1987 · 825 КБ» under a book title. */
@Composable
internal fun bookMeta(entry: CatalogEntry): String {
    val parts = buildList {
        addAll(entry.downloads.keys.ifEmpty { entry.otherFormats })
        entry.year?.let(::add)
        entry.size?.let { add(fileSize(it)) }
    }
    return parts.joinToString(" · ")
}

/** Outline icon for a catalog folder, chosen by its title. */
internal fun folderIcon(title: String): ImageVector {
    // Word starts, not substrings: «Константиновна» must not look like «Новинки».
    val words = title.lowercase().split(Regex("[^\\p{L}]+"))
    fun has(vararg prefixes: String) = words.any { w -> prefixes.any { w.startsWith(it) } }
    return when {
        has("автор", "author") -> Icons.Outlined.Person
        has("сери", "series", "sequence") -> Icons.Outlined.CollectionsBookmark
        has("жанр", "genre") -> Icons.Outlined.Category
        has("алфавит", "alphabet") -> Icons.Outlined.SortByAlpha
        has("дат", "date") -> Icons.Outlined.Event
        has("нов", "new") -> Icons.Outlined.AutoAwesome
        has("книг", "book") -> Icons.AutoMirrored.Outlined.LibraryBooks
        else -> Icons.Outlined.Folder
    }
}

/** Flibusta describes an author entry by its book count: «1 книга», «23 книги», «120 книг». */
private val bookCount = Regex("""^\d+ (книг|book)""", RegexOption.IGNORE_CASE)

/**
 * A folder row: author photo/initials for authors, an outline icon otherwise; a short summary
 * («12 книг») under the title. Plain row with an inset divider, as in the library lists.
 */
@Composable
internal fun FolderRow(entry: CatalogEntry, onClick: () -> Unit, divider: Boolean = true) {
    val c = Flibusta.colors
    val author = entry.cover != null || bookCount.containsMatchIn(entry.summary)
    val subtitle = entry.summary.lineSequence().firstOrNull()?.takeIf { it.length <= 60 }
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 32.dp, end = 20.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(44.dp), contentAlignment = Alignment.Center) {
                if (author) AuthorAvatar(entry.title, entry.cover) else Icon(folderIcon(entry.title), null, tint = c.label, modifier = Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(entry.title, style = FlibustaType.body, color = c.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, style = FlibustaType.subheadline, color = c.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.tertiaryLabel, modifier = Modifier.size(22.dp))
        }
        if (divider) HorizontalDivider(color = c.separator, thickness = 0.5.dp, modifier = Modifier.padding(start = 90.dp))
    }
}

/** A book in a feed: small cover, title, author, formats/year/size and its download state. */
@Composable
internal fun BookRow(entry: CatalogEntry, downloads: Map<String, DownloadItem>, onClick: () -> Unit) {
    val c = Flibusta.colors
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 32.dp, end = 20.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RemoteCover(entry.title, entry.author, entry.cover, Modifier.width(48.dp), elevation = 3.dp, titleSize = 7.sp)
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(entry.title, style = FlibustaType.headline, color = c.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (entry.author.isNotBlank()) {
                    Text(entry.author, style = FlibustaType.subheadline, color = c.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val meta = bookMeta(entry)
                if (meta.isNotEmpty()) Text(meta, style = FlibustaType.footnote, color = c.secondaryLabel, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
            }
            DownloadGlyph(entry.downloads.values.mapNotNull { downloads[it] })
        }
        HorizontalDivider(color = c.separator, thickness = 0.5.dp, modifier = Modifier.padding(start = 94.dp))
    }
}

/** The most advanced state among a book's format downloads, as a small trailing glyph. */
@Composable
internal fun DownloadGlyph(items: List<DownloadItem>) {
    val c = Flibusta.colors
    val item = items.firstOrNull { it.readable } ?: items.firstOrNull { it.active } ?: items.firstOrNull() ?: return
    Box(Modifier.padding(start = 10.dp).size(24.dp), contentAlignment = Alignment.Center) {
        when {
            item.readable -> Icon(Icons.Rounded.CheckCircle, null, tint = c.badgeText, modifier = Modifier.size(22.dp))
            item.state == DownloadState.Running -> {
                val fraction = item.fraction
                if (fraction != null) {
                    CircularProgressIndicator(progress = { fraction }, modifier = Modifier.size(20.dp), color = c.label, trackColor = c.fill, strokeWidth = 2.5.dp)
                } else {
                    CircularProgressIndicator(Modifier.size(20.dp), color = c.label, trackColor = c.fill, strokeWidth = 2.5.dp)
                }
            }
            item.state == DownloadState.Queued -> Icon(Icons.Rounded.Schedule, null, tint = c.secondaryLabel, modifier = Modifier.size(20.dp))
            item.state == DownloadState.Paused -> Icon(Icons.Rounded.Pause, null, tint = c.secondaryLabel, modifier = Modifier.size(20.dp))
            item.state == DownloadState.Failed -> Icon(Icons.Rounded.ErrorOutline, null, tint = c.destructive, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
internal fun LoadingBlock(text: String? = null, modifier: Modifier = Modifier) {
    val c = Flibusta.colors
    Column(modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(Modifier.size(24.dp), color = c.secondaryLabel, strokeWidth = 2.dp)
        if (text != null) {
            Text(text, style = FlibustaType.subheadline, color = c.secondaryLabel, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

/** A failed load: what went wrong and «Повторить». */
@Composable
internal fun ErrorBlock(code: String?, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val c = Flibusta.colors
    Column(modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.ErrorOutline, null, tint = c.secondaryLabel, modifier = Modifier.size(28.dp))
        Text(errorText(code), style = FlibustaType.subheadline, color = c.secondaryLabel, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp, bottom = 14.dp))
        CapsuleButton(stringResource(R.string.catalog_retry), onRetry)
    }
}

@Composable
internal fun EmptyBlock(title: String, text: String?, modifier: Modifier = Modifier) {
    val c = Flibusta.colors
    Column(modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = FlibustaType.sheetTitle, color = c.secondaryLabel, textAlign = TextAlign.Center)
        if (text != null) {
            Spacer(Modifier.height(8.dp))
            Text(text, style = FlibustaType.subheadline, color = c.secondaryLabel, textAlign = TextAlign.Center)
        }
    }
}

/**
 * The entries of [feed] as lazy items: folders, then books, then the next page (loaded when the
 * end scrolls into view), a note about hidden PDF-only books, or the error.
 */
internal fun LazyListScope.feedItems(
    feed: Feed,
    downloads: Map<String, DownloadItem>,
    onOpen: (CatalogEntry) -> Unit,
    onMore: () -> Unit,
    onRetry: () -> Unit,
    entries: List<CatalogEntry> = feed.entries,
    loadingText: String? = null,
) {
    val (shown, hidden) = readableEntries(entries)
    val folders = shown.filter { !it.isBook }
    val books = shown.filter { it.isBook }
    if (feed.page == null) {
        when {
            feed.error != null -> item(key = "error") { ErrorBlock(feed.error, onRetry) }
            else -> item(key = "loading") { LoadingBlock(loadingText) }
        }
        return
    }
    if (shown.isEmpty() && feed.page?.next == null) {
        item(key = "empty") { EmptyBlock(stringResource(R.string.catalog_empty_folder), null) }
    }
    items(folders, key = { "f-" + it.id }) { entry -> FolderRow(entry, { onOpen(entry) }, divider = entry !== folders.last()) }
    if (folders.isNotEmpty() && books.isNotEmpty()) item(key = "gap") { Spacer(Modifier.height(16.dp)) }
    items(books, key = { "b-" + it.id }) { entry -> BookRow(entry, downloads) { onOpen(entry) } }
    when {
        feed.loadingMore -> item(key = "more") { LoadingBlock() }
        feed.error != null -> item(key = "more-error") { ErrorBlock(feed.error, onRetry = onMore) }
        feed.page?.next != null -> item(key = "more-trigger") {
            LaunchedEffect(feed.page) { onMore() }
            LoadingBlock()
        }
    }
    if (hidden > 0) {
        item(key = "hidden") {
            Text(
                pluralStringResource(R.plurals.catalog_hidden_books, hidden, hidden),
                style = FlibustaType.footnote, color = Flibusta.colors.secondaryLabel, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 16.dp),
            )
        }
    }
}
