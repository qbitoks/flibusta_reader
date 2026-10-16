package app.folio.feature.catalog

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folio.core.data.catalog.DownloadItem
import app.folio.core.designsystem.COVER_ASPECT
import app.folio.core.designsystem.Folio
import app.folio.core.designsystem.FolioType
import app.folio.core.designsystem.components.CircleButton
import app.folio.core.designsystem.components.LargeTitleHeader
import app.folio.core.designsystem.components.PullDownMenu
import app.folio.core.designsystem.components.SectionBand
import app.folio.core.designsystem.components.SectionTitle
import app.folio.core.designsystem.components.SegmentedControl
import app.folio.core.designsystem.components.glass
import app.folio.core.opds.CatalogEntry
import app.folio.core.opds.Facet
import app.folio.core.opds.FacetGroup
import java.io.File

/**
 * «Каталог» tab in the look of «Главная»: large serif title with round buttons (downloads, •••),
 * a glass search capsule, «Новинки» as a shelf of covers and the catalog's sections as a list,
 * each on its gradient band. Folders open as pushed screens; books open [BookSheet].
 */
@Composable
fun CatalogScreen(vm: CatalogViewModel, contentPadding: PaddingValues, onOpen: (String) -> Unit) {
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val byLink = remember(downloads) { downloads.byLink() }
    var searchActive by rememberSaveable { mutableStateOf(vm.results != null) }
    var downloadsOpen by rememberSaveable { mutableStateOf(false) }
    var catalogsOpen by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val homeList = rememberLazyListState()
    val loader = remember(vm) { val load: suspend (String) -> File? = { vm.cover(it) }; load }
    val top = vm.stack.lastOrNull()

    BackHandler(enabled = top != null || vm.results != null || searchActive) {
        if (top == null) {
            searchActive = false
            focus.clearFocus()
        }
        vm.back()
    }

    val signIn = remember(vm) { { vm.signInRequested = true } }
    CompositionLocalProvider(LocalCoverLoader provides loader, LocalSignIn provides signIn) {
        if (top != null) {
            FeedScreen(vm, top, byLink, contentPadding)
        } else {
            LazyColumn(Modifier.fillMaxSize(), state = homeList, contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 24.dp)) {
                item(key = "header") {
                    SectionBand(first = true) {
                        LargeTitleHeader(stringResource(R.string.catalog_title)) {
                            DownloadsButton(downloads) { downloadsOpen = true }
                            MoreMenu(vm, onCatalogs = { catalogsOpen = true })
                        }
                        CatalogSwitcher(vm, onOpen = { catalogsOpen = true })
                        if (vm.bookSearch != null || vm.results != null) {
                            SearchField(
                                vm,
                                active = searchActive,
                                onActivate = { searchActive = true },
                                onCancel = {
                                    searchActive = false
                                    vm.closeSearch()
                                    focus.clearFocus()
                                },
                                onSearch = { focus.clearFocus(); vm.search() },
                            )
                        }
                        val results = vm.results
                        if (results?.authors != null) {
                            SegmentedControl(
                                options = listOf(
                                    stringResource(R.string.catalog_scope_all),
                                    stringResource(R.string.catalog_scope_books),
                                    stringResource(R.string.catalog_scope_authors),
                                ),
                                selected = results.scope.ordinal,
                                onSelect = { vm.setScope(SearchScope.entries[it]) },
                                modifier = Modifier.padding(start = 21.dp, end = 21.dp, top = 12.dp),
                            )
                        }
                        Spacer(Modifier.height(if (searchActive || results != null) 8.dp else 20.dp))
                    }
                }
                val results = vm.results
                when {
                    results != null -> searchResults(vm, results, byLink)
                    searchActive -> recentSearches(vm, onPick = { focus.clearFocus(); vm.search(it) })
                    vm.catalog == null -> item(key = "no-catalog") { NoCatalogBlock(onAdd = { catalogsOpen = true }) }
                    else -> homeSections(vm, byLink, onCatalogs = { catalogsOpen = true })
                }
            }
        }

        vm.selected?.let { entry ->
            BookSheet(vm, entry, byLink, onOpen = { id -> vm.selected = null; onOpen(id) }, onDismiss = { vm.selected = null })
        }
        if (downloadsOpen) {
            DownloadsSheet(vm, downloads, onOpen = { id -> downloadsOpen = false; onOpen(id) }, onDismiss = { downloadsOpen = false })
        }
        if (catalogsOpen) CatalogsSheet(vm, onDismiss = { catalogsOpen = false })
        vm.catalog?.takeIf { vm.signInRequested }?.let { catalog ->
            CatalogsSheet(vm, onDismiss = { vm.signInRequested = false }, edit = catalog)
        }
    }
}

/** A folder opened from the catalog: back button, its title, its entries with endless scrolling. */
@Composable
private fun FeedScreen(vm: CatalogViewModel, feed: Feed, downloads: Map<String, DownloadItem>, contentPadding: PaddingValues) {
    val c = Folio.colors
    LazyColumn(
        Modifier.fillMaxSize().background(c.background),
        state = feed.list,
        contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 24.dp),
    ) {
        item(key = "header") {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                CircleButton(Icons.AutoMirrored.Rounded.ArrowBackIos, stringResource(R.string.catalog_back), { vm.back() })
                Spacer(Modifier.weight(1f))
                FolderMenu(vm, feed)
            }
            Text(
                feed.title,
                style = if (feed.title.length <= 22) FolioType.largeTitle else FolioType.section,
                color = c.label,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 4.dp, bottom = 12.dp),
            )
        }
        feed.page?.facets?.takeIf { it.isNotEmpty() }?.let { groups -> item(key = "facets") { FacetRow(groups, vm::openFacet) } }
        feedItems(feed, downloads, onOpen = vm::open, onMore = { vm.more(feed) }, onRetry = { vm.retry(feed) })
    }
}

private fun LazyListScope.homeSections(vm: CatalogViewModel, downloads: Map<String, DownloadItem>, onCatalogs: () -> Unit) {
    vm.newBooks?.let { feed ->
        item(key = "new") {
            SectionBand {
                val title = stringResource(R.string.catalog_new)
                SectionTitle(title, onClick = { vm.openShelf(feed, title) })
                BooksShelf(vm, feed, downloads)
                Spacer(Modifier.height(28.dp))
            }
        }
    }
    vm.popular?.let { feed ->
        item(key = "popular") {
            SectionBand {
                val title = stringResource(R.string.catalog_popular)
                SectionTitle(title, onClick = { vm.openShelf(feed, title) })
                BooksShelf(vm, feed, downloads)
                Spacer(Modifier.height(28.dp))
            }
        }
    }
    item(key = "sections") {
        SectionBand {
            SectionTitle(stringResource(R.string.catalog_sections))
            val root = vm.root
            root.page?.facets?.takeIf { it.isNotEmpty() }?.let { FacetRow(it, vm::openFacet) }
            val signedIn = vm.catalog?.id in vm.logins.collectAsStateWithLifecycle().value
            val sections = root.entries.filter { !it.isBook && (signedIn || !requiresLogin(it)) }
            val books = readableEntries(root.entries).first.filter { it.isBook }
            when {
                root.page == null && root.error != null -> ErrorBlock(root.error, onRetry = { vm.retry(root) }, site = root.address)
                root.page == null -> LoadingBlock()
                sections.isEmpty() && books.isEmpty() -> EmptyBlock(stringResource(R.string.catalog_empty_folder), null)
                else -> {
                    sections.forEach { entry -> FolderRow(entry, { vm.open(entry) }, divider = entry !== sections.last() || books.isNotEmpty()) }
                    // A root that lists books itself (Gutenberg) opens them like a folder does.
                    if (books.isNotEmpty()) {
                        val title = root.page?.title.orEmpty().ifBlank { vm.catalog?.title.orEmpty() }
                        Text(
                            stringResource(R.string.catalog_root_books, books.size),
                            style = FolioType.body, color = Folio.colors.label,
                            modifier = Modifier.fillMaxWidth().clickable { vm.openShelf(root, title) }.padding(horizontal = 32.dp, vertical = 14.dp),
                        )
                    }
                }
            }
            Text(
                stringResource(R.string.catalog_source_footer, vm.catalog?.host.orEmpty()),
                style = FolioType.footnote,
                color = Folio.colors.secondaryLabel,
                modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 24.dp, bottom = 20.dp).clip(RoundedCornerShape(6.dp)).clickable(onClick = onCatalogs),
            )
        }
    }
}

/** Shelves like «Моя полка» need an account; hidden until the catalog has a sign-in. */
private fun requiresLogin(entry: CatalogEntry): Boolean =
    entry.summary.contains("логин", ignoreCase = true) || entry.summary.contains("login", ignoreCase = true)

/** A shelf of large covers («Новинки», «Популярное»), like «Хочу прочесть» on «Главная». */
@Composable
private fun BooksShelf(vm: CatalogViewModel, feed: Feed, downloads: Map<String, DownloadItem>) {
    val c = Folio.colors
    val books = remember(feed.page) { readableEntries(feed.entries).first.filter { it.isBook }.take(24) }
    when {
        feed.page == null && feed.error != null -> ErrorBlock(feed.error, onRetry = { vm.retry(feed) })
        feed.page == null -> LazyRow(
            contentPadding = PaddingValues(horizontal = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            userScrollEnabled = false,
        ) {
            items(5) {
                Box(Modifier.width(112.dp).aspectRatio(COVER_ASPECT).clip(RoundedCornerShape(2.dp)).background(c.fill))
            }
        }
        else -> LazyRow(contentPadding = PaddingValues(horizontal = 32.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(books, key = { it.id }) { entry ->
                Column(Modifier.width(112.dp).clip(RoundedCornerShape(4.dp)).clickable { vm.open(entry) }) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        RemoteCover(entry.title, entry.author, entry.cover, Modifier.fillMaxWidth(), elevation = 6.dp, titleSize = 11.sp)
                        Box(Modifier.padding(4.dp)) { DownloadGlyph(entry.downloads.values.mapNotNull { downloads[it] }) }
                    }
                    Text(
                        entry.title, style = FolioType.footnote.copy(fontWeight = FolioType.headline.fontWeight), color = c.label,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp),
                    )
                    Text(entry.author, style = FolioType.footnote, color = c.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun SearchField(vm: CatalogViewModel, active: Boolean, onActivate: () -> Unit, onCancel: () -> Unit, onSearch: () -> Unit) {
    val c = Folio.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = 21.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).height(48.dp).glass(CircleShape, elevation = 2.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Search, null, tint = c.secondaryLabel, modifier = Modifier.size(22.dp))
            BasicTextField(
                value = vm.query,
                onValueChange = vm::updateQuery,
                singleLine = true,
                textStyle = FolioType.body.copy(color = c.label),
                cursorBrush = SolidColor(c.label),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp).onFocusChanged { if (it.isFocused) onActivate() },
                decorationBox = { inner ->
                    if (vm.query.isEmpty()) Text(stringResource(R.string.catalog_search_hint), style = FolioType.body, color = c.secondaryLabel)
                    inner()
                },
            )
            if (vm.query.isNotEmpty()) {
                Icon(
                    Icons.Rounded.Cancel, stringResource(R.string.catalog_search_clear), tint = c.tertiaryLabel,
                    modifier = Modifier.size(20.dp).clip(CircleShape).clickable { vm.updateQuery("") },
                )
            }
        }
        AnimatedVisibility(active) {
            Text(
                stringResource(R.string.catalog_cancel),
                style = FolioType.body,
                color = c.label,
                modifier = Modifier.padding(start = 14.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onCancel).padding(vertical = 8.dp),
            )
        }
    }
}

/** Recent queries matching what is typed, or a short tip when there are none. */
private fun LazyListScope.recentSearches(vm: CatalogViewModel, onPick: (String) -> Unit) {
    item(key = "recent") {
        val c = Folio.colors
        val recent by vm.recent.collectAsStateWithLifecycle()
        val typed = vm.query.trim()
        val shown = recent.filter { typed.isEmpty() || it.contains(typed, ignoreCase = true) }
        if (shown.isEmpty()) {
            EmptyBlock(stringResource(R.string.catalog_search_tip_title), stringResource(R.string.catalog_search_tip))
            return@item
        }
        Column {
            Row(Modifier.fillMaxWidth().padding(start = 32.dp, end = 24.dp, top = 16.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.catalog_search_recent), style = FolioType.sheetTitle, color = c.label, modifier = Modifier.weight(1f))
                Text(
                    stringResource(R.string.catalog_search_clear), style = FolioType.body, color = c.secondaryLabel,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { vm.clearRecent() }.padding(4.dp),
                )
            }
            HorizontalDivider(color = c.separator, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 32.dp))
            shown.forEach { q ->
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(q) }.padding(start = 32.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Search, null, tint = c.secondaryLabel, modifier = Modifier.size(22.dp))
                    Text(q, style = FolioType.body, color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 12.dp))
                    Icon(
                        Icons.Rounded.Close, stringResource(R.string.catalog_search_forget), tint = c.tertiaryLabel,
                        modifier = Modifier.size(28.dp).clip(CircleShape).clickable { vm.forgetSearch(q) }.padding(5.dp),
                    )
                }
                HorizontalDivider(color = c.separator, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 32.dp))
            }
        }
    }
}

/** Books and authors found at once; «Все» shows the best authors above the books. */
private fun LazyListScope.searchResults(vm: CatalogViewModel, results: SearchResults, downloads: Map<String, DownloadItem>) {
    val books = results.books
    val authors = results.authors
    if (authors == null) {
        val found = readableEntries(books.entries).first
        when {
            books.page == null && books.error == null -> item(key = "books-loading") { LoadingBlock(stringResource(R.string.catalog_searching, results.query)) }
            books.page != null && found.isEmpty() && books.page?.next == null -> item(key = "nothing") {
                EmptyBlock(stringResource(R.string.catalog_nothing_found), stringResource(R.string.catalog_nothing_found_text))
            }
            else -> feedItems(books, downloads, vm::open, { vm.more(books) }, { vm.retry(books) })
        }
        return
    }
    val ranked = rankAuthors(authors.entries, results.query)
    when (results.scope) {
        SearchScope.Books -> feedItems(books, downloads, vm::open, { vm.more(books) }, { vm.retry(books) })
        SearchScope.Authors -> feedItems(authors, downloads, vm::open, { vm.more(authors) }, { vm.retry(authors) }, entries = ranked)
        SearchScope.All -> {
            val bookEntries = readableEntries(books.entries).first
            val done = books.page != null && authors.page != null
            val nothing = done && bookEntries.isEmpty() && ranked.isEmpty() && books.page?.next == null
            when {
                nothing -> item(key = "nothing") {
                    EmptyBlock(stringResource(R.string.catalog_nothing_found), stringResource(R.string.catalog_nothing_found_text))
                }
                books.page == null && authors.page == null && books.error != null && authors.error != null -> item(key = "failed") {
                    ErrorBlock(books.error, onRetry = { vm.retry(books); vm.retry(authors) })
                }
                else -> {
                    if (ranked.isNotEmpty()) {
                        item(key = "authors-title") {
                            val more = ranked.size > AUTHORS_PREVIEW || authors.page?.next != null
                            SectionTitle(
                                stringResource(R.string.catalog_scope_authors),
                                onClick = if (more) ({ vm.setScope(SearchScope.Authors) }) else null,
                            )
                        }
                        val preview = ranked.take(AUTHORS_PREVIEW)
                        items(preview, key = { "a-" + it.id }) { entry -> FolderRow(entry, { vm.open(entry) }, divider = entry !== preview.last()) }
                    }
                    when {
                        books.page == null && books.error == null -> item(key = "books-loading") {
                            LoadingBlock(stringResource(R.string.catalog_searching, results.query))
                        }
                        books.page == null -> item(key = "books-error") { ErrorBlock(books.error, onRetry = { vm.retry(books) }) }
                        bookEntries.isNotEmpty() || books.page?.next != null -> {
                            item(key = "books-title") { SectionTitle(stringResource(R.string.catalog_scope_books)) }
                            feedItems(books, downloads, vm::open, { vm.more(books) }, { vm.retry(books) })
                        }
                    }
                }
            }
        }
    }
}

private const val AUTHORS_PREVIEW = 3

@Composable
private fun DownloadsButton(downloads: List<DownloadItem>, onClick: () -> Unit) {
    val c = Folio.colors
    val active = downloads.count { it.active }
    Box {
        CircleButton(
            if (active > 0) Icons.Rounded.Downloading else Icons.Rounded.Download,
            stringResource(R.string.catalog_downloads),
            onClick,
        )
        if (active > 0) {
            Box(
                Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp).size(20.dp).clip(CircleShape).background(c.label),
                contentAlignment = Alignment.Center,
            ) {
                Text(active.toString(), style = FolioType.caption.copy(fontWeight = FolioType.headline.fontWeight), color = c.background)
            }
        }
    }
}

@Composable
private fun MoreMenu(vm: CatalogViewModel, onCatalogs: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val recent by vm.recent.collectAsStateWithLifecycle()
    Box {
        CircleButton(Icons.Rounded.MoreHoriz, stringResource(R.string.catalog_more_actions), { open = true })
        PullDownMenu(open, { open = false }) {
            Item(stringResource(R.string.catalog_catalogs_menu), onCatalogs, icon = Icons.Rounded.Dns)
            Item(stringResource(R.string.catalog_refresh), { vm.loadHome(fresh = true) }, icon = Icons.Rounded.Refresh)
            if (recent.isNotEmpty()) {
                Divider()
                Item(stringResource(R.string.catalog_clear_history), vm::clearRecent, icon = Icons.Rounded.History, destructive = true)
            }
        }
    }
}

/** The current catalog's name under the title: a menu of favorites and «Все каталоги…» (the sheet). */
@Composable
private fun CatalogSwitcher(vm: CatalogViewModel, onOpen: () -> Unit) {
    val c = Folio.colors
    val saved by vm.catalogs.collectAsStateWithLifecycle()
    val favorites = saved.filter { it.favorite }
    var menu by remember { mutableStateOf(false) }
    Box(Modifier.padding(start = 26.dp, bottom = 12.dp)) {
        Row(
            Modifier.clip(RoundedCornerShape(10.dp)).clickable { if (favorites.isEmpty()) onOpen() else menu = true }.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                vm.catalog?.title ?: stringResource(R.string.catalog_none),
                style = FolioType.headline, color = c.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Icon(Icons.Rounded.UnfoldMore, stringResource(R.string.catalog_catalogs), tint = c.secondaryLabel, modifier = Modifier.padding(start = 2.dp).size(20.dp))
        }
        PullDownMenu(menu, { menu = false }, checkColumn = true) {
            favorites.forEach { catalog -> Item(catalog.title, { vm.select(catalog) }, checked = catalog.id == vm.catalog?.id) }
            Divider()
            Item(stringResource(R.string.catalog_all_catalogs), onOpen, icon = Icons.Rounded.Dns)
        }
    }
}

/** Feed-level filters as capsules; the active one is filled. */
@Composable
private fun FacetRow(groups: List<FacetGroup>, onPick: (Facet) -> Unit) {
    val c = Folio.colors
    Column(Modifier.padding(bottom = 8.dp)) {
        groups.forEach { group ->
            if (group.title.isNotBlank()) {
                Text(group.title, style = FolioType.footnote, color = c.secondaryLabel, modifier = Modifier.padding(start = 32.dp, top = 6.dp, bottom = 6.dp))
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 28.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(group.facets, key = { it.href + it.title }) { facet ->
                    Text(
                        facet.title,
                        style = FolioType.subheadline,
                        color = if (facet.active) c.background else c.label,
                        maxLines = 1,
                        modifier = Modifier.clip(CircleShape).background(if (facet.active) c.label else c.fill)
                            .clickable(enabled = !facet.active) { onPick(facet) }.padding(horizontal = 14.dp, vertical = 7.dp),
                    )
                }
            }
        }
    }
}

/** ••• on a folder: add it as a catalog of its own (a directory like OPDS Home lists whole catalogs). */
@Composable
private fun FolderMenu(vm: CatalogViewModel, feed: Feed) {
    var open by remember { mutableStateOf(false) }
    var added by remember(feed) { mutableStateOf(false) }
    Box {
        CircleButton(Icons.Rounded.MoreHoriz, stringResource(R.string.catalog_more_actions), { open = true })
        PullDownMenu(open, { open = false }) {
            Item(
                stringResource(if (added) R.string.catalog_added else R.string.catalog_add_folder),
                { if (!added) added = vm.addAsCatalog(feed) == null },
                icon = Icons.Rounded.Add,
                checked = added,
            )
        }
    }
}

@Composable
private fun NoCatalogBlock(onAdd: () -> Unit) {
    Column {
        EmptyBlock(stringResource(R.string.catalog_none), stringResource(R.string.catalog_none_text))
        Text(
            stringResource(R.string.catalog_add),
            style = FolioType.headline, color = Folio.colors.label, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onAdd).padding(vertical = 12.dp),
        )
    }
}
