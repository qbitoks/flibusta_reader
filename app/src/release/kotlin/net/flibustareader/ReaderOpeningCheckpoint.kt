package net.flibustareader

import android.content.Context

/** Release builds contain no file-controlled checkpoint. */
@Suppress("UNUSED_PARAMETER")
internal object ReaderOpeningCheckpoint {
    suspend fun beforeAttach(context: Context) = Unit
    fun afterAttach(context: Context) = Unit
}
