package app.folio.feature.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.UnfoldLess
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.folio.core.data.settings.*
import app.folio.core.designsystem.Folio
import app.folio.core.designsystem.FolioType
import app.folio.core.designsystem.ReaderPalette
import app.folio.core.designsystem.ReaderPalettes
import app.folio.core.designsystem.components.GroupedCard
import kotlin.math.roundToInt

/**
 * «Направляющая линия» on the Advanced tab: ready windows as cards, the exact window and dimming in
 * «Подробнее»; the panel button turns it on. A window outside the presets shows a selected «Своя» card. Paged modes only.
 */
@Composable
internal fun GuideSettingsCard(editor: SettingsEditor) {
    val settings = editor.settings
    val window = settings.guideWindow
    fun set(value: GuideWindow) = editor.edit { it.copy(guideWindow = value) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val colors = (settings.customColors ?: CustomColors.of(settings.theme)).copy(base = settings.theme)
    val page = ReaderPalettes.custom(colors, ReaderPalettes.isDark(settings.appearance, androidInDarkTheme()))
    Column {
        SettingsGroupHeader(stringResource(R.string.reader_guide_line), own = editor.isOwn(BookOverrides.GUIDE_KEYS))
        GroupedCard {
            if (settings.pageTurn == PageTurn.Scroll) {
                Text(stringResource(R.string.guide_scroll_hint), style = FolioType.footnote, color = Folio.colors.secondaryLabel,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp))
                return@GroupedCard
            }
            val scroll = rememberScrollState()
            val custom = window !in GuideWindow.PRESETS
            LaunchedEffect(custom) { if (custom) scroll.animateScrollTo(scroll.maxValue) }
            Row(Modifier.horizontalScroll(scroll).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GUIDE_CARDS.forEach { (preset, name, detail) ->
                    GuideTile(preset, page, stringResource(name), stringResource(detail), window == preset) {
                        // «Луч» is the one card that also sets dimming: its fading needs a dark page to read.
                        if (preset == GuideWindow.BEAM) editor.edit { it.copy(guideWindow = preset, guideDim = GuideDim.High) } else set(preset)
                    }
                }
                if (custom) GuideTile(window, page, stringResource(R.string.guide_custom), null, true) {}
            }
            SettingsDivider()
            SettingsDetailsRow(expanded) { expanded = !expanded }
            SettingsDetails(expanded) {
                SegmentsTitle(stringResource(R.string.guide_spread))
                SettingsSegments(window.spread, listOf(
                    SegmentOption(GuideSpread.Around, stringResource(R.string.guide_spread_around)),
                    SegmentOption(GuideSpread.Down, stringResource(R.string.guide_spread_down)),
                    SegmentOption(GuideSpread.Up, stringResource(R.string.guide_spread_up)),
                )) { v -> set(window.copy(spread = v)) }
                SettingsSlider(stringResource(R.string.guide_extra), window.extra.toFloat(),
                    GuideWindow.EXTRA.first.toFloat()..GuideWindow.EXTRA.last.toFloat(), 1f, "${window.extra}",
                    Icons.Rounded.UnfoldLess, Icons.Rounded.UnfoldMore) { v -> set(window.copy(extra = v.roundToInt())) }
                SegmentsTitle(stringResource(R.string.guide_falloff))
                SettingsSegments(window.falloff, listOf(
                    SegmentOption(GuideFalloff.None, stringResource(R.string.guide_falloff_none)),
                    SegmentOption(GuideFalloff.Soft, stringResource(R.string.guide_falloff_soft)),
                    SegmentOption(GuideFalloff.Strong, stringResource(R.string.guide_falloff_strong)),
                )) { v -> set(window.copy(falloff = v)) }
                SettingsDivider()
                SegmentsTitle(stringResource(R.string.guide_dim))
                SettingsSegments(settings.guideDim, guideDimOptions()) { v -> editor.edit { it.copy(guideDim = v) } }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

private val GUIDE_CARDS = listOf(
    Triple(GuideWindow.LINE, R.string.guide_card_line, R.string.guide_card_line_detail),
    Triple(GuideWindow.BEAM, R.string.guide_card_beam, R.string.guide_card_beam_detail),
    Triple(GuideWindow.NEXT, R.string.guide_card_next, R.string.guide_card_next_detail),
    Triple(GuideWindow.PARAGRAPH, R.string.guide_card_paragraph, R.string.guide_card_paragraph_detail),
    Triple(GuideWindow.BAND, R.string.guide_card_band, R.string.guide_card_band_detail),
)

@Composable
internal fun guideDimOptions() = listOf(
    SegmentOption(GuideDim.None, stringResource(R.string.guide_dim_none)),
    SegmentOption(GuideDim.Low, stringResource(R.string.guide_dim_low)),
    SegmentOption(GuideDim.Medium, stringResource(R.string.guide_dim_medium)),
    SegmentOption(GuideDim.High, stringResource(R.string.guide_dim_high)),
)

/** Five text bars on the page colour, lit as [window] lights them; the framed bars are the plate. */
@Composable
private fun GuideTile(window: GuideWindow, page: ReaderPalette, name: String, detail: String?, selected: Boolean, onClick: () -> Unit) {
    ChoiceTile(name, selected, page.background, Modifier.width(116.dp), detail, onClick) {
        val rows = 5
        val anchor = if (window.spread == GuideSpread.Up) 3 else if (window.spread == GuideSpread.Around) 2 else 1
        val full = window.falloff == GuideFalloff.None
        Canvas(Modifier.size(width = 76.dp, height = 46.dp)) {
            val bar = 3.dp.toPx()
            val step = size.height / rows
            for (i in 0 until rows) {
                val light = window.light((i - anchor).toFloat())
                val y = step * i + (step - bar) / 2
                val width = if (i == rows - 1) size.width * 0.6f else size.width
                drawRoundRect(page.text.copy(alpha = 1f - 0.8f * (1f - light)), Offset(0f, y), Size(width, bar), CornerRadius(bar / 2))
            }
            val first = (anchor - if (full) window.above else 0).coerceAtLeast(0)
            val last = (anchor + if (full) window.below else 0).coerceAtMost(rows - 1)
            val pad = 3.dp.toPx()
            drawRoundRect(page.text.copy(alpha = 0.35f), Offset(-pad, step * first + 1.dp.toPx()),
                Size(size.width + 2 * pad, step * (last - first + 1) - 2.dp.toPx()), CornerRadius(4.dp.toPx()), style = Stroke(1.dp.toPx()))
        }
    }
}
