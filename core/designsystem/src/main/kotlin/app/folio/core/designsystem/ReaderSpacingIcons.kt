package app.folio.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Same 24-unit grid and rounded 1.7-unit outlines as [PageTurnIcons]. Whole-unit coordinates only, and
 * every glyph is centred on y = 12, so the three states differ by one property each.
 */
object ReaderSpacingIcons {
    private const val PAGE = "M6,3H18Q20,3 20,5V19Q20,21 18,21H6Q4,21 4,19V5Q4,3 6,3Z"
    private const val SLIDERS = "M4,8H7M11,8H20M4,16H13M17,16H20M11,8A2,2 0 1,1 7,8A2,2 0 1,1 11,8ZM17,16A2,2 0 1,1 13,16A2,2 0 1,1 17,16Z"

    /** Three text lines; only their step changes (4, 6, 8 units). */
    val Dense = icon("DenseLines", "M4,8H20M4,12H20M4,16H14")
    val Normal = icon("NormalLines", "M4,6H20M4,12H20M4,18H14")
    val Loose = icon("LooseLines", "M4,4H20M4,12H20M4,20H14")
    val CustomLines = icon("CustomLines", SLIDERS)

    /** A page with three centred lines; only the side margin changes (3, 5, 6 units inside the page). */
    val Narrow = icon("NarrowMargins", PAGE, "M7,8H17M7,12H17M7,16H17")
    val Medium = icon("MediumMargins", PAGE, "M9,8H15M9,12H15M9,16H15")
    val Wide = icon("WideMargins", PAGE, "M10,8H14M10,12H14M10,16H14")
    val CustomMargins = icon("CustomMargins", SLIDERS)

    private fun icon(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { path ->
                addPath(pathData = addPathNodes(path), stroke = SolidColor(Color.Black), strokeLineWidth = 1.7f,
                    strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
            }
        }.build()
}
