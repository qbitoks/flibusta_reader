package app.folio.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folio.core.designsystem.BookCover
import app.folio.core.designsystem.BookTransition
import app.folio.core.designsystem.bookTransition
import app.folio.core.designsystem.Folio
import app.folio.core.designsystem.FolioType
import app.folio.core.designsystem.components.LargeTitleHeader
import app.folio.core.designsystem.components.CircleButton
import app.folio.core.designsystem.components.glass

/**
 * Library search (white spot 25, decision E): the tab bar turns into a search field at the
 * bottom; above it — recent queries, or matching books while typing.
 */
@Composable
fun SearchScreen(vm: LibraryViewModel, onClose: () -> Unit, onOpen: (String) -> Unit) {
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.searchResults.collectAsStateWithLifecycle()
    val history by vm.searchHistory.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val c = Folio.colors

    Box(Modifier.fillMaxSize().imePadding()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)) {
            item { LargeTitleHeader(stringResource(R.string.search_title)) }
            if (query.isBlank()) {
                if (history.isNotEmpty()) {
                    item {
                        Row(Modifier.fillMaxWidth().padding(start = 32.dp, end = 24.dp, top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.search_recent), style = FolioType.sheetTitle, color = c.label, modifier = Modifier.weight(1f))
                            Text(stringResource(R.string.search_clear), style = FolioType.body, color = c.secondaryLabel, modifier = Modifier.clickable { vm.clearSearchHistory() })
                        }
                        HorizontalDivider(color = c.separator, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 32.dp))
                    }
                    items(history) { q ->
                        Row(Modifier.fillMaxWidth().clickable { vm.setQuery(q) }.padding(horizontal = 32.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Search, null, tint = c.secondaryLabel, modifier = Modifier.size(22.dp))
                            Text(q, style = FolioType.body, color = c.label, modifier = Modifier.padding(start = 12.dp))
                        }
                        HorizontalDivider(color = c.separator, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 32.dp))
                    }
                }
            } else if (results.isEmpty()) {
                item {
                    Text(stringResource(R.string.search_no_results), style = FolioType.sheetTitle, color = c.secondaryLabel, modifier = Modifier.fillMaxWidth().padding(48.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            } else {
                items(results, key = { it.id }) { item ->
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.commitSearch(); BookTransition.open("search-${item.id}", item.id, onOpen) }.padding(horizontal = 32.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.width(42.dp).height(64.dp), contentAlignment = Alignment.Center) {
                            BookCover(item.title, item.author, item.cover, Modifier.fillMaxWidth().bookTransition("search-${item.id}"), elevation = 3.dp, naturalAspect = true)
                        }
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Text(item.title, style = FolioType.headline, color = c.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(item.author ?: stringResource(R.string.book_unknown_author), style = FolioType.subheadline, color = c.secondaryLabel, maxLines = 1)
                        }
                    }
                }
            }
        }

        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(start = 21.dp, end = 21.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CircleButton(Icons.AutoMirrored.Rounded.LibraryBooks, stringResource(R.string.tab_library), { vm.setQuery(""); onClose() }, size = 52.dp)
            Row(
                Modifier.weight(1f).height(52.dp).glass(CircleShape, elevation = 4.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, tint = c.secondaryLabel, modifier = Modifier.size(22.dp))
                BasicTextField(
                    value = query,
                    onValueChange = vm::setQuery,
                    singleLine = true,
                    textStyle = FolioType.body.copy(color = c.label),
                    cursorBrush = SolidColor(c.label),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { vm.commitSearch() }),
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp).focusRequester(focus),
                    decorationBox = { inner ->
                        if (query.isEmpty()) Text(stringResource(R.string.search_hint), style = FolioType.body, color = c.secondaryLabel)
                        inner()
                    },
                )
                if (query.isNotEmpty()) {
                    Icon(Icons.Rounded.Cancel, null, tint = c.tertiaryLabel, modifier = Modifier.size(20.dp).clickable { vm.setQuery("") })
                }
            }
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit, versionName: String) {
    val c = Folio.colors
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
            CircleButton(Icons.AutoMirrored.Rounded.ArrowBackIos, stringResource(R.string.library_back), onBack)
        }
        Text(stringResource(R.string.about_title), style = FolioType.largeTitle, color = c.label, modifier = Modifier.padding(start = 32.dp, top = 4.dp, bottom = 12.dp))
        Row(Modifier.padding(horizontal = 32.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Info, null, tint = c.secondaryLabel, modifier = Modifier.size(20.dp))
            Text("Folio · " + stringResource(R.string.about_version, versionName), style = FolioType.body, color = c.label, modifier = Modifier.padding(start = 8.dp))
        }
        Text(stringResource(R.string.about_fonts), style = FolioType.sheetTitle, color = c.label, modifier = Modifier.padding(start = 32.dp, top = 28.dp, bottom = 6.dp))
        Text(stringResource(R.string.about_fonts_text), style = FolioType.subheadline, color = c.secondaryLabel, modifier = Modifier.padding(horizontal = 32.dp))
    }
}
