package com.termfold.app.acp.mcp

import org.junit.Assert.assertEquals
import org.junit.Test

class McpCatalogPdfToolsTest {
    @Test
    fun pdfToolsBuiltinKey() {
        assertEquals(
            "builtin:termfold-pdf",
            McpJson.keyFor(McpSource.BUILTIN, "termfold-pdf"),
        )
    }
}
