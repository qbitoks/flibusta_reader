@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)
package net.flibustareader

import android.content.*
import android.os.BatteryManager
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.*
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withResumed
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.flibustareader.core.*
import org.json.JSONObject
import org.json.JSONTokener
import org.readium.r2.navigator.epub.*
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.preferences.ColumnCount
import org.readium.r2.navigator.preferences.Color as ReadiumColor
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.TextAlign
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.positionsByReadingOrder
import org.readium.r2.shared.util.mediatype.MediaType
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.toUrl
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import java.io.File
import java.util.Date

class ReaderActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("reader", MODE_PRIVATE) }
    private val preferencesStore by lazy { ReaderPreferences(prefs) }
    private var options by mutableStateOf(ReaderOptions())
    private var ready by mutableStateOf(false)
    private var settingsVisible by mutableStateOf(false)
    private var controlsVisible = false
    private var clock by mutableStateOf("")
    private var battery by mutableStateOf("—")
    private var progress by mutableFloatStateOf(0f)
    private var chapter by mutableStateOf("Чтение книги")
    private var title by mutableStateOf("")
    private var author by mutableStateOf("")
    private var hasBookmark by mutableStateOf(false)
    private var safeTop by mutableIntStateOf(24)
    private var safeBottom by mutableIntStateOf(0)
    private var footerBottom by mutableIntStateOf(0)
    private var navigator: EpubNavigatorFragment? = null
    private var publication: Publication? = null
    private var weights: List<Int> = emptyList()
    private var chapterJob: Job? = null
    private var reflowJob: Job? = null
    private var reflowAnchor by mutableStateOf<Locator?>(null)
    private var reflowSubmitted = false
    private lateinit var bookId: String
    private lateinit var root: FrameLayout
    private lateinit var bookFrame: FrameLayout
    private lateinit var top: ComposeView
    private lateinit var bottom: ComposeView
    private lateinit var indicators: ComposeView
    private var registered = false
    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateClock()
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                battery = if (level >= 0 && scale > 0) "${(level.toLong() * 100 / scale).coerceIn(0, 100)}%" else "—"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Recreate the publication-dependent Fragment factory and restore a persisted locator.
        super.onCreate(null)
        bookId = intent.getStringExtra("id") ?: run { finish(); return }
        options = preferencesStore.load()
        title = intent.getStringExtra("title").orEmpty()
        author = intent.getStringExtra("author").orEmpty()
        hasBookmark = prefs.contains("bookmark:$bookId")
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        window.isStatusBarContrastEnforced = false
        window.isNavigationBarContrastEnforced = false
        createLayout()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when { settingsVisible -> settingsVisible = false; controlsVisible -> setControls(false); else -> finish() }
            }
        })
        updateWindow()
        openPublication()
    }

    private fun createLayout() {
        root = FrameLayout(this)
        bookFrame = FrameLayout(this).apply { id = R.id.reader_container }
        root.addView(bookFrame, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER_HORIZONTAL))
        top = ComposeView(this).apply {
            setContent {
                ReadingTheme(options.color) {
                    ReaderHeader(title, author, safeTop, { finish() }, ::tableOfContents, ::toggleBookmark, ::restoreBookmark,
                        { changeOptions(options.copy(clockVisible = true, progressVisible = true, batteryVisible = true), false) },
                        { settingsVisible = true })
                    if (settingsVisible) ReaderSettingsSheet(options, { changeOptions(it) }, { settingsVisible = false })
                }
            }
        }
        bottom = ComposeView(this).apply {
            setContent {
                ReadingTheme(options.color) {
                    ReaderBottomPanel(chapter, progress, safeBottom, hasBookmark, ready && reflowAnchor == null, ::seek, ::tableOfContents, ::toggleBookmark,
                        { settingsVisible = true })
                }
            }
        }
        indicators = ComposeView(this).apply {
            setContent { ReadingTheme(options.color) { ReaderIndicators(options, clock, "${(progress * 100).toInt()}%", battery, footerBottom) { changeOptions(options.toggle(it), false) } } }
        }
        root.addView(top, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
        root.addView(bottom, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        root.addView(indicators, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        top.visibility = View.GONE
        bottom.visibility = View.GONE
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val stable = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            safeTop = (stable.top / resources.displayMetrics.density).toInt()
            safeBottom = (stable.bottom / resources.displayMetrics.density).toInt()
            val cutout = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.displayCutout())
            footerBottom = maxOf((cutout.bottom / resources.displayMetrics.density).toInt(), safeBottom.coerceAtMost(8))
            sizeBookFrame(stable.left, stable.right, insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.displayCutout()).top)
            insets
        }
        root.addOnLayoutChangeListener { _, l, _, r, _, oldL, _, oldR, _ -> if (r - l != oldR - oldL) ViewCompat.requestApplyInsets(root) }
        root.doOnLayout { ViewCompat.requestApplyInsets(root) }
    }

    @android.annotation.SuppressLint("RtlHardcoded") // Insets are physical coordinates; LEFT is intentional.
    private fun sizeBookFrame(left: Int, right: Int, cutoutTop: Int) {
        if (root.width <= 0) return
        val density = resources.displayMetrics.density
        val viewport = ReaderViewport.horizontal(root.width, left, right, (760 * density).toInt())
        val params = bookFrame.layoutParams as FrameLayout.LayoutParams
        val bottomMargin = ((footerBottom + ReaderViewport.HUD_HEIGHT_DP) * density).toInt()
        val topMargin = cutoutTop.coerceAtLeast((12 * density).toInt())
        if (params.width != viewport.width || params.leftMargin != viewport.left || params.bottomMargin != bottomMargin || params.topMargin != topMargin) {
            params.width = viewport.width; params.leftMargin = viewport.left; params.gravity = Gravity.LEFT
            params.topMargin = topMargin; params.bottomMargin = bottomMargin
            bookFrame.layoutParams = params
        }
        listOf(top, bottom, indicators).forEach { it.setPadding(left, 0, right, 0) }
    }

    private fun openPublication() {
        lifecycleScope.launch {
            try {
                val path = intent.getStringExtra("path") ?: error("Не указан файл")
                require(File(path).isFile) { "Файл книги не найден" }
                val http = DefaultHttpClient()
                val retriever = AssetRetriever(contentResolver, http)
                val asset = retriever.retrieve(File(path).toUrl()).getOrElse { error("Не удалось прочитать EPUB: $it") }
                val book = PublicationOpener(DefaultPublicationParser(this@ReaderActivity, http, retriever, null))
                    .open(asset, allowUserInteraction = false).getOrElse { error("Не удалось открыть книгу: $it") }
                publication = book
                title = book.metadata.title.orEmpty().ifBlank { title }
                author = book.metadata.authors.joinToString(", ") { it.name }.ifBlank { author }
                weights = book.positionsByReadingOrder().let { list -> book.readingOrder.indices.map { list.getOrNull(it)?.size?.coerceAtLeast(1) ?: 1 } }
                suspendCancellableCoroutine<Unit> { continuation ->
                    root.doOnLayout {
                        ViewCompat.requestApplyInsets(root)
                        bookFrame.doOnLayout { if (continuation.isActive) continuation.resume(Unit) }
                    }
                }
                val position = readLocator("position:$bookId")
                supportFragmentManager.fragmentFactory = EpubNavigatorFactory(book).createFragmentFactory(
                    initialLocator = position, initialPreferences = readiumPreferences(),
                    configuration = EpubNavigatorFragment.Configuration(shouldApplyInsetsPadding = false),
                    paginationListener = object : EpubNavigatorFragment.PaginationListener {
                        override fun onPageLoaded() { ready = true }
                    }
                )
                val fragment = supportFragmentManager.fragmentFactory.instantiate(classLoader, EpubNavigatorFragment::class.java.name) as EpubNavigatorFragment
                navigator = fragment
                fragment.addInputListener(object : InputListener {
                    override fun onTap(event: TapEvent): Boolean {
                        if (!ready) return false
                        val width = fragment.view?.width?.takeIf { it > 0 } ?: return false
                        when (ReaderInteraction.tap(event.point.x.toDouble() / width, options.scroll)) {
                            ReaderTap.PREVIOUS -> if (reflowAnchor == null) fragment.goBackward(animated = true)
                            ReaderTap.NEXT -> if (reflowAnchor == null) fragment.goForward(animated = true)
                            ReaderTap.TOGGLE_CONTROLS -> setControls(!controlsVisible)
                        }
                        return true
                    }
                })
                ReaderOpeningCheckpoint.beforeAttach(this@ReaderActivity)
                lifecycle.withResumed {
                    check(!supportFragmentManager.isStateSaved)
                    supportFragmentManager.beginTransaction().replace(R.id.reader_container, fragment).commitNow()
                    ReaderOpeningCheckpoint.afterAttach(this@ReaderActivity)
                }
                prefs.edit().putString("lastBook", bookId).apply()
                fragment.currentLocator.collect { locator ->
                    if (reflowAnchor != null) return@collect
                    prefs.edit().putString("position:$bookId", locator.toJSON().toString()).apply()
                    val index = book.readingOrder.indexOfFirst { it.url() == locator.href }.coerceAtLeast(0)
                    progress = ReaderInteraction.bookProgress(index, locator.locations.progression ?: 0.0, weights).toFloat()
                    refreshChapter(locator)
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                lifecycle.withResumed {
                    android.app.AlertDialog.Builder(this@ReaderActivity).setTitle("Не удалось открыть книгу").setMessage(e.message).setPositiveButton("Вернуться") { _, _ -> finish() }.setOnCancelListener { finish() }.show()
                }
            }
        }
    }

    private fun readiumPreferences(): EpubPreferences {
        val palette = ReaderPalette.forColor(options.color)
        return EpubPreferences(
            scroll = options.scroll, fontSize = options.fontSize,
            theme = if (palette.dark) Theme.DARK else Theme.LIGHT,
            backgroundColor = ReadiumColor(palette.paper.toArgb()), textColor = ReadiumColor(palette.ink.toArgb()),
            publisherStyles = false, fontFamily = when (options.font) { ReaderFont.BOOK -> FontFamily.SERIF; ReaderFont.SANS -> FontFamily.SANS_SERIF; ReaderFont.MONO -> FontFamily.MONOSPACE },
            lineHeight = options.lineHeight, pageMargins = options.margins, textAlign = TextAlign.JUSTIFY,
            columnCount = ColumnCount.ONE, hyphens = true
        )
    }

    private fun changeOptions(updated: ReaderOptions, repaginate: Boolean = true) {
        val previous = options
        options = updated.normalized()
        preferencesStore.save(options)
        val layoutChanged = previous.font != options.font || previous.fontSize != options.fontSize ||
            previous.lineHeight != options.lineHeight || previous.margins != options.margins || previous.scroll != options.scroll
        if (repaginate && (layoutChanged || previous.color != options.color)) {
            val fragment = navigator
            if (fragment != null && ready) {
                if (reflowAnchor == null) reflowAnchor = fragment.currentLocator.value
                reflowJob?.cancel()
                reflowJob = lifecycleScope.launch {
                    if (!reflowSubmitted) reflowAnchor = fragment.firstVisibleElementLocator() ?: reflowAnchor
                    delay(150)
                    reflowSubmitted = true
                    fragment.submitPreferences(readiumPreferences())
                    awaitStableLayout(fragment)
                    reflowAnchor?.let { fragment.go(it, animated = false) }
                    awaitStableLayout(fragment)
                    reflowAnchor = null
                    reflowSubmitted = false
                    val locator = fragment.currentLocator.value
                    prefs.edit().putString("position:$bookId", locator.toJSON().toString()).apply()
                    val index = publication?.readingOrder?.indexOfFirst { it.url() == locator.href }?.coerceAtLeast(0) ?: 0
                    progress = ReaderInteraction.bookProgress(index, locator.locations.progression ?: 0.0, weights).toFloat()
                }
            }
        }
        if (previous.color != options.color || previous.systemBrightness != options.systemBrightness || previous.brightness != options.brightness) updateWindow()
    }

    private suspend fun awaitStableLayout(fragment: EpubNavigatorFragment) {
        var last: String? = null
        var same = 0
        repeat(20) {
            delay(100)
            val metrics = fragment.evaluateJavascript("JSON.stringify([document.documentElement.scrollWidth,document.documentElement.scrollHeight,window.innerWidth,window.innerHeight,window.scrollX,window.scrollY])")
            same = if (metrics != null && metrics == last) same + 1 else 0
            last = metrics
            if (same >= 3) return
        }
    }

    private fun setControls(show: Boolean) {
        controlsVisible = show
        if (!show) settingsVisible = false
        top.visibility = if (show) View.VISIBLE else View.GONE
        bottom.visibility = if (show) View.VISIBLE else View.GONE
        indicators.visibility = if (show) View.GONE else View.VISIBLE
        updateWindow()
    }

    private fun updateWindow() {
        val palette = ReaderPalette.forColor(options.color)
        root.setBackgroundColor(palette.paper.toArgb())
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(palette.paper.toArgb()))
        window.statusBarColor = palette.paper.toArgb()
        window.navigationBarColor = palette.paper.toArgb()
        WindowCompat.getInsetsController(window, root).apply {
            isAppearanceLightStatusBars = !palette.dark
            isAppearanceLightNavigationBars = !palette.dark
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (controlsVisible) show(WindowInsetsCompat.Type.systemBars()) else hide(WindowInsetsCompat.Type.systemBars())
        }
        window.attributes = window.attributes.apply { screenBrightness = if (options.systemBrightness) -1f else options.brightness.toFloat() }
    }

    private fun seek(fraction: Float) {
        if (reflowAnchor != null) return
        val book = publication ?: return
        val target = ReaderInteraction.seek(fraction.toDouble(), weights)
        val link = book.readingOrder[target.resourceIndex]
        navigator?.go(Locator(href = link.url(), mediaType = link.mediaType ?: MediaType.XHTML, title = link.title,
            locations = Locator.Locations(progression = target.progression)))
    }

    private fun tableOfContents() {
        if (reflowAnchor != null) return
        val links = mutableListOf<Pair<Link, String>>()
        fun flatten(items: List<Link>, level: Int = 0) { items.forEach { links += it to ("  ".repeat(level) + (it.title ?: "Глава")); flatten(it.children, level + 1) } }
        flatten(publication?.tableOfContents.orEmpty())
        if (links.isEmpty()) { android.app.AlertDialog.Builder(this).setMessage("В книге нет оглавления").setPositiveButton("OK", null).show(); return }
        android.app.AlertDialog.Builder(this).setTitle("Оглавление").setItems(links.map { it.second }.toTypedArray()) { _, index -> navigator?.go(links[index].first); chapter = links[index].first.title ?: "Чтение книги" }.setNegativeButton("Закрыть", null).show()
    }

    private fun toggleBookmark() {
        if (reflowAnchor != null) return
        if (hasBookmark) prefs.edit().remove("bookmark:$bookId").apply()
        else navigator?.currentLocator?.value?.let { prefs.edit().putString("bookmark:$bookId", it.toJSON().toString()).apply() }
        hasBookmark = prefs.contains("bookmark:$bookId")
    }
    private fun restoreBookmark() { if (reflowAnchor == null) readLocator("bookmark:$bookId")?.let { navigator?.go(it) } }
    private fun readLocator(key: String) = prefs.getString(key, null)?.let { runCatching { Locator.fromJSON(JSONObject(it)) }.getOrNull() }

    private fun refreshChapter(locator: Locator) {
        val defaultTitle = locator.title?.takeIf { it != title }
        if (defaultTitle != null) chapter = defaultTitle
        chapterJob?.cancel()
        chapterJob = lifecycleScope.launch {
            delay(250)
            val raw = navigator?.evaluateJavascript("""(function(){var a=Array.from(document.querySelectorAll('h1,h2,h3,section>header'));var h=a.filter(function(e){var r=e.getBoundingClientRect();return r.left<window.innerWidth*.5&&r.top<window.innerHeight*.35;}).pop();return h?h.textContent.trim():'';})()""")
            val heading = raw?.let { runCatching { JSONTokener(it).nextValue() as? String }.getOrNull() }
            if (!heading.isNullOrBlank()) chapter = heading
        }
    }

    private fun updateClock() { clock = android.text.format.DateFormat.getTimeFormat(this).format(Date()) }
    override fun onStart() {
        super.onStart()
        updateClock()
        val filter = IntentFilter().apply { addAction(Intent.ACTION_BATTERY_CHANGED); addAction(Intent.ACTION_TIME_TICK); addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED) }
        registerReceiver(systemReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        registered = true
    }
    override fun onStop() { if (registered) { unregisterReceiver(systemReceiver); registered = false }; super.onStop() }
    override fun onPause() {
        (reflowAnchor ?: navigator?.currentLocator?.value)?.let { prefs.edit().putString("position:$bookId", it.toJSON().toString()).commit() }
        super.onPause()
    }
    override fun onDestroy() { reflowJob?.cancel(); chapterJob?.cancel(); super.onDestroy(); publication?.close() }
}
