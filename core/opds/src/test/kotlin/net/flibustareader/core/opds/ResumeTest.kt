package net.flibustareader.core.opds

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class ResumeTest {
    @Test fun freshDownloadStartsFromZero() {
        assertEquals(Resume.Restart(1000), Resume.plan(0, 200, null, 1000))
    }
    @Test fun partialContentAppendsAndKnowsTotal() {
        assertEquals(Resume.Append(1000), Resume.plan(400, 206, "bytes 400-999/1000", 600))
        assertEquals(Resume.Append(1000), Resume.plan(400, 206, "bytes 400-999/*", 600))
    }
    @Test fun serverIgnoringRangeRestarts() {
        assertEquals(Resume.Restart(1000), Resume.plan(400, 200, null, 1000))
        assertEquals(Resume.Restart(-1), Resume.plan(400, 200, null, -1))
    }
    @Test fun rangeNotSatisfiableMeansCompleteOrDiscard() {
        assertEquals(Resume.Complete, Resume.plan(1000, 416, "bytes */1000", 0))
        assertEquals(Resume.Discard, Resume.plan(1200, 416, "bytes */1000", 0))
        assertEquals(Resume.Discard, Resume.plan(1200, 416, null, 0))
    }
    @Test(expected = IOException::class) fun rejectsRangeFromAnotherOffset() {
        Resume.plan(400, 206, "bytes 300-999/1000", 700)
    }
    @Test fun recognisesHtmlInsteadOfBook() {
        assertTrue(DownloadCheck.looksLikeHtml("\n<!DOCTYPE html><html><body>Ошибка".toByteArray()))
        assertTrue(DownloadCheck.looksLikeHtml("<html><head><title>502 Bad Gateway".toByteArray()))
        assertFalse(DownloadCheck.looksLikeHtml(byteArrayOf(0x50, 0x4B, 3, 4)))
        assertFalse(DownloadCheck.looksLikeHtml("<?xml version=\"1.0\"?><FictionBook><body><p>html</p>".toByteArray()))
    }
}
