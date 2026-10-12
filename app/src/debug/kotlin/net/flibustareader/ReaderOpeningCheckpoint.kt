package net.flibustareader

import android.content.Context
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

/** Private, file-controlled checkpoint for the emulator lifecycle regression script. */
internal object ReaderOpeningCheckpoint {
    suspend fun beforeAttach(context: Context) {
        val directory = File(context.filesDir, "reader-opening-checkpoint")
        val hold = File(directory, "hold")
        if (!hold.exists()) return
        File(directory, "reached").writeText("prepared")
        withTimeout(60_000) {
            while (hold.exists()) delay(50)
        }
        File(directory, "released").writeText("released")
    }

    fun afterAttach(context: Context) {
        val directory = File(context.filesDir, "reader-opening-checkpoint")
        if (File(directory, "reached").exists()) {
            File(directory, "attached").writeText("attached")
        }
    }
}
