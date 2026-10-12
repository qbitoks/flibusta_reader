package net.flibustareader

import android.content.Context
import android.net.Uri
import net.flibustareader.core.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.zip.ZipFile

data class LocalBook(val id: String, val title: String, val author: String, val path: String, val format: String)

class BookRepository(private val context: Context) {
    private val directory = File(context.filesDir, "books").apply { mkdirs() }
    private val index = AtomicTextFile(File(directory, "library.json"))
    private val prefs = context.getSharedPreferences("reader", Context.MODE_PRIVATE)
    init {
        synchronized(recoveredDirectories) {
            if (directory.canonicalPath !in recoveredDirectories) {
                val rows = JSONArray(index.read() ?: "[]")
                val known = (0 until rows.length()).map { rows.getJSONObject(it).getString("path") }.toSet()
                directory.listFiles()?.filter { it.extension == "part" || (it.extension == "epub" && it.absolutePath !in known) }?.forEach { it.delete() }
                recoveredDirectories.add(directory.canonicalPath)
            }
        }
    }
    var catalogBase: String
        get() = prefs.getString("catalog", "https://flibusta.is")!!
        set(value) { prefs.edit().putString("catalog", validateBase(value)).apply() }
    var downloadBase: String
        get() = prefs.getString("downloads", "https://flibusta.is")!!
        set(value) { prefs.edit().putString("downloads", validateBase(value)).apply() }

    private fun validateBase(value: String): String {
        val url = URL(value.trim())
        require(url.protocol == "https" && url.host.isNotBlank() && url.userInfo == null && url.query == null && url.ref == null && url.path in listOf("", "/")) { "Введите HTTPS адрес сайта без пути, например https://flibusta.is" }
        return "https://${url.authority}"
    }

    fun saveSources(catalog: String, downloads: String) {
        val checkedCatalog = validateBase(catalog)
        val checkedDownloads = validateBase(downloads)
        prefs.edit().putString("catalog", checkedCatalog).putString("downloads", checkedDownloads).apply()
    }

    fun library(): List<LocalBook> {
        return decode(index.read())
    }

    private fun decode(text: String?): List<LocalBook> {
        if (text == null) return emptyList()
        val data = JSONArray(text)
        return (0 until data.length()).map { i ->
            val row = data.getJSONObject(i)
            LocalBook(row.getString("id"), row.getString("title"), row.optString("author"), row.getString("path"), row.getString("format"))
        }.filter { File(it.path).isFile }
    }

    private fun save(book: LocalBook): LocalBook {
        index.update { previous ->
            encode(listOf(book) + decode(previous).filterNot { it.id == book.id })
        }
        return book
    }

    private fun encode(books: List<LocalBook>): String {
        val rows = JSONArray()
        books.forEach {
            rows.put(JSONObject().put("id", it.id).put("title", it.title).put("author", it.author).put("path", it.path).put("format", it.format))
        }
        return rows.toString()
    }

    fun remove(book: LocalBook) {
        index.update { previous -> encode(decode(previous).filterNot { it.id == book.id }) }
        File(book.path).delete()
        prefs.edit().remove("position:${book.id}").remove("bookmark:${book.id}").apply()
    }

    fun page(address: String): CatalogPage {
        return request(address) { stream, _, finalUrl -> OpdsParser.parse(boundedBytes(stream, 5L * 1024 * 1024).toString(Charsets.UTF_8), finalUrl) }
    }

    fun download(entry: CatalogEntry, format: String, progress: (String) -> Unit): LocalBook {
        val link = entry.downloads[format] ?: error("Формат недоступен")
        val source = URL(link)
        val address = URL(URL(downloadBase), source.file).toString()
        val temp = File.createTempFile("download", ".part", context.cacheDir)
        try {
            request(address) { stream, size, _ ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var total = 0L
                    while (true) {
                        checkCancellation()
                        val count = stream.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= MAX_SIZE) { "Книга превышает 50 МБ" }
                        output.write(buffer, 0, count)
                        progress(if (size > 0) "Скачивание ${(100 * total / size).coerceAtMost(100)}%" else "Скачано ${total / 1024} КБ")
                    }
                    require(total > 0 && (size <= 0 || total == size)) { "Загрузка прервана" }
                }
            }
            progress("Подготовка книги…")
            checkCancellation()
            return prepare(temp, format, entry.title, entry.author)
        } finally { temp.delete() }
    }

    fun import(uri: Uri): LocalBook {
        var name = "Книга"
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) name = it.getString(0)
        }
        val format = when {
            name.endsWith(".epub", true) -> "EPUB"
            name.endsWith(".fb2", true) || name.endsWith(".fb2.zip", true) || name.endsWith(".zip", true) -> "FB2"
            else -> error("Выберите EPUB, FB2 или FB2.ZIP. MOBI будет добавлен позже.")
        }
        val temp = File.createTempFile("import", ".part", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input -> temp.writeBytes(boundedBytes(input, MAX_SIZE)) }
                ?: error("Не удалось открыть файл")
            return prepare(temp, format, name.substringBeforeLast('.'), "")
        } finally { temp.delete() }
    }

    private fun prepare(input: File, format: String, title: String, author: String): LocalBook {
        val id = UUID.randomUUID().toString()
        val output = File(directory, "$id.epub")
        val staged = File(directory, "$id.part")
        var actualTitle = title
        try {
            if (format == "EPUB") {
                ZipFile(input).use { zip ->
                    require(zip.getEntry("META-INF/container.xml") != null) { "Сервер вернул не EPUB. Попробуйте другой источник или FB2." }
                }
                input.copyTo(staged)
            } else {
                val raw = File.createTempFile("fb2", ".fb2", context.cacheDir)
                try {
                    val signature = input.inputStream().use { it.readNBytes(2) }
                    if (signature.contentEquals(byteArrayOf(80, 75))) {
                        ZipFile(input).use { zip ->
                            val entry = zip.entries().asSequence().firstOrNull { !it.isDirectory && it.name.endsWith(".fb2", true) }
                                ?: error("В архиве нет FB2")
                            zip.getInputStream(entry).use { raw.writeBytes(boundedBytes(it, MAX_SIZE)) }
                        }
                    } else input.copyTo(raw, overwrite = true)
                    actualTitle = Fb2Converter.convert(raw, staged)
                } finally { raw.delete() }
            }
            checkCancellation()
            java.nio.file.Files.move(staged.toPath(), output.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE)
            return save(LocalBook(id, actualTitle, author, output.absolutePath, format))
        } catch (e: Exception) { output.delete(); throw e }
        finally { staged.delete() }
    }

    fun demo(): LocalBook {
        library().firstOrNull { it.title == "Первые страницы" }?.let { return it }
        val input = File.createTempFile("demo", ".fb2", context.cacheDir)
        try {
            val chapters = (1..5).joinToString("") { chapter ->
                "<section id=\"chapter$chapter\"><title><p>Глава $chapter. Время читать</p></title>" +
                    (1..20).joinToString("") { "<p>Это демонстрационная книга Flibusta Reader. Здесь можно проверить размер шрифта, светлую и тёмную темы, прокрутку и перелистывание. Закройте книгу и откройте снова — приложение сохранит место чтения. На планшете текст остаётся удобным для чтения.</p>" } + "</section>"
            }
            input.writeText("""<?xml version="1.0" encoding="UTF-8"?><FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0"><description><title-info><book-title>Первые страницы</book-title><lang>ru</lang></title-info></description><body>$chapters</body></FictionBook>""")
            return prepare(input, "FB2", "Первые страницы", "Демонстрационная книга")
        } finally { input.delete() }
    }

    private fun <T> request(address: String, read: (InputStream, Long, String) -> T): T = HttpsRequest().read(address, read)

    private fun boundedBytes(input: InputStream, limit: Long): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(32 * 1024)
        while (true) {
            checkCancellation()
            val n = input.read(buffer)
            if (n < 0) break
            require(output.size().toLong() + n <= limit) { "Файл слишком большой" }
            output.write(buffer, 0, n)
        }
        return output.toByteArray()
    }

    companion object {
        const val MAX_SIZE = 50L * 1024 * 1024
        private val recoveredDirectories = mutableSetOf<String>()
    }

    private fun checkCancellation() {
        if (Thread.currentThread().isInterrupted) throw InterruptedException("Операция отменена")
    }
}
