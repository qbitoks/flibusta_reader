package app.folio.core.speech

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Mono float PCM synthesized by an offline engine. */
internal class Pcm(val samples: FloatArray, val sampleRate: Int)

/** Plays [Pcm] phrases through one reused [AudioTrack] (offline engines: Piper, Silero). */
internal class PcmPlayer {
    private var track: AudioTrack? = null

    /** Plays [pcm] to the end; cancelling stops the sound at once. */
    suspend fun play(pcm: Pcm) = withContext(Dispatchers.IO) {
        val t = track(pcm.sampleRate)
        val start = t.playbackHeadPosition
        try {
            t.play()
            var offset = 0
            while (offset < pcm.samples.size) {
                currentCoroutineContext().ensureActive()
                val n = minOf(CHUNK, pcm.samples.size - offset)
                val written = t.write(pcm.samples, offset, n, AudioTrack.WRITE_BLOCKING)
                if (written < 0) throw SpeechUnavailableException("AudioTrack error $written")
                offset += written
            }
            while (t.playbackHeadPosition - start < pcm.samples.size) delay(15)
        } catch (e: Throwable) {
            t.pause()
            t.flush()
            throw e
        }
    }

    fun release() {
        track?.release()
        track = null
    }

    private fun track(sampleRate: Int): AudioTrack {
        track?.takeIf { it.sampleRate == sampleRate }?.let { return it }
        track?.release()
        val format = AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
        return AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(format)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT) * 4)
            .build()
            .also { track = it }
    }

    private companion object {
        const val CHUNK = 4096
    }
}
