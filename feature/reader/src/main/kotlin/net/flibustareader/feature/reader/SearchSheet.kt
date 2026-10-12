package net.flibustareader.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.flibustareader.core.designsystem.ReaderPalette
import net.flibustareader.core.designsystem.UiSans
import net.flibustareader.core.designsystem.components.FlibustaSheet
import kotlinx.coroutines.delay

/**
 * «Поиск в книге» (white spot 6): the field sits at the bottom above the keyboard; results show
 * the chapter and page over a fragment in the book's font with the match in bold.
 */
@Composable
internal fun SearchSheet(vm: ReaderViewModel, palette: ReaderPalette, bookFont: FontFamily, onDismiss: () -> Unit) {
    val search by vm.search.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf(search.query) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(query) {
        if (query != search.query) {
            delay(250)
            vm.search(query)
        }
    }
    val background = if (palette.isDark) Color(0xFF1C1C1E) else Color.White
    val rule = palette.secondary.copy(alpha = 0.25f)

    FlibustaSheet(onDismiss = onDismiss, containerColor = background) {
        Column(Modifier.fillMaxHeight().imePadding()) {
            Text(
                stringResource(R.string.reader_search),
                color = palette.text, fontFamily = UiSans, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 22.dp),
            )
            HorizontalDivider(color = rule, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 24.dp))
            LazyColumn(Modifier.weight(1f)) {
                items(search.results) { hit ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                vm.openSearchHit(hit)
                                onDismiss()
                            }
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                hit.chapterTitle.orEmpty(),
                                color = palette.text, fontFamily = UiSans, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                            )
                            vm.pageNumberOf(hit.position)?.let {
                                Text(it.toString(), color = palette.secondary, fontFamily = bookFont, fontSize = 16.sp, modifier = Modifier.padding(start = 12.dp))
                            }
                        }
                        Text(
                            buildAnnotatedString {
                                append(hit.snippet.substring(0, hit.matchStart))
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                    append(hit.snippet.substring(hit.matchStart, hit.matchStart + hit.matchLength))
                                }
                                append(hit.snippet.substring(hit.matchStart + hit.matchLength))
                            },
                            color = palette.text, fontFamily = bookFont, fontSize = 17.sp, lineHeight = 23.sp,
                            maxLines = 3, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    HorizontalDivider(color = rule, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 24.dp))
                }
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                        when {
                            search.searching -> CircularProgressIndicator(color = palette.secondary, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                            search.query.length >= 2 -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(stringResource(R.string.search_done), color = palette.text, fontFamily = UiSans, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    pluralStringResource(R.plurals.search_found, search.results.size, search.results.size),
                                    color = palette.text, fontFamily = UiSans, fontSize = 15.sp,
                                )
                            }
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val field = if (palette.isDark) Color(0xFF2C2C2E) else Color.White
                Row(
                    Modifier
                        .weight(1f)
                        .height(48.dp)
                        .background(field, CircleShape)
                        .border(0.5.dp, palette.secondary.copy(alpha = 0.3f), CircleShape)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Search, null, tint = palette.text, modifier = Modifier.size(22.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = palette.text, fontFamily = UiSans, fontSize = 17.sp),
                        cursorBrush = SolidColor(palette.link),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { vm.search(query) }),
                        modifier = Modifier.weight(1f).padding(horizontal = 10.dp).focusRequester(focus),
                        decorationBox = { inner ->
                            if (query.isEmpty()) Text(stringResource(R.string.search_hint), color = palette.secondary, fontFamily = UiSans, fontSize = 17.sp)
                            inner()
                        },
                    )
                    if (query.isNotEmpty()) {
                        Icon(Icons.Rounded.Cancel, null, tint = palette.secondary, modifier = Modifier.size(20.dp).clickable { query = ""; vm.search("") })
                    }
                }
                RoundButton(Icons.Rounded.Close, stringResource(R.string.reader_close), field, palette.text, size = 48.dp, onClick = onDismiss)
            }
        }
    }
}
