package com.termfold.app.preview.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeckoLoadProgressTest {
    @Test
    fun subresourceProgressDoesNotUnsettleAfterPageStop() {
        val p = GeckoLoadProgress()
        p.onNavigate()
        p.onPageStart("https://youtu.be/x")
        p.onProgressChange(40)
        assertEquals(40, p.value)
        p.onPageStop()
        assertEquals(100, p.value)
        assertTrue(p.documentSettled)
        p.onProgressChange(72)
        assertEquals(100, p.value)
    }

    @Test
    fun spaPageStartAfterStopDoesNotRestartSpinner() {
        val p = GeckoLoadProgress()
        p.onNavigate()
        p.onPageStart("https://youtu.be/x")
        p.onPageStop()
        p.onPageStart("https://www.youtube.com/watch?v=x")
        p.onProgressChange(80)
        assertEquals(100, p.value)
        assertTrue(p.documentSettled)
    }
}
