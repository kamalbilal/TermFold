package com.termfold.app.preview.network

import org.junit.Assert.assertTrue
import org.junit.Test

class GeckoNetworkLogTest {
    @org.junit.Before
    fun syncIngest() {
        NetworkCaptureStore.synchronousIngestForTests = true
        NetworkCaptureStore.clear("coding-log-test")
    }

    @org.junit.After
    fun restoreIngest() {
        NetworkCaptureStore.synchronousIngestForTests = false
    }

    @Test
    fun marksBlockedAndOverridden() {
        val key = "coding-log-test"
        GeckoNetworkLog.record(key, "GET", "https://api.foo.com/v1/user", "blocked")
        GeckoNetworkLog.record(key, "GET", "https://api.foo.com/v1/cart", "overridden")
        val text = GeckoNetworkLog.text(key, failedOnly = false, sinceGeneration = 0)
        assertTrue(text.contains("(blocked:devtools)"))
        assertTrue(text.contains("overridden"))
        val failed = GeckoNetworkLog.text(key, failedOnly = true, sinceGeneration = 0)
        assertTrue(failed.contains("user"))
    }
}

class NetworkRuleCorsTest {
    @Test
    fun overrideKeepsCorsResponseHeaders() {
        val store = NetworkRuleStore(kotlin.io.path.createTempDirectory("cors").toFile())
        store.addOverride(
            "s",
            "https://api.foo.com/v1/*",
            "{\"ok\":false}",
            headers = mapOf("Access-Control-Allow-Origin" to "*"),
        )
        val rule = store.list("s").single()
        assertTrue(rule.headers["Access-Control-Allow-Origin"] == "*")
        val json = rule.toJson()
        val parsed = NetworkRuleStore.parseRule(json)!!
        assertTrue(parsed.headers["Access-Control-Allow-Origin"] == "*")
    }
}
