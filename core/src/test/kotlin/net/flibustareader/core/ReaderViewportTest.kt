package net.flibustareader.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderViewportTest {
    @Test fun asymmetricCutoutDoesNotOverlapText() {
        assertEquals(HorizontalViewport(80, 1000), ReaderViewport.horizontal(1080, 80, 0, 2000))
        assertEquals(HorizontalViewport(0, 1000), ReaderViewport.horizontal(1080, 0, 80, 2000))
    }
    @Test fun tabletTextIsCenteredInsideSafeArea() {
        assertEquals(HorizontalViewport(240, 1200), ReaderViewport.horizontal(1600, 80, 0, 1200))
    }
}
