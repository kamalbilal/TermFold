package com.termfold.app.ui.screens

import com.termfold.app.acp.AcpItem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolCallUiTest {

    @Test
    fun mcpCodegraphShowsOutputPaneWhenCompleted() {
        val item = AcpItem.ToolCall(
            id = 1L,
            toolCallId = "tc1",
            title = "codegraph: codegraph_explore",
            kind = "other",
            status = "completed",
            detail = "",
        )
        assertTrue(toolCallShowsOutputPane(item))
        assertTrue(toolCallExpectsTextBody(item))
    }

    @Test
    fun editWithDiffsHidesTextPane() {
        val item = AcpItem.ToolCall(
            id = 2L,
            toolCallId = "tc2",
            title = "Edit foo.kt",
            kind = "edit",
            status = "completed",
            detail = "",
            fileDiffs = listOf(
                com.termfold.app.acp.AcpToolFileDiff(
                    path = "/x.kt",
                    operation = com.termfold.app.acp.AcpToolFileDiff.Operation.MODIFY,
                    patchText = "@@",
                ),
            ),
        )
        assertFalse(toolCallShowsOutputPane(item))
    }
}
