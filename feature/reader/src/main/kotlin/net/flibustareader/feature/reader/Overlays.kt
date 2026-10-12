package net.flibustareader.feature.reader

import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import net.flibustareader.core.designsystem.ReaderPalette
import net.flibustareader.core.designsystem.UiSans
import net.flibustareader.core.model.Block
import net.flibustareader.core.model.Span
import coil3.compose.AsyncImage
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FootnoteSheet(blocks: List<Block>, palette: ReaderPalette, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.chrome,
        contentColor = palette.text,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
        ) {
            Text(stringResource(R.string.footnote), color = palette.secondary, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
            blocks.forEach { block ->
                if (block is Block.Text) {
                    Text(block.toAnnotated(), fontSize = 17.sp, lineHeight = 24.sp, modifier = Modifier.padding(bottom = 8.dp))
                }
            }
        }
    }
}

private fun Block.Text.toAnnotated(): AnnotatedString = buildAnnotatedString {
    append(text)
    for (s in spans) {
        val style = SpanStyle(
            fontWeight = if (s.has(Span.BOLD)) FontWeight.Bold else null,
            fontStyle = if (s.has(Span.ITALIC)) FontStyle.Italic else null,
            baselineShift = when {
                s.has(Span.SUPERSCRIPT) -> BaselineShift.Superscript
                s.has(Span.SUBSCRIPT) -> BaselineShift.Subscript
                else -> null
            },
            fontSize = if (s.has(Span.SUPERSCRIPT) || s.has(Span.SUBSCRIPT)) 12.sp else androidx.compose.ui.unit.TextUnit.Unspecified,
        )
        addStyle(style, s.start.coerceIn(0, text.length), s.end.coerceIn(0, text.length))
    }
}

/**
 * Full-screen image (white spot 9): «Изображение» with ✓ on a white page; a tap switches the
 * background between white and black (the header hides on black), pinch and double-tap zoom.
 */
@Composable
internal fun ImageViewer(file: File, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // Edge to edge: the page covers the status bar and cutout area too, as in Apple Books.
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.let {
                it.setDimAmount(0f)
                it.attributes = it.attributes.apply { layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES }
            }
        }
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        var black by remember { mutableStateOf(false) }
        SideEffect {
            window?.let { WindowCompat.getInsetsController(it, it.decorView) }?.apply {
                isAppearanceLightStatusBars = !black
                isAppearanceLightNavigationBars = !black
            }
        }
        val transform = rememberTransformableState { _, zoom, pan, _ ->
            scale = (scale * zoom).coerceIn(1f, 5f)
            offset = if (scale == 1f) Offset.Zero else offset + pan
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(if (black) Color.Black else Color.White)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { black = !black },
                        onDoubleTap = {
                            if (scale > 1f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2.5f
                            }
                        },
                    )
                }
                .transformable(transform),
        ) {
            AsyncImage(
                model = file,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )
            if (!black) {
                Box(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        stringResource(R.string.image_title),
                        color = Color.Black,
                        fontFamily = UiSans,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    RoundButton(
                        Icons.Rounded.Check, stringResource(R.string.image_close), Color(0xFF1C1C1E), Color.White,
                        Modifier.align(Alignment.CenterEnd), onClick = onDismiss,
                    )
                }
            }
        }
    }
}
