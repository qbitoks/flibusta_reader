package app.folio.feature.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.folio.core.layout.Page
import app.folio.core.model.Block
import java.io.File
import androidx.compose.runtime.Stable
import androidx.compose.runtime.MutableState

internal enum class ReaderSheet { Contents, Bookmarks, Search, Themes, Position, Voice }

/** Screen-local overlays; window preferences are saveable. */
@Stable
internal class ReaderControls(
    chromeVisible: MutableState<Boolean>,
    rotationLocked: MutableState<Boolean>,
    brightness: MutableState<Float>,
) {
    var chromeVisible by chromeVisible
    var rotationLocked by rotationLocked
    var brightness by brightness
    var sheet by mutableStateOf<ReaderSheet?>(null)
    var note by mutableStateOf<List<Block>?>(null)
    var image by mutableStateOf<File?>(null)
    var guide by mutableStateOf(GuideNavigation())
    var guideMoving by mutableStateOf(false)
    var noteFor by mutableStateOf<NoteTarget?>(null)
    var activeHighlight by mutableStateOf<Long?>(null)
    var focusPage by mutableStateOf<Page?>(null)
    /** «Слушать» opened the speech player; it shows with the controls only. */
    var speechPlayer by mutableStateOf(false)
    /** Height of the bottom navigation panel while shown; the speech mini-player floats above it. */
    var bottomPanelHeight by mutableStateOf(androidx.compose.ui.unit.Dp(0f))
    /** Page turns asked by hardware keys: +1 forward, −1 back. */
    val keyTurns = kotlinx.coroutines.flow.MutableSharedFlow<Int>(extraBufferCapacity = 4)
}

@Composable
internal fun rememberReaderControls(): ReaderControls {
    // Open books with controls showing; keep saveable state at the screen's lifetime.
    val chrome = rememberSaveable { mutableStateOf(true) }
    val rotation = rememberSaveable { mutableStateOf(false) }
    val brightness = rememberSaveable { mutableFloatStateOf(-1f) }
    return remember { ReaderControls(chrome, rotation, brightness) }
}
