package app.folio.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Outline icons of the page-turn modes, drawn in one thin stroke so they read as a set. */
object PageTurnIcons {
    /** «Скольжение»: a page with motion lines behind it. */
    val Slide: ImageVector by lazy {
        icon("Slide", "M11,3H17Q19,3 19,5V19Q19,21 17,21H11Q9,21 9,19V5Q9,3 11,3Z", "M3,8H6M2,12H6M3,16H6")
    }

    /** «Загиб»: a page with its lower corner turned over. */
    val Curl: ImageVector by lazy {
        icon("Curl", "M7,3H17Q19,3 19,5V14L13,21H7Q5,21 5,19V5Q5,3 7,3Z", "M19,14H15Q13,14 13,16V21")
    }

    /** «Без анимации»: a page with a lightning bolt, replaced at once. */
    val Instant: ImageVector by lazy {
        icon("Instant", "M7,3H17Q19,3 19,5V19Q19,21 17,21H7Q5,21 5,19V5Q5,3 7,3Z", "M13,7L9.5,12.5H14.5L11,17")
    }

    /** «Прокрутка»: a page of lines with a vertical arrow. */
    val Scroll: ImageVector by lazy {
        icon("Scroll", "M7,3H13Q15,3 15,5V19Q15,21 13,21H7Q5,21 5,19V5Q5,3 7,3Z", "M8,8H12M8,12H12M8,16H11M19,5V19M17,7L19,5L21,7M17,17L19,19L21,17")
    }

    private fun icon(name: String, vararg paths: String, faint: String? = null): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            fun stroke(d: String, alpha: Float) = addPath(
                pathData = addPathNodes(d), stroke = SolidColor(Color.Black), strokeAlpha = alpha, strokeLineWidth = 1.7f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            )
            paths.forEach { stroke(it, 1f) }
            faint?.let { stroke(it, 0.4f) }
        }.build()
}
