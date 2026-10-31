package com.termfold.app.acp

import com.termfold.app.preview.BrowserBridge
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class AskUserBridgeSessionKeyTest {

    @After
    fun tearDown() {
        BrowserBridge.codingSessionKey = null
        BrowserBridge.automationSessionKey = null
    }

    @Test
    fun resolveSessionKeyPrefersExplicitArg() {
        BrowserBridge.codingSessionKey = "coding"
        val key = AskUserBridge.resolveSessionKey(JSONObject().put("session_key", "from-args"))
        assertEquals("from-args", key)
    }

    @Test
    fun resolveSessionKeyFallsBackToCodingThenAutomation() {
        BrowserBridge.automationSessionKey = "auto"
        assertEquals("auto", AskUserBridge.resolveSessionKey(JSONObject()))
        BrowserBridge.codingSessionKey = "coding"
        assertEquals("coding", AskUserBridge.resolveSessionKey(JSONObject()))
    }
}
