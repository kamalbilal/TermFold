package com.termfold.app.gitworkspace

/** Composite keys for terminals, ACP clients, drafts, and notifications. */
object SessionKeys {
    data class Parts(val folderId: String, val workspaceId: String, val sessionId: String)

    fun compose(folderId: String, workspaceId: String, sessionId: String): String =
        "$folderId/$workspaceId/$sessionId"

    fun parse(key: String): Parts {
        val parts = key.split('/')
        return when (parts.size) {
            2 -> Parts(parts[0], WorkspaceIds.GOLDEN, parts[1])
            3 -> Parts(parts[0], parts[1], parts[2])
            else -> error("Invalid session key: $key")
        }
    }
}
