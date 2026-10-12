package net.flibustareader.feature.reader

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.flibustareader.core.designsystem.ReaderPalette
import net.flibustareader.core.designsystem.UiSans
import net.flibustareader.core.layout.TextSpan
import kotlin.math.pow

/**
 * Apple's highlight palette (#72): five colours and a red underline. [dot] is the palette
 * swatch; [base] is what multiplies with the page (measured: pink and blue on «Покой», #73/#78).
 */
internal enum class HighlightColor(val key: String, val dot: Color, val base: Color) {
    Yellow("yellow", Color(0xFFEFD038), Color(0xFFFFE85A)),
    Green("green", Color(0xFF9ADC82), Color(0xFFB4E8A0)),
    Blue("blue", Color(0xFF94B9FF), Color(0xFFAED4FC)),
    Pink("pink", Color(0xFFFF9BB5), Color(0xFFF7B2DD)),
    Purple("purple", Color(0xFFC5AFFA), Color(0xFFD6C4FC)),
    Underline("underline", Color(0xFFEC3B35), Color(0xFFEC3B35));

    companion object {
        fun of(key: String?): HighlightColor = entries.firstOrNull { it.key == key } ?: Yellow
    }
}

/** Apple's highlight yellow in lists (measured on #27). */
internal val HighlightYellow = Color(0xFFFFE441)

/**
 * The colour a highlight is painted with on [palette]'s page. Light pages multiply, so the text
 * stays black; dark pages get the swatch at 45 %. [active] (palette open, #72) is deeper.
 */
internal fun highlightPaint(color: HighlightColor, palette: ReaderPalette, active: Boolean = false): Color {
    if (color == HighlightColor.Underline) return color.base
    val bg = palette.background
    if (palette.isDark) return lerp(bg, color.dot, if (active) 0.6f else 0.45f)
    val k = if (active) 1.6f else 1f
    fun ch(page: Float, base: Float) = page * base.pow(k)
    return Color(ch(bg.red, color.base.red), ch(bg.green, color.base.green), ch(bg.blue, color.base.blue))
}

/** A note to edit: on an existing highlight ([highlightId]) or on a new selection to save with it. */
internal data class NoteTarget(
    val highlightId: Long?,
    val span: TextSpan,
    val note: String,
    val color: HighlightColor,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Colours over a tapped highlight (#72): five dots and «U», split by hairlines. */
@Composable
internal fun HighlightPalette(palette: ReaderPalette, onPick: (HighlightColor) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .height(44.dp)
            .shadow(10.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.18f))
            .background(palette.menuCapsule(), CircleShape)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HighlightColor.entries.forEachIndexed { i, color ->
            if (i > 0) Box(Modifier.width(0.5.dp).height(22.dp).background(palette.text.copy(alpha = 0.15f)))
            Box(
                Modifier.width(48.dp).fillMaxHeight().clickable { onPick(color) },
                contentAlignment = Alignment.Center,
            ) {
                if (color == HighlightColor.Underline) {
                    Text("U", color = palette.text, fontFamily = UiSans, fontSize = 17.sp, textDecoration = TextDecoration.Underline)
                } else {
                    Box(Modifier.size(16.dp).background(color.dot, CircleShape))
                }
            }
        }
    }
}

/**
 * Note marker at the start of a highlight with a note (#78): a disc in the highlight colour
 * ringed with the page colour, a small page-coloured square inside. Tapping opens the note.
 */
@Composable
internal fun NoteMarker(color: Color, palette: ReaderPalette, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .size(22.dp)
            .shadow(2.dp, CircleShape)
            .background(palette.background, CircleShape)
            .padding(2.dp)
            .background(color, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(8.dp).background(palette.background, RoundedCornerShape(2.dp)))
    }
}

/**
 * Note editor (#77): «Заметка» with the time, a dark ✓; the passage in the book's font beside a
 * bar in the highlight colour; the note below. Saves on ✓ or when dismissed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NoteSheet(
    target: NoteTarget,
    quote: String,
    bookFont: FontFamily,
    palette: ReaderPalette,
    onDone: (String) -> Unit,
) {
    var text by remember { mutableStateOf(target.note) }
    val focus = remember { FocusRequester() }
    val context = LocalContext.current
    val time = remember(target.createdAt) {
        val flags = if (DateUtils.isToday(target.createdAt)) DateUtils.FORMAT_SHOW_TIME else DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME
        DateUtils.formatDateTime(context, target.createdAt, flags)
    }
    val sheet = if (palette.isDark) palette.sheet() else Color.White
    ModalBottomSheet(
        onDismissRequest = { onDone(text) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = sheet,
        contentColor = palette.text,
        dragHandle = null,
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.93f).imePadding().padding(start = 28.dp, end = 16.dp, top = 16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.note_title), color = palette.text, fontFamily = UiSans, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                Text(" $time", color = palette.secondary, fontFamily = UiSans, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, modifier = Modifier.weight(1f))
                RoundButton(Icons.Rounded.Check, stringResource(R.string.note_done), Color(0xFF1C1C1E), Color.White, Modifier, size = 44.dp) { onDone(text) }
            }
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(top = 24.dp, bottom = 18.dp, end = 12.dp)) {
                Box(Modifier.width(3.dp).fillMaxHeight().background(target.color.dot, RoundedCornerShape(1.5.dp)))
                Text(
                    quote,
                    color = palette.text,
                    fontFamily = bookFont,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    modifier = Modifier.padding(start = 12.dp, top = 2.dp, bottom = 2.dp),
                )
            }
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = TextStyle(color = palette.text, fontSize = 17.sp, lineHeight = 23.sp, fontFamily = UiSans, fontWeight = FontWeight.SemiBold),
                cursorBrush = SolidColor(SelectionBlue),
                modifier = Modifier.fillMaxWidth().weight(1f).padding(end = 12.dp).focusRequester(focus),
                decorationBox = { field ->
                    Box {
                        if (text.isEmpty()) Text(stringResource(R.string.note_hint), color = palette.secondary, fontSize = 17.sp, fontFamily = UiSans, fontWeight = FontWeight.SemiBold)
                        field()
                    }
                },
            )
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}
