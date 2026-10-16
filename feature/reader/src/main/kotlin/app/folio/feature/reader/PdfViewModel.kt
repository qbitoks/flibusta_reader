package app.folio.feature.reader

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.folio.core.data.library.LibraryRepository
import app.folio.core.format.BookFormat
import app.folio.core.model.Position
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** What the reader route shows: the reflow reader, or the fixed-page PDF viewer. */
sealed interface ReaderKind {
    data object Loading : ReaderKind
    data object Reflow : ReaderKind
    data class Pdf(val title: String, val pageCount: Int, val startPage: Int) : ReaderKind
    data object Broken : ReaderKind
}

/**
 * Fixed-page PDF reading through [PdfRenderer]. The position is stored as `Position(page)` in the
 * same column as reflowed books, so library progress and «Продолжить» work unchanged.
 */
@HiltViewModel
class PdfViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val library: LibraryRepository,
) : ViewModel() {
    private val bookId: String = checkNotNull(savedState["bookId"])

    private val _kind = MutableStateFlow<ReaderKind>(ReaderKind.Loading)
    val kind: StateFlow<ReaderKind> = _kind

    /** [PdfRenderer] allows one open page at a time: every call goes through this one thread. */
    private val pdfThread = Dispatchers.IO.limitedParallelism(1)
    private var renderer: PdfRenderer? = null
    private var fd: ParcelFileDescriptor? = null
    private val bitmaps = LruCache<Pair<Int, Int>, Bitmap>(6)
    private var savedPage = -1

    init {
        viewModelScope.launch {
            val entity = library.book(bookId).first()
            if (entity?.format != BookFormat.Pdf.name) {
                _kind.value = ReaderKind.Reflow
                return@launch
            }
            _kind.value = runCatching {
                withContext(pdfThread) {
                    val file = library.originalFile(bookId, BookFormat.Pdf)
                    fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    val pdf = PdfRenderer(fd!!).also { renderer = it }
                    val count = pdf.pageCount
                    val start = (Position.decode(entity.position)?.chapter ?: 0).coerceIn(0, (count - 1).coerceAtLeast(0))
                    ReaderKind.Pdf(entity.displayTitle, count, start)
                }
            }.getOrElse { ReaderKind.Broken }
            library.markOpened(bookId)
        }
    }

    /** Page [index] rendered [width] px wide on white; null if the document is closed. */
    suspend fun render(index: Int, width: Int): Bitmap? = withContext(pdfThread) {
        bitmaps.get(index to width)?.let { return@withContext it }
        val pdf = renderer ?: return@withContext null
        pdf.openPage(index).use { page ->
            val height = (width.toLong() * page.height / page.width.coerceAtLeast(1)).toInt().coerceIn(1, width * 4)
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.WHITE)
                page.render(this, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            }
        }.also { bitmaps.put(index to width, it) }
    }

    fun onPage(index: Int, count: Int) {
        if (index == savedPage || count <= 0) return
        savedPage = index
        viewModelScope.launch(NonCancellable) {
            library.savePosition(bookId, Position(index), (index + 1f) / count)
        }
    }

    override fun onCleared() {
        // Close on the renderer's thread so a render in flight finishes first.
        val pdf = renderer
        val file = fd
        renderer = null
        @Suppress("OPT_IN_USAGE")
        kotlinx.coroutines.GlobalScope.launch(pdfThread) {
            runCatching { pdf?.close() }
            runCatching { file?.close() }
        }
    }
}
