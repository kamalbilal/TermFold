package com.termfold.app.acp

import com.termfold.app.acp.AcpItem.ToolCall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InteractiveAskTimelineTest {

    private fun askTool(id: Long, wireId: String) = ToolCall(
        id = id,
        toolCallId = wireId,
        title = "termfold-ask-user: ask_user",
        kind = "mcp",
        status = "completed",
        detail = "",
    )

    @Test
    fun pairsByWireIdWhenEqual() {
        val snap = AskQuestionTurnSnapshot("wire-1", "Q", emptyList(), AskQuestionPhase.Answered)
        val map = pairAskSnapshotsWithAskTools(listOf(askTool(1L, "wire-1")), listOf(snap))
        assertEquals(snap, map["wire-1"])
    }

    @Test
    fun pairsByOrderWhenMcpGeneratedId() {
        val snap = AskQuestionTurnSnapshot("ask_999", "Q", emptyList(), AskQuestionPhase.Answered)
        val map = pairAskSnapshotsWithAskTools(listOf(askTool(1L, "cursor-wire-id")), listOf(snap))
        assertEquals(snap, map["cursor-wire-id"])
    }

    @Test
    fun inlineIdsExcludeFooterDuplicates() {
        val turn = listOf(
            AcpItem.UserMessage(10L, "hi", emptyList()),
            askTool(1L, "w1"),
            AcpItem.AgentText(2L, "summary"),
        )
        val snaps = listOf(AskQuestionTurnSnapshot("ask_1", null, emptyList(), AskQuestionPhase.Answered))
        assertEquals(setOf("ask_1"), inlineAskSnapshotIdsForTurn(turn, snaps))
    }
}
