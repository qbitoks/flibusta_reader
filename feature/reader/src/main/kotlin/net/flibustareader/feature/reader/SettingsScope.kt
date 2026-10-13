package net.flibustareader.feature.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Publish
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import net.flibustareader.core.data.settings.BookOverrides
import net.flibustareader.core.designsystem.Flibusta
import net.flibustareader.core.designsystem.FlibustaType
import net.flibustareader.core.designsystem.components.PullDownMenu

/**
 * «Для всех книг ⌄» / «Только для этой книги ⌄» under the panel title: where edits go, plus moving
 * a book's own settings to all books or dropping them. Both of those ask first.
 */
@Composable
internal fun SettingsScopeButton(
    bookScope: Boolean,
    own: BookOverrides,
    onScope: (Boolean) -> Unit,
    onShare: () -> Unit,
    onReset: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<Boolean?>(null) }
    val c = Flibusta.colors
    Box {
        Row(Modifier.clip(CircleShape).clickable(role = Role.Button) { open = true }.heightIn(min = 32.dp).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(if (bookScope) R.string.settings_scope_book else R.string.settings_scope_all),
                style = FlibustaType.subheadline, color = c.secondaryLabel, maxLines = 1)
            Icon(Icons.Rounded.KeyboardArrowDown, null, tint = c.secondaryLabel, modifier = Modifier.size(18.dp))
        }
        PullDownMenu(open, { open = false }, width = 280.dp, checkColumn = true) {
            ownSummary(own)?.let { Header(it) }
            Item(stringResource(R.string.settings_scope_all), { onScope(false) }, icon = Icons.AutoMirrored.Rounded.LibraryBooks, checked = !bookScope)
            Item(stringResource(R.string.settings_scope_book), { onScope(true) }, icon = Icons.AutoMirrored.Rounded.MenuBook, checked = bookScope)
            if (!own.isEmpty) {
                Divider()
                Item(stringResource(R.string.settings_share_own), { confirm = true }, icon = Icons.Rounded.Publish)
                Item(stringResource(R.string.settings_reset_own), { confirm = false }, icon = Icons.AutoMirrored.Rounded.Undo, destructive = true)
            }
        }
    }
    confirm?.let { share ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            containerColor = c.surface,
            title = { Text(stringResource(if (share) R.string.settings_share_own_title else R.string.settings_reset_own_title), style = FlibustaType.headline) },
            text = { Text(stringResource(if (share) R.string.settings_share_own_text else R.string.settings_reset_own_text), style = FlibustaType.subheadline) },
            confirmButton = {
                TextButton(onClick = { confirm = null; if (share) onShare() else onReset() }) {
                    Text(stringResource(if (share) R.string.settings_share_own_confirm else R.string.settings_reset_own_confirm),
                        color = if (share) c.label else c.destructive)
                }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.settings_cancel), color = c.secondaryLabel) } },
        )
    }
}

/** «В этой книге изменено: тема, шрифт» — groups the book sets itself, in panel order. */
@Composable
private fun ownSummary(own: BookOverrides): String? {
    if (own.isEmpty) return null
    val groups = listOf(
        listOf(BookOverrides.THEME) to R.string.settings_own_theme,
        listOf(BookOverrides.APPEARANCE) to R.string.settings_own_appearance,
        listOf(BookOverrides.FONT_SIZE) to R.string.settings_own_size,
        BookOverrides.TEXT to R.string.settings_own_text,
        listOf(BookOverrides.PAGE_TURN) to R.string.settings_own_turn,
        BookOverrides.HEADER_KEYS to R.string.settings_own_header,
        BookOverrides.FOOTER_KEYS to R.string.settings_own_footer,
    ).filter { own.has(it.first) }.map { stringResource(it.second) }
    if (groups.isEmpty()) return null
    return stringResource(R.string.settings_own_summary, groups.joinToString(", "))
}
