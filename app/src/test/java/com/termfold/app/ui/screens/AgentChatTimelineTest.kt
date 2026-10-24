package com.termfold.app.ui.screens

import com.termfold.app.acp.AcpItem
import com.termfold.app.acp.AcpToolFileDiff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentChatTimelineTest {

    private fun tool(
        id: Long = 1L,
        status: String = "completed",
        fileDiffs: List<AcpToolFileDiff> = emptyList(),
    ) = AcpItem.ToolCall(
        id = id,
        toolCallId = "tc-$id",
        title = "Edit",
        kind = "edit",
        status = status,
        detail = "",
        fileDiffs = fileDiffs,
    )

    private val oneFileDiff = listOf(
        AcpToolFileDiff(path = "/root/ws/a.kt", operation = AcpToolFileDiff.Operation.MODIFY, patchText = "@@"),
    )

    @Test
    fun inProgressDefaultsExpanded() {
        assertFalse(defaultToolCallCollapsed(tool(status = "in_progress"), showLiveToolDiffs = true))
        assertFalse(defaultToolCallCollapsed(tool(status = "in_progress"), showLiveToolDiffs = false))
    }

    @Test
    fun completedFileToolExpandedWhenLiveDiffsOn() {
        assertFalse(
            defaultToolCallCollapsed(
                tool(status = "completed", fileDiffs = oneFileDiff),
                showLiveToolDiffs = true,
            ),
        )
        assertFalse(
            defaultToolCallCollapsed(
                tool(status = "failed", fileDiffs = oneFileDiff),
                showLiveToolDiffs = true,
            ),
        )
    }

    @Test
    fun completedFileToolCollapsedWhenLiveDiffsOff() {
        assertTrue(
            defaultToolCallCollapsed(
                tool(status = "completed", fileDiffs = oneFileDiff),
                showLiveToolDiffs = false,
            ),
        )
    }

    @Test
    fun completedNonFileToolCollapsedRegardlessOfSetting() {
        assertTrue(defaultToolCallCollapsed(tool(status = "completed"), showLiveToolDiffs = true))
        assertTrue(defaultToolCallCollapsed(tool(status = "completed"), showLiveToolDiffs = false))
    }

    @Test
    fun syncExpandsWhenFileDiffsArriveLate() {
        val emptyDiff = listOf(tool(id = 1L, status = "completed", fileDiffs = emptyList()))
        val map = mutableMapOf<Long, Boolean>()
        syncCompletedToolCollapse(emptyDiff, map, showLiveToolDiffs = true)
        assertEquals(true, map[1L])
        val withDiff = listOf(tool(id = 1L, status = "completed", fileDiffs = oneFileDiff))
        syncCompletedToolCollapse(withDiff, map, showLiveToolDiffs = true)
        assertEquals(false, map[1L])
    }

    @Test
    fun syncRespectsUserToggledToolIds() {
        val items = listOf(tool(id = 1L, status = "completed", fileDiffs = oneFileDiff))
        val map = mutableMapOf(1L to true)
        syncCompletedToolCollapse(items, map, showLiveToolDiffs = true, userToggledToolIds = setOf(1L))
        assertEquals(true, map[1L])
    }

    @Test
    fun syncAppliesDefaultForNewTools() {
        val items = listOf(tool(id = 2L, status = "completed", fileDiffs = oneFileDiff))
        val map = mutableMapOf<Long, Boolean>()
        syncCompletedToolCollapse(items, map, showLiveToolDiffs = true)
        assertEquals(false, map[2L])
    }
}
