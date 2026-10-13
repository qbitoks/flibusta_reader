package net.flibustareader.feature.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import net.flibustareader.core.layout.Page
import net.flibustareader.core.model.Block
import java.io.File
import androidx.compose.runtime.Stable
import androidx.compose.runtime.MutableState

internal enum class ReaderSheet { Contents, Bookmarks, Search, Themes }

/** Screen-local overlays and controls; only the three original window preferences are saveable. */
@Stable
internal class ReaderControls(
    chromeVisible: MutableState<Boolean>,
    rotationLocked: MutableState<Boolean>,
    brightness: MutableState<Float>,
) {
    var chromeVisible by chromeVisible
    var rotationLocked by rotationLocked
    var brightness by brightness
    var menuVisible by mutableStateOf(false)
    var sheet by mutableStateOf<ReaderSheet?>(null)
    var note by mutableStateOf<List<Block>?>(null)
    var image by mutableStateOf<File?>(null)
    var guide by mutableStateOf(GuideNavigation())
    var guideMoving by mutableStateOf(false)
    var noteFor by mutableStateOf<NoteTarget?>(null)
    var activeHighlight by mutableStateOf<Long?>(null)
    var focusPage by mutableStateOf<Page?>(null)
}

@Composable
internal fun rememberReaderControls(): ReaderControls {
    // Open books with controls showing; keep saveable state at the screen's lifetime.
    val chrome = rememberSaveable { mutableStateOf(true) }
    val rotation = rememberSaveable { mutableStateOf(false) }
    val brightness = rememberSaveable { mutableFloatStateOf(-1f) }
    return remember { ReaderControls(chrome, rotation, brightness) }
}
