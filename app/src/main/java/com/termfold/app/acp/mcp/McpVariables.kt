package com.termfold.app.acp.mcp

import android.content.Context
import com.termfold.app.acp.mcp.McpPrefs.secret

/**
 * Cursor `mcp.json` variable substitution for paths inside the Debian guest.
 */
class McpVariables(
    private val context: Context,
    private val workspaceGuestDir: String,
    private val serverKey: String? = null,
    private val guestEnv: Map<String, String> = emptyMap(),
) {
    private val userHome = "/root"
    private val workspace = workspaceGuestDir.trimEnd('/').ifBlank { "/" }
    private val workspaceBasename = workspace.substringAfterLast('/').ifBlank { workspace }

    fun resolve(text: String): String {
        if (!text.contains('$')) return text
        var out = text
        out = out.replace("\${userHome}", userHome)
        out = out.replace("\${workspaceFolder}", workspace)
        out = out.replace("\${workspaceFolderBasename}", workspaceBasename)
        out = out.replace("\${pathSeparator}", "/")
        out = out.replace("\${/}", "/")
        return ENV_REF.replace(out) { match ->
            val name = match.groupValues[1]
            val fromPrefs = serverKey?.let { secret(context, it, name) }
            if (!fromPrefs.isNullOrEmpty()) {
                fromPrefs
            } else {
                guestEnv[name] ?: System.getenv(name) ?: match.value
            }
        }
    }

    companion object {
        private val ENV_REF = Regex("""\$\{env:([^}]+)\}""")
    }
}
