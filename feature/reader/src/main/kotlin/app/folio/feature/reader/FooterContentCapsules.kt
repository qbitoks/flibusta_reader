package app.folio.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.folio.core.data.settings.*
import app.folio.core.designsystem.*

/** Independent controls share the inset track and raised selection used by settings segments. */
@Composable
internal fun FooterContentCapsules(editor: SettingsEditor, source: PreviewSource) {
    val footer = editor.settings.shownFooter
    val clock by rememberClock(true)
    val battery by rememberBattery(true)
    val labels = listOf(R.string.footer_progress_short, R.string.footer_chapter_short,
        R.string.footer_clock, R.string.footer_battery_short).map { stringResource(it) }
    val descriptions = listOf(R.string.footer_progress_short, R.string.footer_pages_left,
        R.string.footer_clock, R.string.footer_battery).map { stringResource(it) }
    val values = listOf(
        when (footer.page) {
            FooterPage.NumberOfTotal -> "42/300"
            FooterPage.Percent -> stringResource(R.string.reader_percent, 7)
            FooterPage.Number -> "42"
            FooterPage.None -> stringResource(R.string.header_none)
        },
        source.pagesLeft?.let { stringResource(R.string.footer_chapter_value, it) } ?: "—",
        clock ?: "—",
        battery?.let { stringResource(R.string.reader_percent, it.level) } ?: "—",
    )
    val formatDescription = stringResource(when (footer.page) {
        FooterPage.None -> R.string.footer_page_none
        FooterPage.Number -> R.string.settings_page_number
        FooterPage.NumberOfTotal -> R.string.settings_page_total
        FooterPage.Percent -> R.string.footer_page_percent
    })
    val cycleLabel = stringResource(R.string.footer_next_format)
    val active = listOf(footer.page != FooterPage.None, footer.pagesLeft, footer.clock, footer.battery)
    val c = Folio.colors
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        val columns = footerContentColumns(maxWidth.value - 6, fontScale)
        Column(Modifier.clip(RoundedCornerShape(16.dp)).background(c.fill).padding(3.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            (0 until 4 step columns).forEach { start ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (start until start + columns).forEach { i ->
                        val action = if (i == 0) Modifier.clickable(role = Role.Button, onClickLabel = cycleLabel) {
                            editor.edit { it.copy(footer = it.footer.copy(page = nextFooterPage(it.footer.page))) }
                        } else Modifier.toggleable(active[i], role = Role.Checkbox) { on ->
                            editor.edit { it.editFooterContent { f ->
                                when (i) {
                                    1 -> f.copy(pagesLeft = on)
                                    2 -> f.copy(clock = on)
                                    else -> f.copy(battery = on)
                                }
                            } }
                        }
                        val shape = RoundedCornerShape(13.dp)
                        Column(Modifier.weight(1f)
                            .then(if (active[i]) Modifier.shadow(2.dp, shape) else Modifier)
                            .clip(shape)
                            .background(if (active[i]) (if (c.isDark) Color(0xFF636366) else Color.White) else Color.Transparent)
                            .then(action)
                            .semantics(mergeDescendants = true) {
                                contentDescription = descriptions[i]
                                if (i == 0) stateDescription = formatDescription
                            }
                            .heightIn(min = 58.dp).padding(horizontal = 4.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) {
                            BasicText(labels[i], style = FolioType.caption.copy(color = c.secondaryLabel,
                                textAlign = TextAlign.Center), maxLines = 1, softWrap = false,
                                autoSize = TextAutoSize.StepBased(10.sp, FolioType.caption.fontSize, 0.5.sp))
                            BasicText(values[i], style = FolioType.subheadline.copy(
                                color = if (active[i]) c.label else c.secondaryLabel,
                                fontWeight = if (active[i]) FontWeight.SemiBold else FontWeight.Normal,
                                textAlign = TextAlign.Center), maxLines = 1, softWrap = false,
                                autoSize = TextAutoSize.StepBased(10.sp, FolioType.subheadline.fontSize, 0.5.sp))
                        }
                    }
                }
            }
        }
    }
}
