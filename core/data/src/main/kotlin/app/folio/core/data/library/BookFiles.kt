package app.folio.core.data.library

import android.content.Context
import android.graphics.BitmapFactory
import app.folio.core.format.ResourceSink
import app.folio.core.layout.ImageSizer
import app.folio.core.layout.IntSizePx
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-disk layout:
 * - `files/books/<id>.<ext>` — the imported original (never re-downloaded, so it lives in files/)
 * - `files/covers/<id>.jpg` — library thumbnail
 * - `cache/books/<id>/book.bin` + `res/` — parsed model and extracted images; if the
 *   system clears the cache, the book is transparently re-parsed from the original.
 */
@Singleton
class BookFiles @Inject constructor(@ApplicationContext context: Context) {
    val booksDir = File(context.filesDir, "books").apply { mkdirs() }
    val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
    private val cacheRoot = File(context.cacheDir, "books")
    val importDir = File(context.cacheDir, "import").apply { mkdirs() }

    fun original(id: String, extension: String) = File(booksDir, "$id.$extension")

    fun cover(id: String) = File(coversDir, "$id.jpg")

    fun cacheDir(id: String) = File(cacheRoot, id)

    fun parsedCache(id: String) = File(cacheDir(id), "book.bin")

    fun resources(id: String) = BookResources(File(cacheDir(id), "res"))

    fun delete(id: String) {
        booksDir.listFiles { f -> f.name.startsWith("$id.") }?.forEach { it.delete() }
        cover(id).delete()
        cacheDir(id).deleteRecursively()
    }
}

/** Extracted images of one book, addressed by their in-book resource id. */
class BookResources(val dir: File) : ResourceSink, ImageSizer {
    private val sizes = ConcurrentHashMap<String, IntSizePx>()

    fun file(id: String) = File(dir, hash(id))

    override fun put(id: String, bytes: ByteArray) {
        dir.mkdirs()
        file(id).writeBytes(bytes)
    }

    override fun size(resource: String): IntSizePx? {
        sizes[resource]?.let { return it }
        val file = file(resource)
        if (!file.exists()) return null
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return null
        return IntSizePx(options.outWidth, options.outHeight).also { sizes[resource] = it }
    }

    private fun hash(id: String): String =
        MessageDigest.getInstance("SHA-1").digest(id.toByteArray()).joinToString("") { "%02x".format(it) }
}
