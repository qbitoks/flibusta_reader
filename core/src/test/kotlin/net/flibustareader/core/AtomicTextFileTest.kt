package net.flibustareader.core

import java.nio.file.Files
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class AtomicTextFileTest {
    @Test fun concurrentInstancesDoNotLoseUpdates() {
        val directory = Files.createTempDirectory("library-index").toFile()
        val executor = Executors.newFixedThreadPool(4)
        try {
            val file = directory.resolve("index.json")
            val jobs = (1..4).map {
                executor.submit {
                    val index = AtomicTextFile(file)
                    repeat(25) { index.update { old -> ((old?.toInt() ?: 0) + 1).toString() } }
                }
            }
            jobs.forEach { it.get(15, TimeUnit.SECONDS) }
            assertEquals("100", AtomicTextFile(file).read())
        } finally { executor.shutdownNow(); directory.deleteRecursively() }
    }
    @Test fun failedTransactionKeepsPreviousIndex() {
        val directory = Files.createTempDirectory("library-index").toFile()
        try {
            val index = AtomicTextFile(directory.resolve("index.json"))
            index.update { "valid" }
            try { index.update { throw IllegalArgumentException("bad import") }; fail("Expected failure") }
            catch (_: IllegalArgumentException) { }
            assertEquals("valid", index.read())
        } finally { directory.deleteRecursively() }
    }
}
