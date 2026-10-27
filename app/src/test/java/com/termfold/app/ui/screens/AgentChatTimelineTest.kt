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

    private fun itemsWithUserThenTools(vararg tools: AcpItem.ToolCall): List<AcpItem> =
        listOf(AcpItem.UserMessage(10L, "go", emptyList())) + tools

    @Test
    fun inProgressDefaultsExpanded() {
        val items = itemsWithUserThenTools(tool(status = "in_progress"))
        assertFalse(defaultToolCallCollapsed(tool(status = "in_progress"), items, showLiveToolDiffs = true, agentBusy = true))
        assertFalse(defaultToolCallCollapsed(tool(status = "in_progress"), items, showLiveToolDiffs = false, agentBusy = true))
    }

    @Test
    fun liveDiffsExpandOnlyLatestTurnWhenIdle() {
        val items = itemsWithUserThenTools(
            tool(id = 1L, status = "completed", fileDiffs = oneFileDiff),
        )
        assertFalse(
            defaultToolCallCollapsed(
                tool(id = 1L, status = "completed", fileDiffs = oneFileDiff),
                items,
                showLiveToolDiffs = true,
                agentBusy = false,
            ),
        )
        val olderTurn = listOf(
            AcpItem.UserMessage(1L, "old", emptyList()),
            tool(id = 2L, status = "completed", fileDiffs = oneFileDiff),
            AcpItem.UserMessage(10L, "new", emptyList()),
            tool(id = 3L, status = "completed", fileDiffs = oneFileDiff),
        )
        assertTrue(
            defaultToolCallCollapsed(
                tool(id = 2L, status = "completed", fileDiffs = oneFileDiff),
                olderTurn,
                showLiveToolDiffs = true,
                agentBusy = false,
            ),
        )
        assertFalse(
            defaultToolCallCollapsed(
                tool(id = 3L, status = "completed", fileDiffs = oneFileDiff),
                olderTurn,
                showLiveToolDiffs = true,
                agentBusy = false,
            ),
        )
    }

    @Test
    fun liveDiffsCollapseCompletedToolsDuringBusyTurn() {
        val items = itemsWithUserThenTools(
            tool(id = 1L, status = "completed", fileDiffs = oneFileDiff),
            tool(id = 2L, status = "in_progress"),
        )
        assertTrue(
            defaultToolCallCollapsed(
                tool(id = 1L, status = "completed", fileDiffs = oneFileDiff),
                items,
                showLiveToolDiffs = true,
                agentBusy = true,
            ),
        )
        assertFalse(
            defaultToolCallCollapsed(
                tool(id = 2L, status = "in_progress"),
                items,
                showLiveToolDiffs = true,
                agentBusy = true,
            ),
        )
    }

    @Test
    fun completedFileToolCollapsedWhenLiveDiffsOff() {
        val items = itemsWithUserThenTools(tool(status = "completed", fileDiffs = oneFileDiff))
        assertTrue(
            defaultToolCallCollapsed(
                tool(status = "completed", fileDiffs = oneFileDiff),
                items,
                showLiveToolDiffs = false,
                agentBusy = false,
            ),
        )
    }

    @Test
    fun completedNonFileToolCollapsedRegardlessOfSetting() {
        val items = itemsWithUserThenTools(tool(status = "completed"))
        assertTrue(defaultToolCallCollapsed(tool(status = "completed"), items, showLiveToolDiffs = true, agentBusy = false))
        assertTrue(defaultToolCallCollapsed(tool(status = "completed"), items, showLiveToolDiffs = false, agentBusy = false))
    }

    @Test
    fun syncExpandsLatestTurnWhenFileDiffsArriveLate() {
        val emptyDiff = listOf(
            AcpItem.UserMessage(10L, "x", emptyList()),
            tool(id = 1L, status = "completed", fileDiffs = emptyList()),
        )
        val map = mutableMapOf<Long, Boolean>()
        syncCompletedToolCollapse(emptyDiff, map, showLiveToolDiffs = true, agentBusy = false)
        assertEquals(true, map[1L])
        val withDiff = listOf(
            AcpItem.UserMessage(10L, "x", emptyList()),
            tool(id = 1L, status = "completed", fileDiffs = oneFileDiff),
        )
        syncCompletedToolCollapse(withDiff, map, showLiveToolDiffs = true, agentBusy = false)
        assertEquals(false, map[1L])
    }

    @Test
    fun syncRespectsUserToggledToolIds() {
        val items = itemsWithUserThenTools(tool(id = 1L, status = "completed", fileDiffs = oneFileDiff))
        val map = mutableMapOf(1L to true)
        syncCompletedToolCollapse(items, map, showLiveToolDiffs = true, agentBusy = false, userToggledToolIds = setOf(1L))
        assertEquals(true, map[1L])
    }

    @Test
    fun buildMapExpandsLatestTurnWhenFileDiffsArriveLate() {
        val empty = listOf(
            AcpItem.UserMessage(10L, "x", emptyList()),
            tool(id = 1L, status = "completed", fileDiffs = emptyList()),
        )
        assertEquals(true, buildToolCallCollapsedMap(empty, showLiveToolDiffs = true, agentBusy = false, emptySet())[1L])
        val withDiff = listOf(
            AcpItem.UserMessage(10L, "x", emptyList()),
            tool(id = 1L, status = "completed", fileDiffs = oneFileDiff),
        )
        assertEquals(false, buildToolCallCollapsedMap(withDiff, showLiveToolDiffs = true, agentBusy = false, emptySet())[1L])
    }

    @Test
    fun turnFootersPlacedAfterEachUserTurnNotOnlyAtEnd() {
        val items = listOf(
            AcpItem.UserMessage(1L, "first", emptyList()),
            AcpItem.AgentText(2L, "reply one"),
            AcpItem.UserMessage(3L, "second", emptyList()),
            AcpItem.AgentText(4L, "reply two"),
        )
        val flat = buildAgentChatTimelineEntries(
            items = items,
            collapsedTools = emptyMap(),
            expandedToolFiles = emptyMap(),
            contextFolds = emptyMap(),
            workspaceDir = "/root/ws",
            showLiveToolDiffs = false,
            presentationCache = emptyMap(),
            turnFooters = false,
        )
        assertTrue(flat.none { it is AgentChatLazyEntry.TurnFooter })

        val withFooters = buildAgentChatTimelineEntries(
            items = items,
            collapsedTools = emptyMap(),
            expandedToolFiles = emptyMap(),
            contextFolds = emptyMap(),
            workspaceDir = "/root/ws",
            showLiveToolDiffs = false,
            presentationCache = emptyMap(),
            turnFooters = true,
        )
        val footerIds = withFooters.filterIsInstance<AgentChatLazyEntry.TurnFooter>().map { it.userMessageId }
        assertEquals(listOf(1L, 3L), footerIds)
        val firstFooterIndex = withFooters.indexOfFirst { it is AgentChatLazyEntry.TurnFooter && it.userMessageId == 1L }
        val secondUserIndex = withFooters.indexOfFirst {
            it is AgentChatLazyEntry.Standard && it.item is AcpItem.UserMessage && it.item.id == 3L
        }
        assertTrue(firstFooterIndex >= 0)
        assertTrue(secondUserIndex > firstFooterIndex)
    }

    @Test
    fun agentChatItemsForUserTurnSlicesOneTurn() {
        val items = listOf(
            AcpItem.UserMessage(1L, "a", emptyList()),
            AcpItem.AgentText(2L, "x"),
            AcpItem.UserMessage(3L, "b", emptyList()),
            AcpItem.AgentText(4L, "y"),
        )
        assertEquals(listOf(1L, 2L), agentChatItemsForUserTurn(items, 1L).map { it.id })
        assertEquals(listOf(3L, 4L), agentChatItemsForUserTurn(items, 3L).map { it.id })
    }

    @Test
    fun buildMapRespectsUserOverride() {
        val items = itemsWithUserThenTools(tool(id = 1L, status = "completed", fileDiffs = oneFileDiff))
        val map = buildToolCallCollapsedMap(
            items,
            showLiveToolDiffs = true,
            agentBusy = false,
            userToggledToolIds = setOf(1L),
            userCollapsedOverrides = mapOf(1L to true),
        )
        assertEquals(true, map[1L])
    }
}
