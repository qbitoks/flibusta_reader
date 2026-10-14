package app.folio.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.folio.core.designsystem.Folio
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * Backdrop for "Liquid Glass" surfaces. A screen provides it with [GlassScope] and marks
 * its scrolling content with [glassSource]; every [glass] surface drawn above then blurs
 * that content. Without a provider (e.g. inside popups, which live in another window)
 * surfaces fall back to a translucent fill.
 */
val LocalGlassState = staticCompositionLocalOf<HazeState?> { null }

@Composable
fun GlassScope(content: @Composable (source: Modifier) -> Unit) {
    val state = rememberHazeState()
    CompositionLocalProvider(LocalGlassState provides state) {
        content(Modifier.hazeSource(state))
    }
}

/** Glass background clipped to [shape]; [tint] overrides the theme glass colour. */
@Composable
fun Modifier.glass(shape: Shape, tint: Color? = null, elevation: Dp = 0.dp): Modifier {
    val c = Folio.colors
    val state = LocalGlassState.current
    val hairline = if (c.isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.06f)
    val shadowed = if (elevation > 0.dp) {
        shadow(elevation, shape, ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.18f))
    } else {
        this
    }
    val clipped = shadowed.clip(shape)
    val filled = if (state != null) {
        clipped.hazeBlur(
            input = HazeInput.Backdrop(state),
            style = HazeBlurStyle {
                blurRadius(22.dp)
                noiseFactor(0f)
                backgroundColor(c.background)
                colorEffects(listOf(HazeColorEffect.tint(tint ?: c.glass)))
                fallbackColorEffect(HazeColorEffect.tint(tint?.copy(alpha = 0.95f) ?: c.glassFallback))
            },
        )
    } else {
        clipped.background(tint?.copy(alpha = 0.95f) ?: c.glassFallback)
    }
    return filled.border(0.5.dp, hairline, shape)
}

@Composable
fun GlassSurface(
    shape: Shape,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    elevation: Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.glass(shape, tint, elevation), content = content)
}
