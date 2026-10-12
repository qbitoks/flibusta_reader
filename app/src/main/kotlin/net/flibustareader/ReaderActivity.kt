@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)
package net.flibustareader

import android.os.Bundle
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.readium.r2.navigator.epub.*
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.toUrl
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import java.io.File
import net.flibustareader.core.ReadingProgress

class ReaderActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("reader", MODE_PRIVATE) }
    private var navigator: EpubNavigatorFragment? = null
    private var publication: Publication? = null
    private var message by mutableStateOf("Открываем книгу…")
    private var ready by mutableStateOf(false)
    private var settings by mutableStateOf(false)
    private var scroll by mutableStateOf(false)
    private var fontSize by mutableDoubleStateOf(1.1)
    private var theme by mutableStateOf(Theme.LIGHT)
    private var progress by mutableStateOf("")
    private lateinit var bookId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        // Readium factory depends on a reopened publication; restore the persisted locator instead of Fragment state.
        super.onCreate(null)
        bookId = intent.getStringExtra("id") ?: run { finish(); return }
        scroll = prefs.getBoolean("scroll", false)
        fontSize = prefs.getFloat("fontSize", 1.1f).toDouble()
        theme = runCatching { Theme.valueOf(prefs.getString("theme", "LIGHT")!!) }.getOrDefault(Theme.LIGHT)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val controls = ComposeView(this).apply { setContent { ReaderTheme(dark = theme == Theme.DARK) { Controls() } } }
        val frame = FrameLayout(this).apply { id = R.id.reader_container }
        layout.addView(controls, LinearLayout.LayoutParams(-1, -2))
        layout.addView(frame, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(layout)
        updateSystemBars()
        lifecycleScope.launch {
            try {
                val path = intent.getStringExtra("path") ?: error("Не указан файл")
                require(File(path).isFile) { "Файл книги не найден" }
                val http = DefaultHttpClient()
                val retriever = AssetRetriever(contentResolver, http)
                val asset = retriever.retrieve(File(path).toUrl()).getOrElse { error("Не удалось прочитать EPUB: $it") }
                val opener = PublicationOpener(DefaultPublicationParser(this@ReaderActivity, http, retriever, null))
                val book = opener.open(asset, allowUserInteraction = false).getOrElse { error("Не удалось открыть книгу: $it") }
                publication = book
                val position = prefs.getString("position:$bookId", null)?.let { runCatching { Locator.fromJSON(JSONObject(it)) }.getOrNull() }
                supportFragmentManager.fragmentFactory = EpubNavigatorFactory(book).createFragmentFactory(
                    initialLocator = position,
                    initialPreferences = preferences(),
                    paginationListener = object : EpubNavigatorFragment.PaginationListener {
                        override fun onPageLoaded() { ready = true; message = "" }
                    }
                )
                val fragment = supportFragmentManager.fragmentFactory.instantiate(classLoader, EpubNavigatorFragment::class.java.name) as EpubNavigatorFragment
                supportFragmentManager.beginTransaction().replace(R.id.reader_container, fragment).commitNow()
                navigator = fragment
                prefs.edit().putString("lastBook", bookId).apply()
                fragment.currentLocator.collect { locator ->
                    prefs.edit().putString("position:$bookId", locator.toJSON().toString()).apply()
                    progress = ReadingProgress.percent(locator.locations.progression, locator.locations.totalProgression, book.readingOrder.size)?.let { "$it%" }.orEmpty()
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { message = e.message ?: "Ошибка открытия книги" }
        }
    }

    private fun preferences() = EpubPreferences(scroll = scroll, fontSize = fontSize, theme = theme, publisherStyles = false, fontFamily = FontFamily.SERIF, lineHeight = 1.5, pageMargins = 1.2)
    private fun applyPreferences() {
        prefs.edit().putBoolean("scroll", scroll).putFloat("fontSize", fontSize.toFloat()).putString("theme", theme.name).apply()
        navigator?.submitPreferences(preferences())
        updateSystemBars()
    }

    private fun updateSystemBars() {
        val dark = theme == Theme.DARK
        window.statusBarColor = if (dark) android.graphics.Color.BLACK else android.graphics.Color.rgb(247, 245, 240)
        window.navigationBarColor = window.statusBarColor
        window.insetsController?.setSystemBarsAppearance(
            if (dark) 0 else android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
            android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        )
    }

    @Composable private fun Controls() {
        Surface {
            Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                Row {
                    TextButton(onClick = { finish() }) { Text("← Книги") }
                    Spacer(Modifier.weight(1f))
                    TextButton(enabled = ready, onClick = { tableOfContents() }) { Text("Главы") }
                    TextButton(enabled = ready, onClick = { settings = !settings }) { Text("Aa") }
                    TextButton(enabled = ready, onClick = { bookmarks() }) { Text("Закладка") }
                }
                Text(intent.getStringExtra("title").orEmpty(), style = MaterialTheme.typography.labelMedium, maxLines = 1, modifier = Modifier.padding(horizontal = 8.dp))
                Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    TextButton(enabled = ready, onClick = { navigator?.goBackward(animated = true) }) { Text("←") }
                    Text(if (message.isNotBlank()) message else "${if (scroll) "Прокрутка" else "Страницы"} · $progress", style = MaterialTheme.typography.bodySmall, maxLines = 2, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    TextButton(enabled = ready, onClick = { navigator?.goForward(animated = true) }) { Text("→") }
                }
                if (settings) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = !scroll, onClick = { scroll = false; applyPreferences() }, label = { Text("Страницы") })
                        FilterChip(selected = scroll, onClick = { scroll = true; applyPreferences() }, label = { Text("Прокрутка") })
                        TextButton(onClick = { fontSize = (fontSize - .1).coerceAtLeast(.7); applyPreferences() }) { Text("A−") }
                        TextButton(onClick = { fontSize = (fontSize + .1).coerceAtMost(2.5); applyPreferences() }) { Text("A+") }
                    }
                    Row {
                        listOf(Theme.LIGHT to "Светлая", Theme.SEPIA to "Сепия", Theme.DARK to "Тёмная").forEach { (value, name) ->
                            TextButton(onClick = { theme = value; applyPreferences() }) { Text(name) }
                        }
                    }
                }
            }
        }
    }

    private fun tableOfContents() {
        val links = mutableListOf<Pair<Link, String>>()
        fun flatten(items: List<Link>, level: Int = 0) { items.forEach { links += it to ("  ".repeat(level) + (it.title ?: "Глава")); flatten(it.children, level + 1) } }
        flatten(publication?.tableOfContents.orEmpty())
        if (links.isEmpty()) { android.app.AlertDialog.Builder(this).setMessage("В книге нет оглавления").setPositiveButton("OK", null).show(); return }
        android.app.AlertDialog.Builder(this).setTitle("Оглавление").setItems(links.map { it.second }.toTypedArray()) { _, index -> navigator?.go(links[index].first) }.setNegativeButton("Закрыть", null).show()
    }

    private fun bookmarks() {
        val saved = prefs.getString("bookmark:$bookId", null)
        val options = if (saved == null) arrayOf("Сохранить текущую позицию") else arrayOf("Сохранить текущую позицию", "Перейти к закладке", "Удалить закладку")
        android.app.AlertDialog.Builder(this).setTitle("Закладка").setItems(options) { _, index ->
            when (index) {
                0 -> navigator?.currentLocator?.value?.let { prefs.edit().putString("bookmark:$bookId", it.toJSON().toString()).apply() }
                1 -> saved?.let { Locator.fromJSON(JSONObject(it)) }?.let { navigator?.go(it) }
                2 -> prefs.edit().remove("bookmark:$bookId").apply()
            }
        }.show()
    }

    override fun onPause() {
        navigator?.currentLocator?.value?.let { prefs.edit().putString("position:$bookId", it.toJSON().toString()).commit() }
        super.onPause()
    }
    override fun onDestroy() { super.onDestroy(); publication?.close() }
}
