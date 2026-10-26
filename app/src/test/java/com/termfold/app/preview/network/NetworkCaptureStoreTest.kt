package com.termfold.app.preview.network

import com.termfold.app.preview.BrowserBridge
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkCaptureStoreTest {
    @org.junit.Before
    fun syncIngest() {
        NetworkCaptureStore.synchronousIngestForTests = true
    }

    @org.junit.After
    fun restoreIngest() {
        NetworkCaptureStore.synchronousIngestForTests = false
    }

    @Test
    fun preserveOffClearsOnNavigation() {
        val key = "cap-test-1"
        NetworkCaptureStore.clear(key)
        NetworkCaptureStore.setPreserve(key, false)
        NetworkCaptureStore.recordLegacy(key, "GET", "https://example.com/a", "ok")
        NetworkCaptureStore.onMainFrameNavigation(key)
        val (text, _) = NetworkCaptureStore.listText(key, 0, false, false, null, 40)
        assertTrue(text.contains("No requests"))
    }

    @Test
    fun preserveOnKeepsAcrossNavigation() {
        val key = "cap-test-2"
        NetworkCaptureStore.clear(key)
        NetworkCaptureStore.setPreserve(key, true)
        NetworkCaptureStore.recordLegacy(key, "GET", "https://example.com/a", "ok")
        NetworkCaptureStore.onMainFrameNavigation(key)
        val (text, _) = NetworkCaptureStore.listText(key, 0, false, false, null, 40)
        assertTrue(text.contains("/a"))
    }

    @Test
    fun sinceGenerationFilter() {
        val key = "cap-test-gen"
        NetworkCaptureStore.clear(key)
        BrowserBridge.networkGeneration = 3
        NetworkCaptureStore.recordLegacy(key, "GET", "https://gen.test/old", "ok")
        BrowserBridge.networkGeneration = 9
        NetworkCaptureStore.recordLegacy(key, "GET", "https://gen.test/new", "ok")
        val (text, _) = NetworkCaptureStore.listText(key, 0, false, false, null, 40, sinceGeneration = 9)
        assertTrue(text.contains("/new"))
        assertTrue(!text.contains("/old"))
    }

    @Test
    fun sinceCursorFilters() {
        val key = "cap-test-3"
        NetworkCaptureStore.clear(key)
        NetworkCaptureStore.recordLegacy(key, "GET", "https://a.test/1", "ok")
        val (_, c1) = NetworkCaptureStore.listText(key, 0, false, false, null, 40)
        NetworkCaptureStore.recordLegacy(key, "GET", "https://a.test/2", "ok")
        val (text, _) = NetworkCaptureStore.listText(key, c1, false, false, null, 40)
        assertTrue(text.contains("/2"))
        assertTrue(!text.contains("/1") || text.lines().size <= 2)
    }

    @Test
    fun startAndDoneShareOneEntry() {
        val key = "cap-test-dedup"
        NetworkCaptureStore.clear(key)
        val reqId = "req-dedup-1"
        NetworkCaptureStore.ingest(
            JSONObject()
                .put("sessionKey", key)
                .put("phase", "start")
                .put("requestId", reqId)
                .put("method", "GET")
                .put("url", "https://dedup.test/x")
                .put("resourceType", "xmlhttprequest"),
        )
        NetworkCaptureStore.ingest(
            JSONObject()
                .put("sessionKey", key)
                .put("phase", "start")
                .put("requestId", reqId)
                .put("method", "GET")
                .put("url", "https://dedup.test/x")
                .put("resourceType", "xmlhttprequest"),
        )
        NetworkCaptureStore.ingest(
            JSONObject()
                .put("sessionKey", key)
                .put("phase", "done")
                .put("requestId", reqId)
                .put("status", 204)
                .put("outcome", "ok"),
        )
        assertEquals(1, NetworkCaptureStore.entries(key).size)
        val entry = NetworkCaptureStore.entries(key).single()
        assertEquals(204, entry.status)
        assertEquals("ok", entry.outcome)
    }

    @Test
    fun ingestStartAndDone() {
        val key = "cap-test-4"
        NetworkCaptureStore.clear(key)
        NetworkCaptureStore.ingest(
            JSONObject()
                .put("sessionKey", key)
                .put("phase", "start")
                .put("requestId", "r1")
                .put("method", "POST")
                .put("url", "https://api.test/x")
                .put("resourceType", "xmlhttprequest"),
        )
        NetworkCaptureStore.ingest(
            JSONObject()
                .put("sessionKey", key)
                .put("phase", "done")
                .put("requestId", "r1")
                .put("status", 200)
                .put("outcome", "ok"),
        )
        val (text, _) = NetworkCaptureStore.listText(key, 0, false, false, null, 40)
        assertTrue(text.contains("POST"))
        assertTrue(text.contains("200"))
    }
}
