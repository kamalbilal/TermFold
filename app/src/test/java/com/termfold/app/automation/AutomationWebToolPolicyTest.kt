package com.termfold.app.automation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

class AutomationWebToolPolicyTest {
    @Test
    fun blocksSearchAndFetchKinds() {
        assertTrue(AutomationWebToolPolicy.blocksBuiltInWebTool("search", "Web search: foo", "web_search_0"))
        assertTrue(AutomationWebToolPolicy.blocksBuiltInWebTool("fetch", "WebFetch https://example.com", "fetch_1"))
        assertFalse(AutomationWebToolPolicy.blocksBuiltInWebTool("read", "Read README.md", "read_0"))
    }

    @Test
    fun blocksFromPermissionPayload() {
        val params = JSONObject()
            .put(
                "toolCall",
                JSONObject()
                    .put("toolCallId", "web_search_0")
                    .put("kind", "search")
                    .put("title", "Web search: node lts"),
            )
        assertTrue(AutomationWebToolPolicy.shouldBlockPermission(params))
    }
}
