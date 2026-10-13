package net.flibustareader.feature.reader

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import net.flibustareader.core.data.settings.FooterPage
import net.flibustareader.core.data.settings.PageFooter
import net.flibustareader.core.data.settings.PageHeader
import net.flibustareader.core.designsystem.ReaderPalette
import net.flibustareader.core.designsystem.UiSans
import net.flibustareader.core.designsystem.components.ReaderBatteryLabel
import java.util.Date

/** Text of the line above the page; null when it shows nothing. A chapter without a title falls back to the book. */
internal fun headerText(header: PageHeader, book: String, chapter: String?): String? = when (header) {
    PageHeader.None -> null
    PageHeader.Book -> book
    PageHeader.Chapter -> chapter?.takeIf { it.isNotBlank() } ?: book
}

/** Words of the bottom line, passed in so the line can be built without resources. */
internal class FooterWords(
    val pageOf: (Int, Int) -> String,
    val percent: (Int) -> String,
    val pagesLeft: (Int) -> String,
    val chapterEnd: String,
)

/**
 * Middle of the bottom line: the chosen page format, then «ещё N стр.». Until the book is counted ([number] or [total] null) the percent
 * stands in for numbers that would otherwise jump.
 */
internal fun footerCenter(footer: PageFooter, number: Int?, total: Int?, progress: Float, left: Int?, words: FooterWords, separator: String = " · "): String {
    val percent = words.percent((progress * 100).toInt().coerceIn(0, 100))
    val page = when (footer.page) {
        FooterPage.None -> null
        FooterPage.Percent -> percent
        FooterPage.Number -> when {
            number == null -> percent
            else -> number.toString()
        }
        FooterPage.NumberOfTotal -> if (number != null && total != null) words.pageOf(number, total) else percent
    }
    val rest = if (footer.pagesLeft && left != null) (if (left > 0) words.pagesLeft(left) else words.chapterEnd) else null
    return listOfNotNull(page, rest).joinToString(separator)
}

internal class BatteryInfo(val level: Int, val charging: Boolean)

/** Current time in the system's 12/24-hour format, updated every minute while [enabled]. */
@Composable
internal fun rememberClock(enabled: Boolean): State<String?> {
    val context = LocalContext.current
    val state = remember { mutableStateOf<String?>(null) }
    DisposableEffect(enabled) {
        if (!enabled) {
            state.value = null
            return@DisposableEffect onDispose { }
        }
        fun now() = DateFormat.getTimeFormat(context).format(Date())
        state.value = now()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                state.value = now()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    return state
}

/** Battery level and charging state while [enabled]; the system sends the current value at once. */
@Composable
internal fun rememberBattery(enabled: Boolean): State<BatteryInfo?> {
    val context = LocalContext.current
    val state = remember { mutableStateOf<BatteryInfo?>(null) }
    DisposableEffect(enabled) {
        if (!enabled) {
            state.value = null
            return@DisposableEffect onDispose { }
        }
        fun read(intent: Intent?) {
            intent ?: return
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            if (level < 0) return
            state.value = BatteryInfo(
                level = level * 100 / scale,
                charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
            )
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) = read(intent)
        }
        read(ContextCompat.registerReceiver(context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED))
        onDispose { context.unregisterReceiver(receiver) }
    }
    return state
}

/**
 * The bottom line: time on the left, [center] in the middle, battery on the right, in the grey of
 * the page labels. Empty sides keep their room so the middle stays centred. Without [spread] all
 * parts sit together in the middle; a [rule] colour draws a hairline above the line.
 */
@Composable
internal fun PageFooterBar(
    center: String,
    clock: String?,
    battery: BatteryInfo?,
    palette: ReaderPalette,
    modifier: Modifier = Modifier,
    startInset: Dp = 0.dp,
    endInset: Dp = 0.dp,
    textStyle: TextStyle = TextStyle(color = palette.secondary, fontFamily = UiSans, fontSize = 15.sp),
    spread: Boolean = true,
    rule: Color? = null,
    onCenterTap: (() -> Unit)? = null,
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val clockWidth = clock?.let { with(density) { measurer.measure(it, textStyle, maxLines = 1).size.width.toDp() } } ?: 0.dp
    val batteryWidth = battery?.let {
        with(density) { measurer.measure("${it.level}%", textStyle, maxLines = 1).size.width.toDp() + textStyle.fontSize.toDp() * 1.6f } + 4.dp
    } ?: 0.dp
    val centerTap = if (onCenterTap != null && center.isNotEmpty()) Modifier.clickable(onClick = onCenterTap) else Modifier
    Column(modifier.fillMaxWidth()) {
    if (rule != null) {
        LineRule(rule)
        Spacer(Modifier.height(FooterRuleSpace - 0.5.dp))
    }
    if (!spread) {
        Row(Modifier.fillMaxWidth().padding(start = startInset, end = endInset), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally)) {
            if (clock != null) FooterText(clock, textStyle)
            if (center.isNotEmpty()) FooterText(center, textStyle, Modifier.weight(1f, fill = false).then(centerTap))
            if (battery != null) ReaderBatteryLabel(battery.level, battery.charging, palette, textStyle)
        }
        return@Column
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val side = maxOf(clockWidth + startInset, batteryWidth + endInset).coerceAtMost(maxWidth * 0.45f)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(side).padding(start = startInset), contentAlignment = Alignment.CenterStart) {
                if (clock != null) FooterText(clock, textStyle)
            }
            Box(Modifier.weight(1f).then(centerTap), contentAlignment = Alignment.Center) {
                FooterText(center, textStyle, Modifier.fillMaxWidth(), TextAlign.Center)
            }
            Box(Modifier.width(side).padding(end = endInset), contentAlignment = Alignment.CenterEnd) {
                if (battery != null) ReaderBatteryLabel(battery.level, battery.charging, palette, textStyle)
            }
        }
    }
    }
}

@Composable
private fun FooterText(text: String, textStyle: TextStyle, modifier: Modifier = Modifier, align: TextAlign = TextAlign.Start) {
    Text(text, style = textStyle, modifier = modifier, textAlign = align, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** Height of the faint «Полоска» under scrolling text, above the navigation inset. */
internal val FooterStripHeight = 30.dp

/**
 * Strip at the very bottom that the scrolling text passes under: a shade off the page colour, so it
 * is barely visible yet keeps the lines under it from showing through the time and page number.
 */
@Composable
internal fun FooterStrip(palette: ReaderPalette, height: Dp, modifier: Modifier = Modifier) {
    val tint = lerp(palette.background, palette.text, 0.05f).copy(alpha = 0.97f)
    Box(modifier.fillMaxWidth().height(height).background(tint)) {
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(palette.text.copy(alpha = 0.1f)))
    }
}

/**
 * Page colour over [height] at an edge of the scrolling text, solid at the screen edge and fading
 * over the last [fade] towards the text, so lines dissolve under the header and footer.
 */
@Composable
internal fun EdgeFade(palette: ReaderPalette, height: Dp, top: Boolean, modifier: Modifier = Modifier, fade: Dp = 28.dp) {
    if (height <= 0.dp) return
    val solid = ((height - fade) / height).coerceIn(0f, 1f)
    val bg = palette.background
    val clear = bg.copy(alpha = 0f)
    val stops = if (top) arrayOf(0f to bg, solid to bg, 1f to clear) else arrayOf(0f to clear, (1f - solid) to bg, 1f to bg)
    Box(modifier.fillMaxWidth().height(height).background(Brush.verticalGradient(colorStops = stops)))
}
