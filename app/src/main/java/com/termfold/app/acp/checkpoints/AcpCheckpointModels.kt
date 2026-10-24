package com.termfold.app.acp.checkpoints

import com.termfold.app.acp.AcpItem

/** Shadow-git commit hash captured immediately before a user message is sent. */
data class PreTurnCheckpoint(
    val userMessageId: Long,
    val commitHash: String,
)

/** In-flight undo/redo on a specific user message row. */
sealed class CheckpointBusy {
    abstract val messageId: Long

    data class Undo(override val messageId: Long) : CheckpointBusy()
    data class Redo(override val messageId: Long) : CheckpointBusy()
}

/**
 * One undo step. Redo restores the timeline, ghost marker, checkpoint map, and worktree tip.
 */
data class AcpRedoFrame(
    val items: List<AcpItem>,
    val ghostFromItemId: Long?,
    val preTurnByMessageId: Map<Long, String>,
    val undoableMessageIds: Set<Long>,
    val worktreeTipCommit: String?,
    /** Wire session before undo resync, for best-effort redo of agent memory. */
    val wireSessionId: String? = null,
)

data class AcpCheckpointUiState(
    val preTurnByMessageId: Map<Long, String> = emptyMap(),
    /** User turns where the workspace differed from the pre-turn snapshot after the agent finished. */
    val undoableMessageIds: Set<Long> = emptySet(),
    val redoStack: List<AcpRedoFrame> = emptyList(),
    /** Items with id >= this value render at reduced opacity until the branch is committed. */
    val ghostFromItemId: Long? = null,
    val wireResyncing: Boolean = false,
    val busy: CheckpointBusy? = null,
    /** User message id for which context compress already ran (idempotent across restore). */
    val compressedForUserMessageId: Long? = null,
) {
    val canRedo: Boolean get() = redoStack.isNotEmpty()
}
