package net.flibustareader.core

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.AtomicMoveNotSupportedException
import java.util.concurrent.ConcurrentHashMap

class AtomicTextFile(private val file: File) {
    private val lock = locks.getOrPut(file.canonicalPath) { Any() }
    fun read(): String? = synchronized(lock) { if (file.exists()) file.readText() else null }
    fun update(transform: (String?) -> String) = synchronized(lock) {
        val updated = transform(read())
        file.parentFile?.mkdirs()
        val temp = File.createTempFile("index-", ".part", file.parentFile)
        try {
            temp.writeText(updated)
            try { Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
            catch (_: AtomicMoveNotSupportedException) { Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING) }
        } finally { temp.delete() }
    }
    companion object { private val locks = ConcurrentHashMap<String, Any>() }
}
