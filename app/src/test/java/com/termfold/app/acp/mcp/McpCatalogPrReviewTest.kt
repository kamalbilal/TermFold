package com.termfold.app.acp.mcp

import org.junit.Assert.assertEquals
import org.junit.Test

class McpCatalogPrReviewTest {
    @Test
    fun prReviewBuiltinKey() {
        assertEquals(
            "builtin:termfold-pr-review",
            McpJson.keyFor(McpSource.BUILTIN, "termfold-pr-review"),
        )
    }
}
