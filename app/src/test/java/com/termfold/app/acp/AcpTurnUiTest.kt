package com.termfold.app.acp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AcpTurnUiTest {

    @Test
    fun hidesWorkingWhenToolsDoneButBusyFlagStillSet() {
        val items = listOf(
            AcpItem.UserMessage(1L, "fix", emptyList()),
            AcpItem.ToolCall(
                id = 2L,
                toolCallId = "t1",
                title = "Edit",
                kind = "edit",
                status = "completed",
                detail = "",
                fileDiffs = oneFileDiff(),
            ),
        )
        assertFalse(AcpTurnUi.showWorkingIndicator(agentBusy = true, items))
    }

    @Test
    fun showsWorkingWhileToolInProgress() {
        val items = listOf(
            AcpItem.UserMessage(1L, "fix", emptyList()),
            AcpItem.ToolCall(
                id = 2L,
                toolCallId = "t1",
                title = "Edit",
                kind = "edit",
                status = "in_progress",
                detail = "",
            ),
        )
        assertTrue(AcpTurnUi.showWorkingIndicator(agentBusy = true, items))
    }

    private fun oneFileDiff() = listOf(
        AcpToolFileDiff(path = "/x", operation = AcpToolFileDiff.Operation.MODIFY, patchText = "@@"),
    )
}
