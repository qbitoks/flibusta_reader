package net.flibustareader.core.data.catalog

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import net.flibustareader.core.data.library.ImportResult
import net.flibustareader.core.data.library.LibraryRepository
import net.flibustareader.core.opds.CatalogEntry
import net.flibustareader.core.opds.CatalogPage
import net.flibustareader.core.opds.HttpsRequest
import net.flibustareader.core.opds.OpdsParser
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OPDS catalog over HTTPS: browsing feeds and downloading books into the library.
 * Sources (catalog and download host) are plain HTTPS origins kept in the `catalog` prefs.
 */
@Singleton
class CatalogRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val library: LibraryRepository,
) {
    private val prefs = context.getSharedPreferences("catalog", Context.MODE_PRIVATE)

    val catalogBase: String get() = prefs.getString(KEY_CATALOG, null) ?: DEFAULT_BASE
    val downloadBase: String get() = prefs.getString(KEY_DOWNLOADS, null) ?: DEFAULT_BASE

    /** Validates both origins before saving either; throws [IllegalArgumentException] with a user message. */
    fun saveSources(catalog: String, downloads: String) {
        val checkedCatalog = validateBase(catalog)
        val checkedDownloads = validateBase(downloads)
        prefs.edit().putString(KEY_CATALOG, checkedCatalog).putString(KEY_DOWNLOADS, checkedDownloads).apply()
    }

    fun searchAddress(query: String, authors: Boolean): String =
        "$catalogBase/opds/search?searchType=${if (authors) "authors" else "books"}&searchTerm=${URLEncoder.encode(query.trim(), "UTF-8")}"

    fun newAddress(): String = "$catalogBase/opds/new"

    suspend fun page(address: String): CatalogPage = runInterruptible(Dispatchers.IO) {
        HttpsRequest().read(address) { stream, _, finalUrl ->
            OpdsParser.parse(bounded(stream, MAX_FEED_BYTES).toString(Charsets.UTF_8), finalUrl)
        }
    }

    /** Downloads [format] of [entry] via the download host and imports it; returns the book id. */
    suspend fun download(entry: CatalogEntry, format: String, progress: (Int?) -> Unit): ImportResult {
        val link = entry.downloads[format] ?: throw IllegalArgumentException("Формат недоступен")
        val address = URL(URL(downloadBase), URL(link).file).toString()
        val temp = File.createTempFile("download", ".part", context.cacheDir)
        try {
            runInterruptible(Dispatchers.IO) {
                HttpsRequest().read(address) { stream, size, _ ->
                    temp.outputStream().use { output ->
                        val buffer = ByteArray(32 * 1024)
                        var total = 0L
                        while (true) {
                            if (Thread.currentThread().isInterrupted) throw InterruptedException()
                            val count = stream.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= LibraryRepository.MAX_BOOK_BYTES) { "Книга больше 50 МБ" }
                            output.write(buffer, 0, count)
                            progress(if (size > 0) (100 * total / size).toInt().coerceAtMost(100) else null)
                        }
                        require(total > 0 && (size <= 0 || total == size)) { "Загрузка прервана" }
                    }
                }
            }
            return library.import(Uri.fromFile(temp), sourceUri = link)
        } finally {
            temp.delete()
        }
    }

    private fun validateBase(value: String): String {
        val url = runCatching { URL(value.trim()) }.getOrNull()
        require(
            url != null && url.protocol == "https" && url.host.isNotBlank() && url.userInfo == null &&
                url.query == null && url.ref == null && url.path in listOf("", "/"),
        ) { "Введите HTTPS-адрес сайта без пути, например https://flibusta.is" }
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

    private companion object {
        const val KEY_CATALOG = "catalog"
        const val KEY_DOWNLOADS = "downloads"
        const val DEFAULT_BASE = "https://flibusta.is"
        const val MAX_FEED_BYTES = 5L * 1024 * 1024
    }
}
