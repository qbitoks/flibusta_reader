package net.flibustareader.feature.catalog

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.flibustareader.core.designsystem.Flibusta
import net.flibustareader.core.designsystem.FlibustaType
import net.flibustareader.core.designsystem.components.CapsuleButton
import net.flibustareader.core.designsystem.components.CircleButton
import net.flibustareader.core.designsystem.components.GroupedCard
import net.flibustareader.core.designsystem.components.GroupedRow
import net.flibustareader.core.designsystem.components.SegmentedControl
import net.flibustareader.core.designsystem.components.glass
import net.flibustareader.core.opds.CatalogEntry

/**
 * «Каталог» tab: OPDS search and browsing in the library's look — serif large title,
 * glass search capsule, grouped rows. Downloading a book imports it and opens it.
 */
@Composable
fun CatalogScreen(vm: CatalogViewModel, contentPadding: PaddingValues, onOpen: (String) -> Unit) {
    val c = Flibusta.colors
    val focus = LocalFocusManager.current
    var sources by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(vm) { vm.opened.collect(onOpen) }
    BackHandler(enabled = vm.canGoBack && vm.work == null) { vm.back() }

    val page = vm.page
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(start = 32.dp, end = 16.dp, top = 14.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.catalog_title), style = FlibustaType.largeTitle, color = c.label, maxLines = 1, modifier = Modifier.weight(1f))
                CircleButton(Icons.Rounded.Tune, stringResource(R.string.catalog_sources), { sources = true }, enabled = vm.work == null)
            }
        }
        item {
            Column(Modifier.padding(horizontal = 21.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    Modifier.fillMaxWidth().height(48.dp).glass(CircleShape, elevation = 2.dp).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Search, null, tint = c.secondaryLabel, modifier = Modifier.size(22.dp))
                    BasicTextField(
                        value = vm.query,
                        onValueChange = vm::updateQuery,
                        singleLine = true,
                        textStyle = FlibustaType.body.copy(color = c.label),
                        cursorBrush = SolidColor(c.label),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); vm.search() }),
                        modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                        decorationBox = { inner ->
                            if (vm.query.isEmpty()) {
                                Text(
                                    stringResource(if (vm.searchAuthors) R.string.catalog_query_authors else R.string.catalog_query_books),
                                    style = FlibustaType.body, color = c.secondaryLabel,
                                )
                            }
                            inner()
                        },
                    )
                    if (vm.query.isNotEmpty()) {
                        Icon(Icons.Rounded.Cancel, null, tint = c.tertiaryLabel, modifier = Modifier.size(20.dp).clickable { vm.updateQuery("") })
                    }
                }
                SegmentedControl(
                    options = listOf(stringResource(R.string.catalog_books), stringResource(R.string.catalog_authors)),
                    selected = if (vm.searchAuthors) 1 else 0,
                    onSelect = { vm.updateAuthors(it == 1) },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (vm.canGoBack) {
                        CircleButton(Icons.AutoMirrored.Rounded.ArrowBackIos, stringResource(R.string.catalog_back), { vm.back() }, enabled = vm.work == null)
                    }
                    CapsuleButton(stringResource(R.string.catalog_new), { vm.newArrivals() }, enabled = vm.work == null)
                }
            }
        }
        item { StatusLine(vm) }
        if (page == null) {
            item {
                Column(Modifier.padding(horizontal = 32.dp, vertical = 24.dp)) {
                    Text(stringResource(R.string.catalog_empty_title), style = FlibustaType.section, color = c.label)
                    Text(stringResource(R.string.catalog_empty_text), style = FlibustaType.body, color = c.secondaryLabel, modifier = Modifier.padding(top = 8.dp))
                }
            }
        } else {
            item {
                Text(
                    page.title, style = FlibustaType.section, color = c.label, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 20.dp, bottom = 12.dp),
                )
            }
            if (page.entries.isEmpty()) {
                item { Text(stringResource(R.string.catalog_nothing_found), style = FlibustaType.body, color = c.secondaryLabel, modifier = Modifier.padding(horizontal = 32.dp)) }
            } else {
                item {
                    GroupedCard {
                        page.entries.forEachIndexed { i, entry -> EntryRow(entry, last = i == page.entries.lastIndex, enabled = vm.work == null) { vm.open(entry) } }
                    }
                }
            }
            if (page.next != null) {
                item {
                    CapsuleButton(
                        stringResource(R.string.catalog_more), { vm.more() }, enabled = vm.work == null,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 21.dp, vertical = 16.dp),
                    )
                }
            }
        }
    }

    vm.selected?.let { entry -> EntryDialog(entry, onDownload = { vm.download(entry, it) }, onDismiss = { vm.selected = null }) }
    if (sources) SourcesDialog(vm, onDismiss = { sources = false })
}

@Composable
private fun EntryRow(entry: CatalogEntry, last: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val c = Flibusta.colors
    val isFolder = entry.navigation != null
    GroupedRow(
        title = entry.title,
        icon = if (isFolder) Icons.Rounded.Folder else Icons.AutoMirrored.Rounded.MenuBook,
        chevron = isFolder,
        enabled = enabled,
        showDivider = !last,
        trailing = if (isFolder) null else {
            { Text(entry.downloads.keys.joinToString(" · "), style = FlibustaType.footnote, color = c.secondaryLabel) }
        },
        onClick = onClick,
    )
    if (entry.author.isNotBlank() && !isFolder) {
        // Author under the title, aligned with it (icon column 36 dp + gap 14 dp + row start 18 dp).
        Text(
            entry.author, style = FlibustaType.footnote, color = c.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 68.dp, end = 16.dp, bottom = 10.dp),
        )
    }
}

@Composable
private fun StatusLine(vm: CatalogViewModel) {
    val c = Flibusta.colors
    val work = vm.work
    val error = vm.error
    if (work == null && error == null) return
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 21.dp, vertical = 12.dp).glass(CircleShape, elevation = 2.dp).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (work != null) CircularProgressIndicator(Modifier.size(18.dp), color = c.label, strokeWidth = 2.dp)
        val text = when (work) {
            CatalogWork.Loading -> stringResource(R.string.catalog_loading)
            is CatalogWork.Downloading -> work.percent?.let { stringResource(R.string.catalog_downloading_percent, it) } ?: stringResource(R.string.catalog_downloading)
            null -> stringResource(R.string.catalog_failed, error.orEmpty())
        }
        Text(text, style = FlibustaType.subheadline, color = if (work == null) c.destructive else c.label, modifier = Modifier.weight(1f).padding(start = 10.dp))
        if (work == null) Text(stringResource(R.string.catalog_close), style = FlibustaType.subheadline, color = c.secondaryLabel, modifier = Modifier.clickable { vm.dismissError() })
    }
}

@Composable
private fun EntryDialog(entry: CatalogEntry, onDownload: (String) -> Unit, onDismiss: () -> Unit) {
    val c = Flibusta.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text(entry.title, style = FlibustaType.sheetTitle, color = c.label) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (entry.author.isNotBlank()) Text(entry.author, style = FlibustaType.headline, color = c.label)
                Spacer(Modifier.height(10.dp))
                Text(entry.summary.ifBlank { stringResource(R.string.catalog_no_description) }, style = FlibustaType.subheadline, color = c.secondaryLabel)
            }
        },
        confirmButton = {
            Row {
                if (entry.downloads.isEmpty()) Text(stringResource(R.string.catalog_no_downloads), style = FlibustaType.subheadline, color = c.secondaryLabel, modifier = Modifier.padding(12.dp))
                entry.downloads.keys.forEach { format ->
                    TextButton(onClick = { onDownload(format) }) { Text(stringResource(R.string.catalog_download, format), color = c.label) }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.catalog_back), color = c.secondaryLabel) } },
    )
}

@Composable
private fun SourcesDialog(vm: CatalogViewModel, onDismiss: () -> Unit) {
    val c = Flibusta.colors
    var catalog by remember { mutableStateOf(vm.catalogBase) }
    var downloads by remember { mutableStateOf(vm.downloadBase) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text(stringResource(R.string.catalog_sources), style = FlibustaType.sheetTitle, color = c.label) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.catalog_sources_text), style = FlibustaType.subheadline, color = c.secondaryLabel)
                OutlinedTextField(catalog, { catalog = it }, label = { Text(stringResource(R.string.catalog_source_catalog)) }, singleLine = true)
                OutlinedTextField(downloads, { downloads = it }, label = { Text(stringResource(R.string.catalog_source_downloads)) }, singleLine = true)
                error?.let { Text(it, style = FlibustaType.footnote, color = c.destructive) }
            }
        },
        confirmButton = {
            TextButton(onClick = { error = vm.saveSources(catalog, downloads); if (error == null) onDismiss() }) {
                Text(stringResource(R.string.catalog_save), color = c.label)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.catalog_cancel), color = c.secondaryLabel) } },
    )
}
