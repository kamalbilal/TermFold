package com.termfold.app.shell

import com.termfold.app.acp.AcpPhase
import com.termfold.app.acp.AcpSessions
import com.termfold.app.gitworkspace.SessionKeys

/** Resolves live terminal and ACP keys across workspace ids for one folder session. */
object SessionRegistry {

    fun keys(folderId: String, sessionId: String? = null): List<String> {
        val all = (TerminalHost.liveSessions().map { it.key } + AcpSessions.all().keys).distinct()
        return all.filter { key ->
            val parts = runCatching { SessionKeys.parse(key) }.getOrNull() ?: return@filter false
            parts.folderId == folderId && (sessionId == null || parts.sessionId == sessionId)
        }
    }

    fun closeAll(folderId: String, sessionId: String? = null) {
        keys(folderId, sessionId).forEach { key ->
            TerminalHost.closeSession(key)
            AcpSessions.close(key)
        }
    }

    fun isBusy(folderId: String, sessionId: String, acpAgentId: String): Boolean {
        for (key in keys(folderId, sessionId)) {
            if (acpAgentId.isNotBlank()) {
                val client = AcpSessions.client(key)
                if (client != null) {
                    val state = client.state.value
                    if (
                        state.agentBusy ||
                        state.phase == AcpPhase.INSTALLING ||
                        state.phase == AcpPhase.CONNECTING
                    ) {
                        return true
                    }
                }
            } else if (TerminalHost.isWorking(key)) {
                return true
            }
        }
        return false
    }
}
