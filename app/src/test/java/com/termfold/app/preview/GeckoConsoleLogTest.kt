package com.termfold.app.preview

import android.webkit.ConsoleMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeckoConsoleLogTest {
    @Test
    fun recordAndReadBack() {
        val key = "test-session-console"
        GeckoConsoleLog.clear(key)
        var seen = 0
        val remove = GeckoConsoleLog.addListener(key) { seen++ }
        GeckoConsoleLog.record(key, "log", "hello", "page")
        GeckoConsoleLog.record(key, "error", "boom", "")
        remove()
        assertEquals(2, seen)
        val text = GeckoConsoleLog.text(key, errorsOnly = false, since = 0)
        assertTrue(text.contains("hello"))
        assertEquals(1, GeckoConsoleLog.errorsSince(key, 0))
        GeckoConsoleLog.clear(key)
    }
}
