package net.flibustareader

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.flibustareader.core.*
import java.util.Locale
import kotlin.math.roundToInt

data class ReaderPalette(val paper: Color, val ink: Color, val accent: Color, val dark: Boolean) {
    companion object {
        fun forColor(color: ReaderColor): ReaderPalette = when (color) {
            ReaderColor.WHITE -> ReaderPalette(Color(0xFFFAFAF7), Color(0xFF282522), Color(0xFF625044), false)
            ReaderColor.SEPIA -> ReaderPalette(Color(0xFFFBF3DF), Color(0xFF50361D), Color(0xFF694723), false)
            ReaderColor.GRAY -> ReaderPalette(Color(0xFF2B2C2B), Color(0xFFE1DDD3), Color(0xFFD8C5A4), true)
            ReaderColor.BLACK -> ReaderPalette(Color(0xFF080909), Color(0xFFCCC9BF), Color(0xFFD8C5A4), true)
        }
    }
}

@Composable fun ReadingTheme(color: ReaderColor, content: @Composable () -> Unit) {
    val palette = ReaderPalette.forColor(color)
    val scheme = if (palette.dark) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = scheme.copy(
        primary = palette.accent, onPrimary = palette.paper,
        primaryContainer = palette.ink.copy(alpha = .09f), onPrimaryContainer = palette.ink,
        secondaryContainer = palette.ink.copy(alpha = .09f), onSecondaryContainer = palette.ink,
        background = palette.paper, surface = palette.paper, onSurface = palette.ink,
        onBackground = palette.ink, onSurfaceVariant = palette.ink.copy(alpha = .6f),
        surfaceVariant = palette.ink.copy(alpha = .07f), outline = palette.ink.copy(alpha = .25f)
    ), content = content)
}

enum class ReaderGlyph { CLOSE, MORE, TOC, BOOKMARK, PREVIOUS, NEXT, MINUS, PLUS }

@Composable private fun Glyph(kind: ReaderGlyph, color: Color, filled: Boolean = false) {
    Canvas(Modifier.size(24.dp)) {
        val unit = size.width / 24
        val stroke = 1.8f * unit
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(color, Offset(x1 * unit, y1 * unit), Offset(x2 * unit, y2 * unit), stroke, StrokeCap.Round)
        when (kind) {
            ReaderGlyph.CLOSE -> { line(5f, 9f, 12f, 16f); line(12f, 16f, 19f, 9f) }
            ReaderGlyph.MORE -> listOf(5f, 12f, 19f).forEach { drawCircle(color, 1.6f * unit, Offset(12 * unit, it * unit)) }
            ReaderGlyph.TOC -> listOf(6f, 12f, 18f).forEach { line(8f, it, 21f, it); drawCircle(color, unit, Offset(3 * unit, it * unit)) }
            ReaderGlyph.BOOKMARK -> {
                val path = Path().apply { moveTo(6 * unit, 3 * unit); lineTo(18 * unit, 3 * unit); lineTo(18 * unit, 21 * unit); lineTo(12 * unit, 16 * unit); lineTo(6 * unit, 21 * unit); close() }
                if (filled) drawPath(path, color) else drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            ReaderGlyph.PREVIOUS -> { line(15f, 5f, 8f, 12f); line(8f, 12f, 15f, 19f) }
            ReaderGlyph.NEXT -> { line(9f, 5f, 16f, 12f); line(16f, 12f, 9f, 19f) }
            ReaderGlyph.MINUS -> line(5f, 12f, 19f, 12f)
            ReaderGlyph.PLUS -> { line(5f, 12f, 19f, 12f); line(12f, 5f, 12f, 19f) }
        }
    }
}

@Composable private fun ReaderIconButton(label: String, glyph: ReaderGlyph, onClick: () -> Unit, round: Boolean = false, enabled: Boolean = true, filled: Boolean = false) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp).clip(CircleShape).then(if (round) Modifier.background(MaterialTheme.colorScheme.onSurface.copy(alpha = .08f), CircleShape) else Modifier).semantics { contentDescription = label }) {
        Glyph(glyph, MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else .35f), filled)
    }
}

@Composable fun ReaderHeader(title: String, author: String, safeTop: Int, onClose: () -> Unit, onContents: () -> Unit, onBookmark: () -> Unit, onRestoreBookmark: () -> Unit, onShowIndicators: () -> Unit, onSettings: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Surface(shadowElevation = 1.dp) {
        Row(Modifier.fillMaxWidth().padding(top = safeTop.dp, start = 16.dp, end = 16.dp, bottom = 16.dp).padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            ReaderIconButton("Вернуться в библиотеку", ReaderGlyph.CLOSE, onClose, round = true)
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (author.isNotBlank()) Text(author, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box {
                ReaderIconButton("Меню чтения", ReaderGlyph.MORE, { menu = true }, round = true)
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    listOf("Оглавление" to onContents, "Закладка" to onBookmark, "Перейти к закладке" to onRestoreBookmark, "Показать все индикаторы" to onShowIndicators, "Настройки чтения" to onSettings).forEach { (name, action) ->
                        DropdownMenuItem(text = { Text(name) }, onClick = { menu = false; action() })
                    }
                }
            }
        }
    }
}

@Composable fun ReaderBottomPanel(chapter: String, progress: Float, safeBottom: Int, bookmark: Boolean, ready: Boolean, onSeek: (Float) -> Unit, onContents: () -> Unit, onBookmark: () -> Unit, onSettings: () -> Unit) {
    var dragging by remember { mutableStateOf(false) }
    var pending by remember { mutableFloatStateOf(progress) }
    Surface(shadowElevation = 1.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 14.dp, bottom = (safeBottom + 10).dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ReaderIconButton("Оглавление", ReaderGlyph.TOC, onContents, enabled = ready)
                Text(chapter, Modifier.weight(1f).padding(start = 4.dp), fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Slider(value = if (dragging) pending else progress, onValueChange = { dragging = true; pending = it }, onValueChangeFinished = { onSeek(pending); dragging = false }, enabled = ready, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Позиция в книге" })
            Text("${((if (dragging) pending else progress) * 100).toInt()}% от всей книги", Modifier.align(Alignment.CenterHorizontally), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                ReaderIconButton(if (bookmark) "Удалить закладку" else "Добавить закладку", ReaderGlyph.BOOKMARK, onBookmark, filled = bookmark, enabled = ready)
                TextButton(onClick = onSettings, enabled = ready, modifier = Modifier.semantics { contentDescription = "Настройки текста" }) { Text("Aa", fontSize = 28.sp, fontFamily = FontFamily.Serif) }
            }
        }
    }
}

@Composable fun ReaderIndicators(options: ReaderOptions, clock: String, progress: String, battery: String, safeBottom: Int, onToggle: (ReaderIndicator) -> Unit) {
    Box {
        Box(Modifier.fillMaxWidth().height((ReaderViewport.HUD_HEIGHT_DP + safeBottom).dp).align(Alignment.BottomCenter).background(MaterialTheme.colorScheme.surface))
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = safeBottom.dp)) {
            data class Item(val id: ReaderIndicator, val name: String, val value: String, val shown: Boolean)
            listOf(Item(ReaderIndicator.CLOCK, "время", clock, options.clockVisible), Item(ReaderIndicator.PROGRESS, "прогресс книги", progress, options.progressVisible), Item(ReaderIndicator.BATTERY, "заряд телефона", battery, options.batteryVisible)).forEach { item ->
                Box(Modifier.weight(1f).height(ReaderViewport.HUD_TOUCH_HEIGHT_DP.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onToggle(item.id) }.padding(bottom = 4.dp).semantics {
                    contentDescription = if (item.shown) "Скрыть ${item.name}" else "Показать ${item.name}"
                    stateDescription = if (item.shown) item.value else "Скрыто"
                }, contentAlignment = Alignment.BottomCenter) {
                    if (item.shown) Text(item.value, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ReaderSettingsSheet(options: ReaderOptions, onChange: (ReaderOptions) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.fillMaxWidth().heightIn(max = 620.dp).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Настройки чтения", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onChange(options.copy(systemBrightness = !options.systemBrightness)) }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = options.systemBrightness, onCheckedChange = { onChange(options.copy(systemBrightness = it)) })
                Text("Яркость как в системе", fontSize = 15.sp)
            }
            if (!options.systemBrightness) Slider(value = options.brightness.toFloat(), onValueChange = { onChange(options.copy(brightness = it.toDouble())) }, valueRange = .05f..1f, modifier = Modifier.semantics { contentDescription = "Яркость чтения" })
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(ReaderColor.WHITE to "Белый", ReaderColor.SEPIA to "Сепия", ReaderColor.GRAY to "Серый", ReaderColor.BLACK to "Чёрный").forEach { (color, label) ->
                    val palette = ReaderPalette.forColor(color)
                    Surface(onClick = { onChange(options.copy(color = color)) }, shape = RoundedCornerShape(28.dp), color = palette.paper, contentColor = palette.ink, border = BorderStroke(if (options.color == color) 2.dp else 1.dp, if (options.color == color) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline), modifier = Modifier.semantics { selected = options.color == color }) {
                        Text(label, Modifier.padding(horizontal = 18.dp, vertical = 12.dp), fontSize = 15.sp)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(ReaderFont.BOOK to "Книжный", ReaderFont.SANS to "Без засечек", ReaderFont.MONO to "Моно").forEach { (font, label) ->
                    FilterChip(selected = options.font == font, onClick = { onChange(options.copy(font = font)) }, label = { Text(label, fontSize = 12.sp) })
                }
            }
            SettingStepper("Размер текста", "${(options.fontSize * 100).roundToInt()}%", { onChange(options.copy(fontSize = options.fontSize - .1)) }, { onChange(options.copy(fontSize = options.fontSize + .1)) })
            SettingStepper("Межстрочный интервал", String.format(Locale.getDefault(), "%.1f", options.lineHeight), { onChange(options.copy(lineHeight = options.lineHeight - .1)) }, { onChange(options.copy(lineHeight = options.lineHeight + .1)) })
            SettingStepper("Поля", String.format(Locale.getDefault(), "%.1f", options.margins), { onChange(options.copy(margins = options.margins - .2)) }, { onChange(options.copy(margins = options.margins + .2)) })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Прокрутка текста", fontSize = 16.sp)
                Switch(checked = options.scroll, onCheckedChange = { onChange(options.copy(scroll = it)) }, modifier = Modifier.semantics { contentDescription = "Прокрутка текста" })
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Готово") }
        }
    }
}

@Composable private fun SettingStepper(label: String, value: String, onLess: () -> Unit, onMore: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(label, fontSize = 15.sp); Text(value, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Surface(shape = RoundedCornerShape(28.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
            Row { ReaderIconButton("Уменьшить: $label", ReaderGlyph.MINUS, onLess); ReaderIconButton("Увеличить: $label", ReaderGlyph.PLUS, onMore) }
        }
    }
}
