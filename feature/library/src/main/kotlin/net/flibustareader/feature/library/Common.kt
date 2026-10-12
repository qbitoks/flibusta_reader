package net.flibustareader.feature.library

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import net.flibustareader.core.data.library.BookSharing
import net.flibustareader.core.designsystem.Flibusta
import net.flibustareader.core.designsystem.FlibustaType
import net.flibustareader.core.designsystem.components.MenuAction
import net.flibustareader.core.designsystem.components.NewBadge
import net.flibustareader.core.designsystem.components.PullDownMenu

/** Large serif title with round glass buttons on the right (§ 3.2 of the guideline). */
@Composable
internal fun LargeTitleHeader(title: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 32.dp, end = 16.dp, top = 14.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = FlibustaType.largeTitle, color = Flibusta.colors.label, maxLines = 1, modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) { trailing() }
    }
}

/** Serif section header; with [onClick] it gets a grey chevron and becomes a link («Хочу прочесть ›»). */
@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier, subtitle: String? = null, onClick: (() -> Unit)? = null) {
    androidx.compose.foundation.layout.Column(modifier.padding(start = 32.dp, end = 32.dp, top = 24.dp, bottom = 14.dp)) {
        Row(
            (if (onClick != null) Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, style = FlibustaType.section, color = Flibusta.colors.label)
            if (onClick != null) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Flibusta.colors.tertiaryLabel, modifier = Modifier.size(30.dp))
            }
        }
        if (subtitle != null) Text(subtitle, style = FlibustaType.body, color = Flibusta.colors.secondaryLabel)
    }
}

/** Where a book menu is opened from: decides which items it shows (guideline, white spots 17–18, 22, 27). */
internal sealed interface MenuContext {
    data object Library : MenuContext
    data object Continue : MenuContext
    data class InShelf(val shelf: Shelf, val name: String) : MenuContext
}

/** Progress («22 %») or the «НОВАЯ» badge, then «•••» with the book menu. */
@Composable
internal fun BookCaption(
    vm: LibraryViewModel,
    item: BookItem,
    context: MenuContext,
    modifier: Modifier = Modifier,
    style: TextStyle = FlibustaType.subheadline,
    onAddToCollection: (BookItem) -> Unit,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val e = item.entity
        Box(Modifier.weight(1f)) {
            when {
                !e.isStarted && !e.isFinished -> NewBadge(stringResource(R.string.book_new))
                e.isFinished -> Text(stringResource(R.string.book_finished), style = style, color = Flibusta.colors.secondaryLabel)
                else -> Text(stringResource(R.string.book_percent, (e.progress * 100).toInt()), style = style, color = Flibusta.colors.secondaryLabel)
            }
        }
        MoreButton(vm, item, context, onAddToCollection)
    }
}

@Composable
internal fun MoreButton(vm: LibraryViewModel, item: BookItem, context: MenuContext, onAddToCollection: (BookItem) -> Unit, tint: Color = Flibusta.colors.secondaryLabel) {
    var open by remember { mutableStateOf(false) }
    Box {
        Icon(
            Icons.Rounded.MoreHoriz,
            contentDescription = stringResource(R.string.library_more),
            tint = tint,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { open = true }.padding(4.dp).size(22.dp),
        )
        BookMenu(vm, item, context, open, onDismiss = { open = false }, onAddToCollection = onAddToCollection)
    }
}

@Composable
internal fun BookMenu(
    vm: LibraryViewModel,
    item: BookItem,
    context: MenuContext,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onAddToCollection: (BookItem) -> Unit,
) {
    val ctx = LocalContext.current
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val e = item.entity
    val share = MenuAction(stringResource(R.string.action_share), Icons.Rounded.IosShare) { shareBook(ctx, vm, item) }
    val want = MenuAction(
        stringResource(R.string.action_want_to_read),
        if (e.wantToRead) Icons.Rounded.CheckCircle else Icons.Rounded.AddCircle,
    ) { vm.setWantToRead(item.id, !e.wantToRead) }

    PullDownMenu(expanded, onDismiss, width = 280.dp) {
        if (context is MenuContext.InShelf) Actions(share) else Actions(share, want)
        if (context is MenuContext.InShelf && context.shelf != Shelf.Books) {
            Item(stringResource(R.string.action_remove_from, context.name), { vm.removeFrom(context.shelf, item.id) }, icon = Icons.Rounded.RemoveCircleOutline)
        }
        Item(stringResource(R.string.action_add_to_collection), { onAddToCollection(item) }, icon = Icons.AutoMirrored.Rounded.PlaylistAdd)
        Item(
            stringResource(if (e.isFinished) R.string.action_mark_unfinished else R.string.action_mark_finished),
            { vm.setFinished(item.id, !e.isFinished) },
            icon = Icons.Rounded.CheckCircleOutline,
        )
        Divider()
        Item(stringResource(R.string.action_rename), { renaming = true }, icon = Icons.Rounded.DriveFileRenameOutline)
        Divider()
        if (context == MenuContext.Continue) {
            Item(stringResource(R.string.action_remove_from_continue), { vm.removeFromContinue(item.id) }, icon = Icons.Rounded.RemoveCircleOutline)
        }
        Item(stringResource(R.string.action_delete), { deleting = true }, icon = Icons.Rounded.DeleteOutline, destructive = true)
    }

    if (renaming) {
        TextInputDialog(
            title = stringResource(R.string.rename_title),
            hint = stringResource(R.string.rename_hint),
            confirm = stringResource(R.string.ok),
            initial = item.title,
            onConfirm = { vm.rename(item.id, it); renaming = false },
            onDismiss = { renaming = false },
        )
    }
    if (deleting) {
        DeleteBooksDialog(count = 1, onConfirm = { vm.delete(listOf(item.id)); deleting = false }, onDismiss = { deleting = false })
    }
}

internal fun shareBook(context: Context, vm: LibraryViewModel, item: BookItem) {
    val file = vm.bookFile(item) ?: return
    BookSharing.share(context, listOf(file to item.title))
}

@Composable
internal fun TextInputDialog(
    title: String,
    hint: String,
    confirm: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    initial: String = "",
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Flibusta.colors.surface,
        title = { Text(title, style = FlibustaType.headline) },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, placeholder = { Text(hint) })
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text(confirm, color = Flibusta.colors.label) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = Flibusta.colors.secondaryLabel) } },
    )
}

@Composable
internal fun DeleteBooksDialog(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Flibusta.colors.surface,
        title = {
            Text(
                if (count == 1) stringResource(R.string.delete_title) else pluralStringResource(R.plurals.delete_many_title, count, count),
                style = FlibustaType.headline,
            )
        },
        text = { Text(stringResource(R.string.delete_text), style = FlibustaType.subheadline) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.delete_confirm), color = Flibusta.colors.destructive) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = Flibusta.colors.label) } },
    )
}
