package com.termfold.app.acp

/**
 * How this TermFold chat reconnects after process death.
 *
 * The folder session (sessionKey) is the conversation the user stays in.
 * ACP `session/new` after undo/edit-revert is only a background wire rotation.
 * If TermFold saved the timeline, resume that UI and attach the remembered wire
 * session without replaying the agent's summary-first clone.
 */
data class AcpSessionResumePlan(
    val useRememberedWireSession: Boolean,
    val replayAgentHistory: Boolean,
    val keepLocalTimeline: Boolean,
    val suppressAgentReplayUpdates: Boolean,
)

object AcpSessionResume {
    fun plan(
        hasLocalTimeline: Boolean,
        rememberedWireId: String?,
        canReopen: Boolean,
    ): AcpSessionResumePlan {
        val useRemembered = !rememberedWireId.isNullOrBlank() && canReopen
        return AcpSessionResumePlan(
            useRememberedWireSession = useRemembered,
            replayAgentHistory = useRemembered && !hasLocalTimeline,
            keepLocalTimeline = hasLocalTimeline,
            suppressAgentReplayUpdates = useRemembered && hasLocalTimeline,
        )
    }
}

object AcpPastSessions {

    const val MAX_LIST_PAGES = 32

    /** SharedPreferences key: first workspace-wide → per-tab migration completed. */
    const val PREFS_MIGRATION_FLAG = "hidden_wire_per_tab_migration_done"

    fun deletedKey(agentId: String, workspaceGuestDir: String, sessionKey: String): String =
        "$agentId|$workspaceGuestDir|$sessionKey"

    fun legacyDeletedKey(agentId: String, workspaceGuestDir: String): String =
        "$agentId|$workspaceGuestDir"

    fun visible(
        sessions: List<AcpPastSession>,
        hiddenIds: Set<String>,
    ): List<AcpPastSession> = sessions.filter { it.sessionId !in hiddenIds }

    fun mergeListed(pages: Iterable<List<AcpPastSession>>): List<AcpPastSession> =
        pages.flatten().distinctBy { it.sessionId }

    /**
     * One-time move of workspace-wide hidden ids to the first tab that needs them after upgrade.
     * Other tabs start with an empty hidden set.
     */
    fun migrateHiddenIds(
        perTabHidden: Set<String>?,
        legacyHidden: Set<String>?,
        globalMigrationDone: Boolean,
    ): Pair<Set<String>, Boolean> {
        if (perTabHidden != null) return perTabHidden to globalMigrationDone
        if (!globalMigrationDone && !legacyHidden.isNullOrEmpty()) {
            return legacyHidden to true
        }
        return emptySet<String>() to globalMigrationDone
    }

    fun wireSessionToHideBeforeNew(previousWireSessionId: String?): String? =
        previousWireSessionId?.takeIf { it.isNotBlank() }
}
