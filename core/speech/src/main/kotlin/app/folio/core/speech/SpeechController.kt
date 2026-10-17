package app.folio.core.speech

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import app.folio.core.data.library.LibraryRepository
import app.folio.core.model.Book
import app.folio.core.model.Position
import app.folio.core.model.progressAt
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** What is being read aloud. [bookId] null = nothing (no player, no notification). */
data class SpeechState(
    val bookId: String? = null,
    val title: String = "",
    val author: String = "",
    val playing: Boolean = false,
    val current: Utterance? = null,
    val rate: Float = 1f,
    val pitch: Float = 1f,
    val sleep: SleepTimer? = null,
    /** The engine has no voice for the book's language; shown once by the reader. */
    val noVoice: Boolean = false,
)

/** Stops reading at [endsAt] (epoch ms, set for [minutes]), or, with [chapter] set, when the voice leaves that chapter. */
data class SleepTimer(val minutes: Int? = null, val endsAt: Long? = null, val chapter: Int? = null)

/**
 * Reads a book aloud phrase by phrase, app-wide: it outlives the reader screen and is driven by the
 * reader's mini-player, the notification and headset buttons through [SpeechService].
 */
@Singleton
class SpeechController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engine: SpeechEngine,
    private val library: LibraryRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prefs = context.getSharedPreferences("speech", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(SpeechState(rate = prefs.getFloat(KEY_RATE, 1f), pitch = prefs.getFloat(KEY_PITCH, 1f)))
    val state: StateFlow<SpeechState> = _state.asStateFlow()

    private var book: Book? = null
    private var job: Job? = null
    private var sleepJob: Job? = null
    private var lastSaved = 0L
    private var pausedByFocus = false

    private val audio = context.getSystemService(AudioManager::class.java)
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        .setOnAudioFocusChangeListener { change ->
            when (change) {
                AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> if (_state.value.playing) {
                    pausedByFocus = change != AudioManager.AUDIOFOCUS_LOSS
                    pause()
                }
                AudioManager.AUDIOFOCUS_GAIN -> if (pausedByFocus) {
                    pausedByFocus = false
                    resume()
                }
            }
        }
        .build()

    /** Starts reading [book] at the sentence containing [from]. */
    fun start(bookId: String, book: Book, from: Position) {
        this.book = book
        _state.update {
            it.copy(bookId = bookId, title = book.metadata.title, author = book.metadata.authors.joinToString(", "), noVoice = false)
        }
        play(from)
    }

    fun toggle() = if (_state.value.playing) pause() else resume()

    fun resume() {
        val from = _state.value.current?.start ?: return
        play(from)
    }

    fun pause() {
        job?.cancel()
        job = null
        _state.update { it.copy(playing = false) }
        savePosition(force = true)
    }

    /** Next ([delta] > 0) or previous phrase; keeps playing or paused as it was. */
    fun skip(delta: Int) {
        val book = book ?: return
        val current = _state.value.current ?: return
        val target = if (delta > 0) {
            book.utterancesFrom(current.end, book.speechLocale()).firstOrNull { it.start >= current.end } ?: return
        } else {
            previousUtterance(book, current) ?: return
        }
        if (_state.value.playing) play(target.start) else _state.update { it.copy(current = target) }
    }

    fun setRate(rate: Float) {
        prefs.edit().putFloat(KEY_RATE, rate).apply()
        _state.update { it.copy(rate = rate) }
        // The new rate applies from the current phrase: restart it.
        if (_state.value.playing) resume()
    }

    fun setPitch(pitch: Float) {
        prefs.edit().putFloat(KEY_PITCH, pitch).apply()
        _state.update { it.copy(pitch = pitch) }
        if (_state.value.playing) resume()
    }

    /** Pauses after [minutes]; null cancels the timer. */
    fun sleepIn(minutes: Int?) {
        sleepJob?.cancel()
        sleepJob = null
        if (minutes == null) {
            _state.update { it.copy(sleep = null) }
            return
        }
        val ms = minutes * 60_000L
        _state.update { it.copy(sleep = SleepTimer(minutes, System.currentTimeMillis() + ms)) }
        sleepJob = scope.launch {
            delay(ms)
            _state.update { it.copy(sleep = null) }
            if (_state.value.playing) pause()
        }
    }

    /** Pauses when the voice reaches the next chapter. */
    fun sleepAtChapterEnd() {
        sleepJob?.cancel()
        sleepJob = null
        val chapter = _state.value.current?.start?.chapter ?: 0
        _state.update { it.copy(sleep = SleepTimer(chapter = chapter)) }
    }

    fun stop() {
        job?.cancel()
        job = null
        sleepJob?.cancel()
        sleepJob = null
        savePosition(force = true)
        engine.release()
        audio.abandonAudioFocusRequest(focus)
        book = null
        _state.update { SpeechState(rate = it.rate, pitch = it.pitch) }
    }

    /** The engine or voice changed: say the current phrase again with it. */
    fun voiceChanged() {
        if (_state.value.playing) resume()
    }

    fun dismissNoVoice() = _state.update { it.copy(noVoice = false) }

    private fun play(from: Position) {
        val book = book ?: return
        job?.cancel()
        if (audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_FAILED) return
        _state.update { it.copy(playing = true) }
        context.startForegroundService(Intent(context, SpeechService::class.java))
        job = scope.launch {
            val locale = book.speechLocale()
            try {
                val phrases = book.utterancesFrom(from, locale).iterator()
                var next = if (phrases.hasNext()) phrases.next() else null
                while (next != null) {
                    val u = next
                    next = if (phrases.hasNext()) phrases.next() else null
                    val sleepChapter = _state.value.sleep?.chapter
                    if (sleepChapter != null && u.start.chapter != sleepChapter) {
                        // The chapter timer: stop before the next chapter's first phrase.
                        _state.update { it.copy(sleep = null, current = u) }
                        pause()
                        return@launch
                    }
                    _state.update { it.copy(current = u) }
                    savePosition(force = false)
                    val rate = _state.value.rate
                    // Looking one phrase ahead lets the engine synthesize it while this one plays.
                    next?.let { engine.prepare(it.text, locale, rate) }
                    engine.speak(u.text, locale, rate, _state.value.pitch)
                }
                // The end of the book.
                stop()
            } catch (e: CancellationException) {
                throw e
            } catch (e: SpeechUnavailableException) {
                _state.update { it.copy(playing = false, noVoice = true) }
                savePosition(force = true)
            }
        }
    }

    private fun savePosition(force: Boolean) {
        val book = book ?: return
        val id = _state.value.bookId ?: return
        val position = _state.value.current?.start ?: return
        val now = System.currentTimeMillis()
        if (!force && now - lastSaved < SAVE_INTERVAL_MS) return
        lastSaved = now
        scope.launch { library.savePosition(id, position, book.progressAt(position)) }
    }

    private companion object {
        const val KEY_RATE = "rate"
        const val KEY_PITCH = "pitch"
        const val SAVE_INTERVAL_MS = 5_000L
    }
}

/** The phrase before [current], walking back over blocks with nothing to say (images, separators). */
internal fun previousUtterance(book: Book, current: Utterance): Utterance? {
    val locale = book.speechLocale()
    var c = current.start.chapter
    var b = current.start.block
    while (c >= 0) {
        val from = Position(c, b)
        book.utterancesFrom(from, locale).takeWhile { it.start < current.start }.lastOrNull()?.let { return it }
        if (b > 0) b-- else if (--c >= 0) b = (book.chapters[c].blocks.size - 1).coerceAtLeast(0)
    }
    return null
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SpeechModule {
    @Binds
    abstract fun engine(engine: SystemTtsEngine): SpeechEngine
}
