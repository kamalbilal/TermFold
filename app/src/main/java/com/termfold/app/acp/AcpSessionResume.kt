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
    fun visible(
        sessions: List<AcpPastSession>,
        hiddenIds: Set<String>,
    ): List<AcpPastSession> = sessions.filter { it.sessionId !in hiddenIds }
}
