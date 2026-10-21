package com.termfold.app.acp.mcp

/**
 * One MCP server TermFold knows about for a Cursor session.
 *
 * [key] is stable for prefs (`project:my-server`, `user:github`, `builtin:termfold-browser`).
 */
data class McpServerEntry(
    val key: String,
    val name: String,
    val source: McpSource,
    val transport: McpTransport,
    val configPath: String?,
    /** Env or header names the user should fill when the config uses `${env:…}` or empty values. */
    val authHints: List<String>,
    /** Cursor `disabled: true` in mcp.json. User can override via [McpPrefs]. */
    val configDisabled: Boolean = false,
)

enum class McpSource { USER, PROJECT, BUILTIN }

sealed class McpTransport {
    data class Stdio(
        val command: String,
        val args: List<String>,
        val env: Map<String, String>,
    ) : McpTransport()

    data class Remote(
        val url: String,
        val headers: Map<String, String>,
        val kind: RemoteKind,
    ) : McpTransport()
}

enum class RemoteKind { HTTP, SSE }
