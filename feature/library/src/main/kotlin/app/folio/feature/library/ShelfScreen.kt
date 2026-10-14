package app.folio.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.MoreHoriz
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folio.core.data.settings.SystemCollection
import app.folio.core.designsystem.BookCover
import app.folio.core.designsystem.BookTransition
import app.folio.core.designsystem.bookTransition
import app.folio.core.designsystem.Folio
import app.folio.core.designsystem.FolioType
import app.folio.core.designsystem.components.ActionPill
import app.folio.core.designsystem.components.CircleButton
import app.folio.core.designsystem.components.PullDownMenu

@Composable
fun shelfTitle(vm: LibraryViewModel, shelf: Shelf): String = when (shelf) {
    Shelf.WantToRead -> SystemCollection.WantToRead.title()
    Shelf.Finished -> SystemCollection.Finished.title()
    Shelf.Books -> SystemCollection.Books.title()
    Shelf.Previous -> stringResource(R.string.home_previous)
    is Shelf.Collection -> vm.collectionName(shelf.id).orEmpty()
}

/**
 * A collection (white spots 22, 24) or «Предыдущие» (26). Collections use a large list with a
 * «ЧИТАТЬ» pill; «Предыдущие» uses a compact list under an inline title.
 */
@Composable
fun ShelfScreen(
    vm: LibraryViewModel,
    shelf: Shelf,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onAddToCollection: (Set<String>) -> Unit,
) {
    val books by remember(shelf) { vm.shelfBooks(shelf) }.collectAsStateWithLifecycle(emptyList())
    val title = shelfTitle(vm, shelf)
    val context = MenuContext.InShelf(shelf, title)
    val addToCollection: (BookItem) -> Unit = { onAddToCollection(setOf(it.id)) }
    val compact = shelf == Shelf.Previous

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 24.dp)) {
        item {
            Box(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
                CircleButton(Icons.AutoMirrored.Rounded.ArrowBackIos, stringResource(R.string.library_back), onBack, modifier = Modifier.align(Alignment.CenterStart))
                if (compact) {
                    Text(title, style = FolioType.sheetTitle, color = Folio.colors.label, modifier = Modifier.align(Alignment.Center))
                } else if (shelf is Shelf.Collection) {
                    Box(Modifier.align(Alignment.CenterEnd)) { CollectionMenu(vm, shelf, title, onBack) }
                }
            }
            if (!compact) {
                Text(
                    title,
                    style = FolioType.largeTitle,
                    color = Folio.colors.label,
                    modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 4.dp, bottom = 16.dp),
                )
            }
        }
        if (books.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.collection_empty_title), style = FolioType.sheetTitle, color = Folio.colors.secondaryLabel)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.collection_empty_text), style = FolioType.subheadline, color = Folio.colors.secondaryLabel, textAlign = TextAlign.Center)
                }
            }
        }
        items(books, key = { it.id }) { item ->
            if (compact) {
                CompactRow(vm, item, onOpen, addToCollection)
            } else {
                LargeRow(vm, item, context, onOpen, addToCollection)
            }
        }
    }
}

@Composable
private fun LargeRow(vm: LibraryViewModel, item: BookItem, context: MenuContext, onOpen: (String) -> Unit, onAddToCollection: (BookItem) -> Unit) {
    val c = Folio.colors
    Column(Modifier.padding(horizontal = 32.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(127.dp).heightIn(max = 190.dp), contentAlignment = Alignment.Center) {
                BookCover(
                    item.title, item.author, item.cover,
                    Modifier.fillMaxWidth().bookTransition("shelf-${item.id}").clickable { BookTransition.open("shelf-${item.id}", item.id, onOpen) },
                    elevation = 8.dp, naturalAspect = true,
                )
            }
            Column(Modifier.weight(1f).padding(start = 24.dp)) {
                Text(item.title, style = FolioType.bookTitle, color = c.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(item.author ?: stringResource(R.string.book_unknown_author), style = FolioType.subheadline, color = c.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionPill(stringResource(R.string.book_read), { BookTransition.open("shelf-${item.id}", item.id, onOpen) })
                    MoreButton(vm, item, context, onAddToCollection)
                }
            }
        }
        HorizontalDivider(color = c.separator, thickness = 0.5.dp)
    }
}

@Composable
private fun CompactRow(vm: LibraryViewModel, item: BookItem, onOpen: (String) -> Unit, onAddToCollection: (BookItem) -> Unit) {
    val c = Folio.colors
    val e = item.entity
    Column(Modifier.padding(start = 32.dp, end = 24.dp)) {
        Row(Modifier.fillMaxWidth().clickable { BookTransition.open("shelf-${item.id}", item.id, onOpen) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(42.dp).height(64.dp), contentAlignment = Alignment.Center) {
                BookCover(item.title, item.author, item.cover, Modifier.fillMaxWidth().bookTransition("shelf-${item.id}"), elevation = 3.dp, naturalAspect = true)
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(item.title, style = FolioType.headline, color = c.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(item.author ?: stringResource(R.string.book_unknown_author), style = FolioType.subheadline, color = c.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.book_kind) + " • ", style = FolioType.subheadline, color = c.secondaryLabel)
                    if (e.isFinished) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = Folio.colors.badgeText, modifier = Modifier.size(15.dp))
                        Text(" " + stringResource(R.string.book_finished), style = FolioType.subheadline, color = c.secondaryLabel)
                    } else {
                        Text(stringResource(R.string.book_percent_done, (e.progress * 100).toInt()), style = FolioType.subheadline, color = c.secondaryLabel)
                    }
                }
            }
            MoreButton(vm, item, MenuContext.Library, onAddToCollection)
        }
        HorizontalDivider(color = c.separator, thickness = 0.5.dp, modifier = Modifier.padding(start = 56.dp))
    }
}

@Composable
private fun CollectionMenu(vm: LibraryViewModel, shelf: Shelf.Collection, title: String, onDeleted: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    Box {
        CircleButton(Icons.Rounded.MoreHoriz, stringResource(R.string.library_more), { open = true })
        PullDownMenu(open, { open = false }) {
            Item(stringResource(R.string.collection_rename), { renaming = true }, icon = Icons.Rounded.DriveFileRenameOutline)
            Item(stringResource(R.string.collection_delete), { vm.deleteCollection(shelf.id); onDeleted() }, icon = Icons.Rounded.DeleteOutline, destructive = true)
        }
    }
    if (renaming) {
        TextInputDialog(
            title = stringResource(R.string.collection_rename),
            hint = stringResource(R.string.collection_name),
            confirm = stringResource(R.string.ok),
            initial = title,
            onConfirm = { vm.renameCollection(shelf.id, it); renaming = false },
            onDismiss = { renaming = false },
        )
    }
}
