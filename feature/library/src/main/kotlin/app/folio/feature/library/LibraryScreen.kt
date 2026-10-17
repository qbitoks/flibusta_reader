package app.folio.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.folio.core.data.library.BookSharing
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folio.core.data.settings.LibraryLayout
import app.folio.core.data.settings.LibrarySort
import app.folio.core.designsystem.BookCover
import app.folio.core.designsystem.BookTransition
import app.folio.core.designsystem.bookTransition
import app.folio.core.designsystem.Folio
import app.folio.core.designsystem.FolioType
import app.folio.core.designsystem.components.LargeTitleHeader
import app.folio.core.designsystem.components.CircleButton
import app.folio.core.designsystem.components.CircleButtonStyle
import app.folio.core.designsystem.components.GlassCapsule
import app.folio.core.designsystem.components.PullDownMenu
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState

/** Actions of the library «•••» menu that need the host (file pickers, navigation). */
class LibraryHostActions(
    val onAddBooks: () -> Unit,
    val onPickFolder: () -> Unit,
    val onCollections: () -> Unit,
    val onAddToCollection: (Set<String>) -> Unit,
)

/** «Библиотека» (guideline § 5, white spots 19–21). */
@Composable
fun LibraryScreen(
    vm: LibraryViewModel,
    contentPadding: PaddingValues,
    onOpen: (String) -> Unit,
    actions: LibraryHostActions,
    modifier: Modifier = Modifier,
) {
    val books by vm.libraryBooks.collectAsStateWithLifecycle()
    val prefs by vm.librarySettings.collectAsStateWithLifecycle()
    val grid = prefs.layout == LibraryLayout.Grid
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    var deleting by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    val reorder = rememberReorderableLazyGridState(gridState) { from, to ->
        val fromKey = from.key as? String
        val toKey = to.key as? String
        if (fromKey != null && toKey != null) {
            vm.moveBook(fromKey, toKey)
            haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
    }
    val addToCollection: (BookItem) -> Unit = { actions.onAddToCollection(setOf(it.id)) }

    // «Назад» and leaving the screen both end selection mode; otherwise the hidden tab bar stays hidden.
    BackHandler(enabled = vm.selecting) { vm.endSelection() }
    DisposableEffect(Unit) { onDispose { vm.endSelection() } }

    Box(modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(if (grid) 2 else 1),
            contentPadding = PaddingValues(
                start = if (grid) 32.dp else 0.dp,
                end = if (grid) 32.dp else 0.dp,
                bottom = contentPadding.calculateBottomPadding() + 24.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalArrangement = Arrangement.spacedBy(if (grid) 22.dp else 0.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "header") {
                Box(if (grid) Modifier.bleed(32.dp) else Modifier) {
                    if (vm.selecting) {
                        LargeTitleHeader(stringResource(R.string.library_title)) {
                            GlassCapsule(stringResource(R.string.library_select_all), { vm.selectAll(books.map { it.id }) })
                            CircleButton(Icons.Rounded.Check, stringResource(R.string.library_done), vm::endSelection, style = CircleButtonStyle.Confirm)
                        }
                    } else {
                        LargeTitleHeader(stringResource(R.string.library_title)) {
                            CircleButton(Icons.AutoMirrored.Rounded.Sort, stringResource(R.string.library_collections), actions.onCollections)
                            LibraryMenuButton(vm, actions)
                        }
                    }
                }
            }
            items(books, key = { it.id }) { item ->
                if (grid) {
                    ReorderableItem(reorder, key = item.id, enabled = !vm.selecting) { dragging ->
                        GridBook(
                            vm = vm,
                            item = item,
                            selected = item.id in vm.selection,
                            coverModifier = Modifier.longPressDraggableHandle(
                                enabled = !vm.selecting,
                                onDragStarted = { haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) },
                            ),
                            dragging = dragging,
                            onClick = { if (vm.selecting) vm.toggleSelected(item.id) else BookTransition.open("library-${item.id}", item.id, onOpen) },
                            onAddToCollection = addToCollection,
                        )
                    }
                } else {
                    ListBook(
                        vm = vm,
                        item = item,
                        selected = item.id in vm.selection,
                        onClick = { if (vm.selecting) vm.toggleSelected(item.id) else BookTransition.open("library-${item.id}", item.id, onOpen) },
                        onAddToCollection = addToCollection,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = vm.selecting,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            val enabled = vm.selection.isNotEmpty()
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 21.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                CircleButton(Icons.Rounded.DeleteOutline, stringResource(R.string.library_delete_selected), { deleting = true }, size = 52.dp, enabled = enabled)
                GlassCapsule(
                    stringResource(R.string.library_add_to),
                    { if (enabled) actions.onAddToCollection(vm.selection) },
                    icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                    height = 52.dp,
                )
                CircleButton(
                    Icons.Rounded.IosShare,
                    stringResource(R.string.library_share),
                    {
                        BookSharing.share(context, books.filter { it.id in vm.selection }.mapNotNull { b -> vm.bookFile(b)?.let { it to b.title } })
                    },
                    size = 52.dp,
                    enabled = enabled,
                )
            }
        }
    }

    if (deleting) {
        DeleteBooksDialog(
            count = vm.selection.size,
            onConfirm = {
                vm.delete(vm.selection)
                vm.endSelection()
                deleting = false
            },
            onDismiss = { deleting = false },
        )
    }
}

@Composable
private fun LibraryMenuButton(vm: LibraryViewModel, actions: LibraryHostActions) {
    val prefs by vm.librarySettings.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    Box {
        CircleButton(Icons.Rounded.MoreHoriz, stringResource(R.string.library_more), { open = true })
        PullDownMenu(open, { open = false }, checkColumn = true) {
            Item(stringResource(R.string.library_select), vm::startSelection, icon = Icons.Rounded.CheckCircleOutline)
            Divider()
            Item(stringResource(R.string.library_view_grid), { vm.setLayout(LibraryLayout.Grid) }, icon = Icons.Rounded.GridView, checked = prefs.layout == LibraryLayout.Grid)
            Item(stringResource(R.string.library_view_list), { vm.setLayout(LibraryLayout.List) }, icon = Icons.AutoMirrored.Rounded.List, checked = prefs.layout == LibraryLayout.List)
            Divider()
            Header(stringResource(R.string.library_sort_header))
            Item(stringResource(R.string.library_sort_recent), { vm.setSort(LibrarySort.Recent) }, checked = prefs.sort == LibrarySort.Recent)
            Item(stringResource(R.string.library_sort_title), { vm.setSort(LibrarySort.Title) }, checked = prefs.sort == LibrarySort.Title)
            Item(stringResource(R.string.library_sort_author), { vm.setSort(LibrarySort.Author) }, checked = prefs.sort == LibrarySort.Author)
            Item(stringResource(R.string.library_sort_manual), { vm.setSort(LibrarySort.Manual) }, checked = prefs.sort == LibrarySort.Manual)
            Divider()
            Item(stringResource(R.string.library_add_books), actions.onAddBooks, icon = Icons.Rounded.FileOpen)
            Item(stringResource(R.string.library_watch_folder), actions.onPickFolder, icon = Icons.Rounded.CreateNewFolder)
            if (prefs.watchedFolder != null) {
                Item(stringResource(R.string.library_scan_now), vm::scanNow)
                Item(stringResource(R.string.library_stop_watching), { vm.watchFolder(null) })
            }
        }
    }
}

@Composable
private fun GridBook(
    vm: LibraryViewModel,
    item: BookItem,
    selected: Boolean,
    coverModifier: Modifier,
    dragging: Boolean,
    onClick: () -> Unit,
    onAddToCollection: (BookItem) -> Unit,
) {
    Column {
        // Covers of different proportions stand on a common baseline, like books on a shelf.
        Box(Modifier.fillMaxWidth().height(230.dp), contentAlignment = Alignment.BottomCenter) {
            Box(contentAlignment = Alignment.BottomEnd) {
                BookCover(
                    item.title,
                    item.author,
                    item.cover,
                    coverModifier.fillMaxWidth().heightIn(max = 230.dp).bookTransition("library-${item.id}").clickable(onClick = onClick),
                    elevation = if (dragging) 16.dp else 7.dp,
                    titleSize = androidx.compose.ui.unit.TextUnit(13f, androidx.compose.ui.unit.TextUnitType.Sp),
                    naturalAspect = true,
                )
                if (vm.selecting) SelectionMark(selected, Modifier.padding(8.dp))
            }
        }
        if (!vm.selecting) {
            BookCaption(vm, item, MenuContext.Library, Modifier.padding(top = 10.dp), onAddToCollection = onAddToCollection)
        }
    }
}

@Composable
private fun ListBook(
    vm: LibraryViewModel,
    item: BookItem,
    selected: Boolean,
    onClick: () -> Unit,
    onAddToCollection: (BookItem) -> Unit,
) {
    val c = Folio.colors
    Column(Modifier.padding(start = 32.dp, end = 24.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(52.dp).height(78.dp), contentAlignment = Alignment.Center) {
                BookCover(item.title, item.author, item.cover, Modifier.fillMaxWidth().heightIn(max = 78.dp).bookTransition("library-${item.id}"), elevation = 3.dp, naturalAspect = true)
            }
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text(item.title, style = FolioType.body, color = c.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(item.author ?: stringResource(R.string.book_unknown_author), style = FolioType.subheadline, color = c.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!vm.selecting) {
                    BookCaption(vm, item, MenuContext.Library, Modifier.padding(top = 2.dp), onAddToCollection = onAddToCollection)
                }
            }
            if (vm.selecting) SelectionMark(selected, Modifier.padding(start = 12.dp))
        }
        HorizontalDivider(color = c.separator, thickness = 0.5.dp)
    }
}

/** Round selection mark on a cover in «Выбор» mode. */
@Composable
internal fun SelectionMark(selected: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(if (selected) Color(0xFF1C1C1E) else Color.Black.copy(alpha = 0.15f))
            .border(1.5.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(15.dp))
    }
}

/** Lets a full-span header extend over the grid's horizontal content padding. */
internal fun Modifier.bleed(horizontal: Dp) = layout { measurable, constraints ->
    val extra = horizontal.roundToPx() * 2
    val placeable = measurable.measure(constraints.copy(minWidth = constraints.maxWidth + extra, maxWidth = constraints.maxWidth + extra))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
}
