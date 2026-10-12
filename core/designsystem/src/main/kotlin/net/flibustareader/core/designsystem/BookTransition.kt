package net.flibustareader.core.designsystem

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Opening a book out of its cover and closing it back into it (white spot 16). The tapped
 * cover's key (where it sat plus the book id, since one book can show twice on «Главная»)
 * is remembered; that cover and the reader share bounds while the navigation animates.
 */
object BookTransition {
    /** Key of the cover the open book came from, or null when it was opened from elsewhere. */
    var key by mutableStateOf<String?>(null)

    fun open(key: String, bookId: String, onOpen: (String) -> Unit) {
        this.key = key
        onOpen(bookId)
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** Animated scope of the navigation destination a composable belongs to. */
val LocalNavAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Shares bounds with the other end of the book transition when [key] is the active one. The
 * content is scaled, not re-measured, so the reader never re-paginates mid-animation.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.bookTransition(key: String?): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val scope = LocalNavAnimatedScope.current ?: return this
    if (key == null || key != BookTransition.key) return this
    return with(shared) {
        this@bookTransition.sharedBounds(
            rememberSharedContentState(key),
            animatedVisibilityScope = scope,
            enter = fadeIn(tween(220, delayMillis = 60)),
            exit = fadeOut(tween(220)),
            clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(4.dp)),
        )
    }
}
