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
import app.folio.core.opds.DownloadCheck
import app.folio.core.opds.HttpStatusException
import app.folio.core.opds.HttpsRequest
import app.folio.core.opds.OpdsParser
import app.folio.core.opds.OpenSearch
import app.folio.core.opds.SearchLink
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.URL
import java.security.MessageDigest
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OPDS catalog over HTTPS: feeds (cached for a few minutes, retried once on a transient failure),
 * cover thumbnails, recent searches and the user's [Catalog] list (JSON in the `catalog` prefs).
 * Downloads live in [DownloadManager].
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

    private val _catalogs = MutableStateFlow(loadCatalogs())
    /** The user's catalogs in their order. */
    val catalogs: StateFlow<List<Catalog>> = _catalogs.asStateFlow()

    private val _current = MutableStateFlow(_catalogs.value.firstOrNull { it.id == prefs.getString(KEY_CURRENT, null) } ?: _catalogs.value.firstOrNull())
    /** The catalog the tab shows; null when the list is empty. */
    val current: StateFlow<Catalog?> = _current.asStateFlow()

    private val searchTemplates = Collections.synchronizedMap(HashMap<String, String?>())

    fun select(id: String) {
        val catalog = _catalogs.value.firstOrNull { it.id == id } ?: return
        _current.value = catalog
        prefs.edit().putString(KEY_CURRENT, id).apply()
    }

    /**
     * Adds or replaces (same id) a catalog after checking its addresses; throws
     * [IllegalArgumentException] with a user message. A new catalog becomes the current one.
     */
    fun save(catalog: Catalog): Catalog {
        val checked = catalog.copy(
            title = catalog.title.trim().ifEmpty { runCatching { URL(catalog.address.trim()).host }.getOrDefault("") },
            address = validateFeed(catalog.address),
            downloads = catalog.downloads?.trim()?.takeIf { it.isNotEmpty() }?.let(::validateBase),
        )
        val list = _catalogs.value
        val index = list.indexOfFirst { it.id == checked.id }
        store(if (index >= 0) list.toMutableList().also { it[index] = checked } else list + checked)
        if (index < 0 || _current.value?.id == checked.id) select(checked.id)
        feeds.clear()
        return checked
    }

    fun remove(id: String) {
        store(_catalogs.value.filterNot { it.id == id })
        if (_current.value?.id == id) {
            _current.value = _catalogs.value.firstOrNull()
            prefs.edit().putString(KEY_CURRENT, _current.value?.id).apply()
        }
    }

    fun move(from: Int, to: Int) {
        val list = _catalogs.value.toMutableList()
        if (from !in list.indices || to !in list.indices) return
        list.add(to, list.removeAt(from))
        store(list)
    }

    private val _downloadIssues = MutableStateFlow(decodeIssues(prefs.getString(KEY_ISSUES, null)))
    /** Catalog host → [NetworkErrors] code of its last download that the site refused; cleared by a success. */
    val downloadIssues: StateFlow<Map<String, String>> = _downloadIssues.asStateFlow()

    /** Records how a download from [link] ended: a refusal marks its catalog, a success clears the mark. */
    fun reportDownload(link: String, code: String?) {
        val host = runCatching { URL(link).host }.getOrNull() ?: return
        val owner = _catalogs.value.firstOrNull { c -> runCatching { URL(c.address).host }.getOrNull() == host || c.downloads?.let { URL(it).host } == host }
            ?.let { URL(it.address).host } ?: host
        val next = if (code != null && NetworkErrors.isUnavailable(code)) _downloadIssues.value + (owner to code) else _downloadIssues.value - owner
        if (next == _downloadIssues.value) return
        _downloadIssues.value = next
        prefs.edit().putString(KEY_ISSUES, next.entries.joinToString("\n") { "${it.key}\t${it.value}" }).apply()
    }

    private fun decodeIssues(value: String?): Map<String, String> =
        value.orEmpty().lines().mapNotNull { line -> line.split('\t').takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()

    /** A new id for a catalog the user adds by address. */
    fun newId(): String = "c" + System.currentTimeMillis().toString(36)

    /** A book link rewritten to its catalog's download mirror, if that catalog has one. */
    fun downloadAddress(link: String): String {
        val url = URL(link)
        val owner = _catalogs.value.firstOrNull { it.downloads != null && runCatching { URL(it.address).host }.getOrNull() == url.host } ?: return link
        return URL(URL(owner.downloads), url.file).toString()
    }

    /**
     * The URL template the catalog searches with: the [declared] link itself, or the Atom template
     * of the OpenSearch description it points to; null when the catalog only searches on its website.
     */
    suspend fun searchTemplate(declared: SearchLink): String? {
        if (!declared.isDescription) return declared.href
        if (searchTemplates.containsKey(declared.href)) return searchTemplates[declared.href]
        val template = withRetry {
            runInterruptible(Dispatchers.IO) {
                HttpsRequest().read(declared.href) { stream, _, finalUrl ->
                    OpenSearch.template(bounded(stream, MAX_COVER_BYTES).toString(Charsets.UTF_8), finalUrl)
                }
            }
        }
        searchTemplates[declared.href] = template
        return template
    }

    suspend fun page(address: String, fresh: Boolean = false): CatalogPage {
        if (!fresh) feeds[address]?.let { (at, page) -> if (System.currentTimeMillis() - at < FEED_TTL_MS) return page }
        val page = try { fetchPage(address) } catch (e: HttpStatusException) {
            if (e.code == 401) throw DownloadException(NetworkErrors.LOGIN, e.message.orEmpty()) else throw e
        }
        feeds[address] = System.currentTimeMillis() to page
        return page
    }

    private suspend fun fetchPage(address: String): CatalogPage {
        val page = withRetry {
            runInterruptible(Dispatchers.IO) {
                HttpsRequest(readTimeoutMs = FEED_READ_TIMEOUT_MS).read(address) { stream, _, finalUrl ->
                    val text = bounded(stream, MAX_FEED_BYTES).toString(Charsets.UTF_8)
                    try {
                        OpdsParser.parse(text, finalUrl)
                    } catch (e: Exception) {
                        // An error or sign-in page served with 200 (EKNIGA's MySQL error, a captcha).
                        if (e is InterruptedException) throw e
                        val login = DownloadCheck.asksToSignIn(text.toByteArray())
                        throw DownloadException(if (login) NetworkErrors.LOGIN else NetworkErrors.NOT_FEED, e.message ?: "Не лента OPDS")
                    }
                }
            }
        }
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

    private fun store(list: List<Catalog>) {
        _catalogs.value = list
        prefs.edit().putString(KEY_CATALOGS, CatalogCodec.encode(list)).apply()
    }

    /** Saved list; on first run, the former single source (kept as a Flibusta-style catalog) or the starter set. */
    private fun loadCatalogs(): List<Catalog> {
        prefs.getString(KEY_CATALOGS, null)?.let { return CatalogCodec.decode(it) }
        val oldCatalog = prefs.getString(KEY_CATALOG, null)
        val list = if (oldCatalog != null) {
            val flibusta = CatalogPresets.all.first { it.catalog.id == "flibusta" }.catalog
            if (oldCatalog == FLIBUSTA_ORIGIN && prefs.getString(KEY_DOWNLOADS, null) in listOf(null, FLIBUSTA_ORIGIN)) {
                listOf(flibusta)
            } else {
                listOf(CatalogPresets.flibustaStyle("flibusta", flibusta.title, oldCatalog, prefs.getString(KEY_DOWNLOADS, null)))
            }
        } else {
            CatalogPresets.starter
        }
        prefs.edit().putString(KEY_CATALOGS, CatalogCodec.encode(list)).remove(KEY_CATALOG).remove(KEY_DOWNLOADS).apply()
        return list
    }

    private fun validateFeed(value: String): String {
        val url = runCatching { URL(value.trim()) }.getOrNull()
        require(url != null && url.protocol == "https" && url.host.isNotBlank() && url.userInfo == null && url.ref == null) {
            "Введите HTTPS-адрес каталога OPDS, например https://example.org/opds"
        }
        return url!!.toString()
    }

    private fun validateBase(value: String): String {
        val url = runCatching { URL(value.trim()) }.getOrNull()
        require(
            url != null && url.protocol == "https" && url.host.isNotBlank() && url.userInfo == null &&
                url.query == null && url.ref == null && url.path in listOf("", "/"),
        ) { "Сервер скачивания: HTTPS-адрес сайта без пути, например https://example.org" }
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
        const val KEY_CATALOGS = "catalogs"
        const val KEY_CURRENT = "current"
        const val KEY_ISSUES = "download_issues"
        const val FLIBUSTA_ORIGIN = "https://flibusta.is"
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
