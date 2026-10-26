package com.termfold.app.preview.network

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.io.path.createTempDirectory

class NetworkRuleCommandsTest {
    @Test
    fun opsStayOnOneSession() {
        val store = NetworkRuleStore(createTempDirectory("nr").toFile())
        NetworkRuleCommands.execute(
            store,
            "coding-a",
            JSONObject().put("op", "add_block").put("url_pattern", "https://a.test/*"),
        )
        NetworkRuleCommands.execute(
            store,
            "coding-b",
            JSONObject().put("op", "add_override").put("url_pattern", "https://b.test/*").put("body", "{}"),
        )
        NetworkRuleCommands.execute(store, "coding-a", JSONObject().put("op", "clear"))
        assertTrue(store.list("coding-a").isEmpty())
        assertEquals(1, store.list("coding-b").size)
        val listed = NetworkRuleCommands.execute(store, "coding-b", JSONObject().put("op", "list")).optString("text")
        assertTrue(listed.contains("https://b.test/*"))
        assertFalse(listed.contains("https://a.test/*"))
    }
}
