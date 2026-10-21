package com.termfold.app.acp.mcp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class McpWireTest {

    @Test
    fun stdioWireOmitsTypeAndIncludesEnvArray() {
        val entry = McpServerEntry(
            key = "project:demo",
            name = "demo",
            source = McpSource.PROJECT,
            transport = McpTransport.Stdio(
                command = "/usr/bin/python3",
                args = listOf("server.py"),
                env = mapOf("API_KEY" to "secret"),
            ),
            configPath = null,
            authHints = emptyList(),
        )
        val wire = McpWire.toAcpJson(entry, { it }, emptyMap())!!
        assertEquals("demo", wire.getString("name"))
        assertEquals("/usr/bin/python3", wire.getString("command"))
        assertFalse(wire.has("type"))
        assertEquals(1, wire.getJSONArray("env").length())
        assertEquals("API_KEY", wire.getJSONArray("env").getJSONObject(0).getString("name"))
    }

    @Test
    fun remoteWireUsesHttpType() {
        val entry = McpServerEntry(
            key = "user:remote",
            name = "remote",
            source = McpSource.USER,
            transport = McpTransport.Remote(
                url = "https://example.com/mcp",
                headers = emptyMap(),
                kind = RemoteKind.HTTP,
            ),
            configPath = null,
            authHints = emptyList(),
        )
        val wire = McpWire.toAcpJson(entry, { it }, emptyMap())!!
        assertEquals("http", wire.getString("type"))
        assertTrue(wire.has("headers"))
    }
}
