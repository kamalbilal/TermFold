package com.termfold.app.acp.mcp

import org.junit.Assert.assertEquals
import org.junit.Test

class McpCatalogAskUserTest {
    @Test
    fun askUserBuiltinKey() {
        assertEquals(
            "builtin:termfold-ask-user",
            McpJson.keyFor(McpSource.BUILTIN, "termfold-ask-user"),
        )
    }
}
