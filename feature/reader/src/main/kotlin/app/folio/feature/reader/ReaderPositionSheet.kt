package app.folio.feature.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.folio.core.designsystem.Folio
import app.folio.core.designsystem.FolioType
import app.folio.core.designsystem.components.CapsuleButton
import app.folio.core.designsystem.components.CircleButton
import app.folio.core.designsystem.components.GroupedCard
import app.folio.core.designsystem.components.GroupHeader
import app.folio.core.designsystem.components.SheetHeader
import app.folio.core.model.progressAt

/** Use the settings dialog geometry: fixed header, one scroll body, and window-wide IME insets. */
@Composable
internal fun ReaderPositionSheet(vm: ReaderViewModel, onDismiss: () -> Unit) {
    val state = vm.state
    val total = state.totalPages
    var mode by rememberSaveable { mutableIntStateOf(if (total == null) 1 else 0) }
    var input by rememberSaveable(mode, stateSaver = TextFieldValue.Saver) {
        val text = if (mode == 0) state.globalPage?.toString().orEmpty() else (state.progress * 100).toInt().toString()
        mutableStateOf(TextFieldValue(text))
    }
    var fieldFocused by remember { mutableStateOf(false) }
    LaunchedEffect(fieldFocused, mode) {
        if (fieldFocused) input = input.copy(selection = TextRange(0, input.text.length))
    }
    val page = input.text.trim().toIntOrNull()?.takeIf { total != null && it in 1..total }
    val percent = readerPercentInput(input.text)
    val valid = state.page != null && if (mode == 0) page != null else percent != null
    val c = Folio.colors
    val history = (vm.history.back.asReversed() + vm.history.forward.asReversed())
        .distinct().filter { it != vm.readingPosition }.take(20)
    fun jump() {
        if (!valid) return
        if (mode == 0) vm.goToPage(checkNotNull(page)) else vm.goToProgress(checkNotNull(percent))
        onDismiss()
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(
        usePlatformDefaultWidth = false, dismissOnClickOutside = false, decorFitsSystemWindows = false,
    )) {
        Surface(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding(),
            shape = RoundedCornerShape(topStart = 23.dp, topEnd = 23.dp), color = c.groupedBackground) {
            Column(Modifier.fillMaxSize()) {
                SheetHeader(stringResource(R.string.reader_position_title), trailing = {
                    CircleButton(Icons.Rounded.Close, stringResource(R.string.reader_close), onDismiss)
                })
                HorizontalDivider(color = c.separator, thickness = 0.5.dp)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState(), overscrollEffect = null).padding(bottom = 24.dp)) {
                    if (total != null) {
                        Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            SettingsSegments(mode, listOf(
                                SegmentOption(0, stringResource(R.string.reader_page_word)),
                                SegmentOption(1, stringResource(R.string.reader_percent_label)),
                            )) { mode = it }
                        }
                    } else Text(stringResource(R.string.reader_scrub_loading), style = FolioType.footnote,
                        color = c.secondaryLabel, modifier = Modifier.padding(horizontal = 34.dp, vertical = 16.dp))
                    val label = stringResource(if (mode == 0) R.string.reader_page_word else R.string.reader_percent_label)
                    GroupedCard {
                        Text(label, style = FolioType.subheadline, color = c.secondaryLabel,
                            modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 14.dp))
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 18.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            BasicTextField(
                                value = input, onValueChange = { input = it }, singleLine = true,
                                textStyle = FolioType.title3.copy(color = c.label), cursorBrush = SolidColor(c.label),
                                keyboardOptions = KeyboardOptions(keyboardType = if (mode == 0) KeyboardType.Number else KeyboardType.Decimal,
                                    imeAction = ImeAction.Go), keyboardActions = KeyboardActions(onGo = { jump() }),
                                modifier = Modifier.weight(1f).onFocusChanged { fieldFocused = it.isFocused }
                                    .semantics { contentDescription = label },
                            )
                            Text(if (mode == 0) "/ $total" else "%", style = FolioType.body, color = c.secondaryLabel)
                        }
                    }
                    val range = if (mode == 0 && total != null) stringResource(R.string.reader_page_range, total)
                        else stringResource(R.string.reader_percent_range)
                    Text(range, style = FolioType.footnote, color = c.secondaryLabel,
                        modifier = Modifier.padding(horizontal = 34.dp, vertical = 8.dp).semantics {
                            if (input.text.isNotEmpty() && !valid) error(range)
                        })
                    CapsuleButton(stringResource(R.string.reader_go_to), ::jump, enabled = valid,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth())
                    GroupHeader(stringResource(R.string.reader_recent_jumps))
                    GroupedCard {
                        if (history.isEmpty()) Text(stringResource(R.string.reader_no_jumps), style = FolioType.body,
                            color = c.secondaryLabel, modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp))
                        history.forEachIndexed { index, target ->
                            if (index > 0) HorizontalDivider(color = c.separator, thickness = 0.5.dp,
                                modifier = Modifier.padding(start = 18.dp))
                            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button) {
                                vm.goTo(target)
                                onDismiss()
                            }.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(vm.chapterTitleAt(target) ?: stringResource(R.string.reader_untitled),
                                    style = FolioType.body, color = c.label, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f))
                                Text(vm.pageNumberOf(target)?.let { stringResource(R.string.reader_scrub_page, it) }
                                    ?: stringResource(R.string.reader_percent, ((vm.book?.progressAt(target) ?: 0f) * 100).toInt()),
                                    style = FolioType.footnote, color = c.secondaryLabel)
                            }
                        }
                    }
                }
            }
        }
    }
}
