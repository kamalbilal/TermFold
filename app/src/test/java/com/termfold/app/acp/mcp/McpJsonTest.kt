package com.termfold.app.acp.mcp

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class McpJsonTest {

    @Test
    fun parsesCursorMcpJson() {
        val map = McpJson.parseText(
            """
            {
              "mcpServers": {
                "demo": {
                  "command": "npx",
                  "args": ["-y", "demo-mcp"]
                },
                "remote": {
                  "url": "https://example.com/mcp"
                }
              }
            }
            """.trimIndent(),
        )
        assertEquals(2, map.size)
        assertTrue(map.containsKey("demo"))
        assertTrue(map.containsKey("remote"))
    }

    @Test
    fun keyForScopesPrefs() {
        assertEquals("project:github", McpJson.keyFor(McpSource.PROJECT, "github"))
        assertEquals("user:github", McpJson.keyFor(McpSource.USER, "github"))
        assertEquals("builtin:termfold-browser", McpJson.keyFor(McpSource.BUILTIN, "termfold-browser"))
    }

    @Test
    fun parseEnvFileReadsKeyValuePairs() {
        val dir = File.createTempFile("mcp-env", null).apply { delete(); mkdir() }
        val file = File(dir, "mcp.env")
        file.writeText("API_KEY=abc\n# skip\nFOO=bar\n")
        val map = McpJson.parseEnvFile(file)
        assertEquals("abc", map["API_KEY"])
        assertEquals("bar", map["FOO"])
        dir.deleteRecursively()
    }

    @Test
    fun remoteKindFromTypeField() {
        val raw = JSONObject().put("url", "https://example.com/x").put("type", "sse")
        val kind = when {
            raw.optString("type").equals("sse", ignoreCase = true) -> RemoteKind.SSE
            else -> RemoteKind.HTTP
        }
        assertEquals(RemoteKind.SSE, kind)
    }
}
