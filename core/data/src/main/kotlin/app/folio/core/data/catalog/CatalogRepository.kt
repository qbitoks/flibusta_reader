package app.folio.core.data.catalog

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import app.folio.core.opds.CatalogPage
import app.folio.core.opds.HttpsRequest
import app.folio.core.opds.OpdsParser
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OPDS catalog over HTTPS: feeds (cached for a few minutes, retried once on a transient failure),
 * cover thumbnails and recent searches. Sources (catalog and download host) are plain HTTPS
 * origins kept in the `catalog` prefs. Downloads live in [DownloadManager].
 */
@Singleton
class CatalogRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences("catalog", Context.MODE_PRIVATE)
    private val feeds = Collections.synchronizedMap(object : LinkedHashMap<String, Pair<Long, CatalogPage>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, CatalogPage>>) = size > MAX_CACHED_FEEDS
    })
    private val coversDir = File(context.cacheDir, "catalog-covers")
    private val coverPermits = Semaphore(4)
    private val failedCovers = Collections.synchronizedSet(HashSet<String>())

    private val _recent = MutableStateFlow(prefs.getString(KEY_RECENT, null)?.split('\n')?.filter { it.isNotBlank() }.orEmpty())
    /** Recent catalog searches, newest first. */
    val recent: StateFlow<List<String>> = _recent.asStateFlow()

    val catalogBase: String get() = prefs.getString(KEY_CATALOG, null) ?: DEFAULT_BASE
    val downloadBase: String get() = prefs.getString(KEY_DOWNLOADS, null) ?: DEFAULT_BASE

    /** Validates both origins before saving either; throws [IllegalArgumentException] with a user message. */
    fun saveSources(catalog: String, downloads: String) {
        val checkedCatalog = validateBase(catalog)
        val checkedDownloads = validateBase(downloads)
        prefs.edit().putString(KEY_CATALOG, checkedCatalog).putString(KEY_DOWNLOADS, checkedDownloads).apply()
        feeds.clear()
    }

    fun rootAddress(): String = "$catalogBase/opds"

    /** The server's flat list of this week's books (`/opds/new` itself is a menu). */
    fun newBooksAddress(): String = "$catalogBase/opds/new/0/new"

    fun searchAddress(query: String, authors: Boolean): String =
        "$catalogBase/opds/search?searchType=${if (authors) "authors" else "books"}&searchTerm=${URLEncoder.encode(query.trim(), "UTF-8")}"

    /** The acquisition [link]'s path on the download host. */
    fun downloadAddress(link: String): String = URL(URL(downloadBase), URL(link).file).toString()

    suspend fun page(address: String, fresh: Boolean = false): CatalogPage {
        if (!fresh) feeds[address]?.let { (at, page) -> if (System.currentTimeMillis() - at < FEED_TTL_MS) return page }
        val page = withRetry {
            runInterruptible(Dispatchers.IO) {
                HttpsRequest(readTimeoutMs = FEED_READ_TIMEOUT_MS).read(address) { stream, _, finalUrl ->
                    OpdsParser.parse(bounded(stream, MAX_FEED_BYTES).toString(Charsets.UTF_8), finalUrl)
                }
            }
        }
        feeds[address] = System.currentTimeMillis() to page
        return page
    }

    /** Cover image cached on disk, or null if there is none or it isn't an image. */
    suspend fun cover(url: String): File? {
        if (url in failedCovers) return null
        val file = File(coversDir, sha1(url))
        if (file.exists()) return file
        return coverPermits.withPermit {
            if (file.exists()) return@withPermit file
            try {
                val bytes = runInterruptible(Dispatchers.IO) {
                    HttpsRequest(readTimeoutMs = 20_000).read(url) { stream, _, _ -> bounded(stream, MAX_COVER_BYTES) }
                }
                if (!isImage(bytes)) { failedCovers += url; return@withPermit null }
                runInterruptible(Dispatchers.IO) {
                    coversDir.mkdirs()
                    val tmp = File(coversDir, "${file.name}.tmp")
                    tmp.writeBytes(bytes)
                    tmp.renameTo(file)
                }
                file
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failedCovers += url
                null
            }
        }
    }

    fun rememberSearch(query: String) {
        val value = query.trim()
        if (value.isEmpty()) return
        val list = (listOf(value) + _recent.value.filterNot { it.equals(value, ignoreCase = true) }).take(MAX_RECENT)
        _recent.value = list
        prefs.edit().putString(KEY_RECENT, list.joinToString("\n")).apply()
    }

    fun forgetSearch(query: String) {
        val list = _recent.value - query
        _recent.value = list
        prefs.edit().putString(KEY_RECENT, list.joinToString("\n")).apply()
    }

    fun clearRecent() {
        _recent.value = emptyList()
        prefs.edit().remove(KEY_RECENT).apply()
    }

    /** Some servers answer 502/504 or stalls on the first try and succeeds on the next. */
    private suspend fun <T> withRetry(block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        if (!NetworkErrors.isTransient(e)) throw e
        delay(1_500)
        block()
    }

    private fun validateBase(value: String): String {
        val url = runCatching { URL(value.trim()) }.getOrNull()
        require(
            url != null && url.protocol == "https" && url.host.isNotBlank() && url.userInfo == null &&
                url.query == null && url.ref == null && url.path in listOf("", "/"),
        ) { "Введите HTTPS-адрес сайта без пути, например https://example.org" }
        return "https://${url!!.authority}"
    }

    private fun bounded(input: InputStream, limit: Long): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(32 * 1024)
        while (true) {
            if (Thread.currentThread().isInterrupted) throw InterruptedException()
            val n = input.read(buffer)
            if (n < 0) break
            require(output.size().toLong() + n <= limit) { "Каталог отдал слишком большой ответ" }
            output.write(buffer, 0, n)
        }
        return output.toByteArray()
    }

    /** JPEG, PNG, GIF or WebP signature. */
    private fun isImage(bytes: ByteArray): Boolean {
        if (bytes.size < 12) return false
        fun at(i: Int) = bytes[i].toInt() and 0xFF
        return (at(0) == 0xFF && at(1) == 0xD8) ||
            (at(0) == 0x89 && at(1) == 0x50 && at(2) == 0x4E && at(3) == 0x47) ||
            (at(0) == 0x47 && at(1) == 0x49 && at(2) == 0x46) ||
            (String(bytes, 0, 4, Charsets.ISO_8859_1) == "RIFF" && String(bytes, 8, 4, Charsets.ISO_8859_1) == "WEBP")
    }

    private companion object {
        const val KEY_CATALOG = "catalog"
        const val KEY_DOWNLOADS = "downloads"
        const val KEY_RECENT = "recent"
        // TODO: remove the built-in default source; the catalog should start empty until the user adds one.
        const val DEFAULT_BASE = "https://flibusta.is"
        const val MAX_FEED_BYTES = 5L * 1024 * 1024
        const val MAX_COVER_BYTES = 3L * 1024 * 1024
        const val MAX_CACHED_FEEDS = 40
        const val MAX_RECENT = 12
        const val FEED_TTL_MS = 10 * 60 * 1000L
        /** Some servers' search can take over half a minute before it answers. */
        const val FEED_READ_TIMEOUT_MS = 60_000
    }
}

internal fun sha1(value: String): String =
    MessageDigest.getInstance("SHA-1").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
