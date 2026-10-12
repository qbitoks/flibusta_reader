package net.flibustareader.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.outlined.ArrowCircleRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.flibustareader.core.data.settings.SystemCollection
import net.flibustareader.core.designsystem.Flibusta
import net.flibustareader.core.designsystem.FlibustaType
import net.flibustareader.core.designsystem.components.CircleButton
import net.flibustareader.core.designsystem.components.GlassCapsule
import net.flibustareader.core.designsystem.components.GroupedCard
import net.flibustareader.core.designsystem.components.GroupedRow
import net.flibustareader.core.designsystem.components.FlibustaSheet
import net.flibustareader.core.designsystem.components.SheetHeader
import sh.calvin.reorderable.ReorderableColumn

internal fun SystemCollection.icon(): ImageVector = when (this) {
    SystemCollection.WantToRead -> Icons.Outlined.ArrowCircleRight
    SystemCollection.Finished -> Icons.Outlined.CheckCircle
    SystemCollection.Books -> Icons.AutoMirrored.Outlined.MenuBook
}

@Composable
internal fun SystemCollection.title(): String = stringResource(
    when (this) {
        SystemCollection.WantToRead -> R.string.collection_want_to_read
        SystemCollection.Finished -> R.string.collection_finished
        SystemCollection.Books -> R.string.collection_books
    },
)

@Composable
internal fun CollectionRow.title(): String = system?.title() ?: name.orEmpty()

/** «Коллекции» (white spots 23): system collections, then user ones, with an edit mode. */
@Composable
fun CollectionsSheet(vm: LibraryViewModel, onOpen: (Shelf) -> Unit, onDismiss: () -> Unit) {
    val rows by vm.collectionRows.collectAsStateWithLifecycle()
    val allRows by vm.allCollectionRows.collectAsStateWithLifecycle()
    val prefs by vm.librarySettings.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<CollectionRow?>(null) }

    FlibustaSheet(onDismiss = onDismiss, containerColor = Flibusta.colors.groupedBackground) {
        if (editing) {
            SheetHeader(
                title = stringResource(R.string.collections_title),
                titleStyle = FlibustaType.sheetTitle,
                trailing = { CircleButton(Icons.Rounded.Check, stringResource(R.string.library_done), { editing = false }) },
            )
        } else {
            SheetHeader(
                title = stringResource(R.string.collections_title),
                centered = false,
                trailing = {
                    GlassCapsule(stringResource(R.string.collections_edit), { editing = true })
                    Spacer(Modifier.width(12.dp))
                    CircleButton(Icons.Rounded.Close, stringResource(R.string.cancel), onDismiss)
                },
            )
        }
        Column(Modifier.fillMaxHeight(0.9f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Spacer(Modifier.height(4.dp))
            val system = (if (editing) allRows else rows).filter { it.system != null }
            if (system.isNotEmpty()) {
                GroupedCard {
                    system.forEachIndexed { i, row ->
                        val hidden = row.system in prefs.hiddenSystemCollections
                        GroupedRow(
                            title = row.title(),
                            icon = row.system!!.icon(),
                            value = row.count.takeIf { it > 0 }?.toString(),
                            chevron = true,
                            titleColor = if (hidden) Flibusta.colors.tertiaryLabel else Flibusta.colors.label,
                            showDivider = i < system.lastIndex,
                            editing = editing,
                            onRemove = { vm.setSystemHidden(row.system, !hidden) },
                            onClick = { onOpen(row.shelf) },
                        )
                    }
                }
            }
            val user = allRows.filter { it.system == null }
            GroupedCard {
                ReorderableColumn(
                    list = user,
                    onSettle = { from, to ->
                        val ids = user.map { (it.shelf as Shelf.Collection).id }.toMutableList()
                        ids.add(to, ids.removeAt(from))
                        vm.reorderCollections(ids)
                    },
                ) { _, row, _ ->
                    key(row.shelf.key) {
                        ReorderableItem {
                            val id = (row.shelf as Shelf.Collection).id
                            GroupedRow(
                                title = row.title(),
                                icon = Icons.AutoMirrored.Rounded.Notes,
                                value = row.count.takeIf { it > 0 }?.toString(),
                                chevron = true,
                                editing = editing,
                                onRemove = { vm.deleteCollection(id) },
                                reorderable = true,
                                handleModifier = Modifier.draggableHandle(),
                                onClick = { if (editing) renaming = row else onOpen(row.shelf) },
                            )
                        }
                    }
                }
                if (!editing) {
                    GroupedRow(
                        title = stringResource(R.string.collections_new),
                        icon = Icons.Rounded.Add,
                        showDivider = false,
                        onClick = { creating = true },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (creating) {
        TextInputDialog(
            title = stringResource(R.string.collections_new).trimEnd('…'),
            hint = stringResource(R.string.collection_name),
            confirm = stringResource(R.string.collection_create),
            onConfirm = { vm.createCollection(it); creating = false },
            onDismiss = { creating = false },
        )
    }
    renaming?.let { row ->
        TextInputDialog(
            title = stringResource(R.string.collection_rename),
            hint = stringResource(R.string.collection_name),
            confirm = stringResource(R.string.ok),
            initial = row.name.orEmpty(),
            onConfirm = { vm.renameCollection((row.shelf as Shelf.Collection).id, it); renaming = null },
            onDismiss = { renaming = null },
        )
    }
}

/**
 * «Добавить в коллекцию» (white spot 20): same cards as «Коллекции»; «Книги» is greyed out
 * because every book is already in it.
 */
@Composable
fun AddToCollectionSheet(vm: LibraryViewModel, bookIds: Set<String>, onDone: () -> Unit) {
    val rows by vm.allCollectionRows.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    FlibustaSheet(onDismiss = onDone, containerColor = Flibusta.colors.groupedBackground) {
        SheetHeader(
            title = stringResource(R.string.collections_add_to),
            titleStyle = FlibustaType.sheetTitle,
            trailing = { CircleButton(Icons.Rounded.Close, stringResource(R.string.cancel), onDone) },
        )
        Column(Modifier.fillMaxHeight(0.9f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Spacer(Modifier.height(4.dp))
            val system = rows.filter { it.system != null }
            GroupedCard {
                system.forEachIndexed { i, row ->
                    val addable = row.system != SystemCollection.Books
                    GroupedRow(
                        title = row.title(),
                        icon = row.system!!.icon(),
                        value = row.count.takeIf { it > 0 }?.toString(),
                        enabled = addable,
                        showDivider = i < system.lastIndex,
                        onClick = { vm.addTo(row.shelf, bookIds); onDone() },
                    )
                }
            }
            GroupedCard {
                rows.filter { it.system == null }.forEach { row ->
                    GroupedRow(
                        title = row.title(),
                        icon = Icons.AutoMirrored.Rounded.Notes,
                        value = row.count.takeIf { it > 0 }?.toString(),
                        onClick = { vm.addTo(row.shelf, bookIds); onDone() },
                    )
                }
                GroupedRow(
                    title = stringResource(R.string.collections_new),
                    icon = Icons.Rounded.Add,
                    showDivider = false,
                    onClick = { creating = true },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (creating) {
        TextInputDialog(
            title = stringResource(R.string.collections_new).trimEnd('…'),
            hint = stringResource(R.string.collection_name),
            confirm = stringResource(R.string.collection_create),
            onConfirm = { vm.createCollection(it, bookIds); creating = false; onDone() },
            onDismiss = { creating = false },
        )
    }
}
