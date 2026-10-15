package app.folio.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Same 24-unit grid and rounded 1.7-unit outlines as [PageTurnIcons]. */
object ReaderSpacingIcons {
    private const val PAGE = "M5,3H19Q21,3 21,5V19Q21,21 19,21H5Q3,21 3,19V5Q3,3 5,3Z"

    val Dense = icon("DenseLines", "M4,4H20M4,7H20M4,10H20M4,13H20M4,16H20M4,19H20")
    val Normal = icon("NormalLines", "M4,4H20M4,8H20M4,12H20M4,16H20M4,20H20")
    val Loose = icon("LooseLines", "M4,4H20M4,9.3H20M4,14.7H20M4,20H20")
    val CustomLines = icon("CustomLines", "M4,6H20M4,12H20M4,18H20", "M9,3V9M16,9V15M11,15V21")

    val Narrow = icon("NarrowMargins", PAGE, "M6,7H18M6,10H18M6,13H18M6,16H18M6,19H18")
    val Medium = icon("MediumMargins", PAGE, "M8,7H16M8,10H16M8,13H16M8,16H16M8,19H16")
    val Wide = icon("WideMargins", PAGE, "M10,7H14M10,10H14M10,13H14M10,16H14M10,19H14")
    val CustomMargins = icon("CustomMargins", PAGE, "M6,7H18M6,12H18M6,17H18", "M10,5V9M15,10V14M9,15V19")

    private fun icon(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { path ->
                addPath(pathData = addPathNodes(path), stroke = SolidColor(Color.Black), strokeLineWidth = 1.7f,
                    strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
            }
        }.build()
}
