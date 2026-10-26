package com.termfold.app.preview.network

import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkDriverFormatTest {
    @org.junit.Before
    fun syncIngest() {
        NetworkCaptureStore.synchronousIngestForTests = true
    }

    @org.junit.After
    fun restoreIngest() {
        NetworkCaptureStore.synchronousIngestForTests = false
    }

    @Test
    fun listRespectsLimit() {
        val key = "driver-fmt"
        NetworkCaptureStore.clear(key)
        repeat(5) { i ->
            NetworkCaptureStore.recordLegacy(key, "GET", "https://x.test/$i", "ok")
        }
        val (text, _) = NetworkCaptureStore.listText(key, 0, false, false, null, 2)
        assertTrue(text.lines().filter { it.startsWith("#") }.size <= 2)
    }

    @Test
    fun invalidEntryIdMessage() {
        val text = NetworkCaptureStore.detailText("none", 99999L, "overview")
        assertTrue(text.contains("No entry"))
    }
}
