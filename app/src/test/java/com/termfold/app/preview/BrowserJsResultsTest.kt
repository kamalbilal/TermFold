package com.termfold.app.preview

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserJsResultsTest {
    @Test
    fun parseHelperCall_acceptsGeckoSnapshotJson() {
        val raw = """{"title":"Example","url":"https://example.com","items":[]}"""
        val obj = BrowserJsResults.parseHelperCall(raw)
        assertNotNull(obj)
        assertEquals("Example", obj!!.optString("title"))
    }

    @Test
    fun parseHelperCall_acceptsWebViewQuotedPayload() {
        val inner = """{"title":"Example","items":[]}"""
        val raw = JSONObject.quote(inner)
        val obj = BrowserJsResults.parseHelperCall(raw)
        assertNotNull(obj)
        assertEquals("Example", obj!!.optString("title"))
    }

    @Test
    fun parseTfJson_wrapsBooleanPrimitives() {
        assertTrue(BrowserJsResults.parseTfJson("true").optBoolean("value"))
    }

    @Test
    fun parseEvalString_readsPlainOrQuotedTitle() {
        assertEquals("Hello", BrowserJsResults.parseEvalString("\"Hello\""))
        assertEquals("Hello", BrowserJsResults.parseEvalString("Hello"))
    }
}
