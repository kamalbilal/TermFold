package com.termfold.app.acp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AcpSessionResumeTest {

    @Test
    fun revertResyncKeepsLocalTimelineAndDoesNotReplayAgentHistory() {
        val plan = AcpSessionResume.plan(
            hasLocalTimeline = true,
            rememberedWireId = "wire-new",
            canReopen = true,
        )
        assertTrue(plan.useRememberedWireSession)
        assertTrue(plan.keepLocalTimeline)
        assertFalse(plan.replayAgentHistory)
        assertTrue(plan.suppressAgentReplayUpdates)
    }

    @Test
    fun firstOpenWithNoLocalTimelineReplaysAgentHistory() {
        val plan = AcpSessionResume.plan(
            hasLocalTimeline = false,
            rememberedWireId = "wire-original",
            canReopen = true,
        )
        assertTrue(plan.useRememberedWireSession)
        assertFalse(plan.keepLocalTimeline)
        assertTrue(plan.replayAgentHistory)
        assertFalse(plan.suppressAgentReplayUpdates)
    }

    @Test
    fun noRememberedSessionOpensFresh() {
        val plan = AcpSessionResume.plan(
            hasLocalTimeline = true,
            rememberedWireId = null,
            canReopen = true,
        )
        assertFalse(plan.useRememberedWireSession)
        assertTrue(plan.keepLocalTimeline)
        assertFalse(plan.replayAgentHistory)
    }
}

class AcpPastSessionVisibilityTest {

    @Test
    fun hiddenWireSessionsDoNotAppearInHistoryList() {
        val listed = listOf(
            AcpPastSession("original", "fix login", "2026-01-01"),
            AcpPastSession("resync", "Session context (summarized", "2026-01-02"),
            AcpPastSession("other", "other chat", "2026-01-03"),
        )
        val visible = AcpPastSessions.visible(
            listed,
            hiddenIds = setOf("original", "resync"),
        )
        assertEquals(listOf("other"), visible.map { it.sessionId })
    }

    @Test
    fun perTabDeletedKeysDifferForSameWorkspace() {
        val workspace = "/guest/proj"
        val tabA = AcpPastSessions.deletedKey("cursor", workspace, "f/w/s1")
        val tabB = AcpPastSessions.deletedKey("cursor", workspace, "f/w/s2")
        assertEquals(AcpPastSessions.legacyDeletedKey("cursor", workspace), "cursor|$workspace")
        assertTrue(tabA != tabB)
        assertTrue(tabA.endsWith("|f/w/s1"))
    }

    @Test
    fun migrateHiddenIdsOnlyFirstTabGetsLegacy() {
        val legacy = setOf("wire-old", "wire-clone")
        val (first, doneFirst) = AcpPastSessions.migrateHiddenIds(null, legacy, false)
        assertEquals(legacy, first)
        assertTrue(doneFirst)

        val (second, doneSecond) = AcpPastSessions.migrateHiddenIds(null, legacy, true)
        assertEquals(emptySet<String>(), second)
        assertTrue(doneSecond)
    }

    @Test
    fun mergeListedDedupesAcrossPages() {
        val page1 = listOf(
            AcpPastSession("a", "one", ""),
            AcpPastSession("b", "two", ""),
        )
        val page2 = listOf(
            AcpPastSession("b", "two again", ""),
            AcpPastSession("c", "three", ""),
        )
        assertEquals(
            listOf("a", "b", "c"),
            AcpPastSessions.mergeListed(listOf(page1, page2)).map { it.sessionId },
        )
    }

    @Test
    fun wireSessionToHideBeforeNewSession() {
        assertEquals("wire-1", AcpPastSessions.wireSessionToHideBeforeNew("wire-1"))
        assertEquals(null, AcpPastSessions.wireSessionToHideBeforeNew(null))
        assertEquals(null, AcpPastSessions.wireSessionToHideBeforeNew(""))
    }

    @Test
    fun perTabHiddenSetsAreIndependent() {
        val sessions = listOf(
            AcpPastSession("shared", "title", ""),
            AcpPastSession("only-b", "b", ""),
        )
        val tabAHidden = setOf("shared")
        val tabBHidden = emptySet<String>()
        assertEquals(listOf("only-b"), AcpPastSessions.visible(sessions, tabAHidden).map { it.sessionId })
        assertEquals(listOf("shared", "only-b"), AcpPastSessions.visible(sessions, tabBHidden).map { it.sessionId })
    }
}

class AcpTimelineCodecTest {

    @Test
    fun roundTripKeepsUserAndAgentTurns() {
        val items = listOf(
            AcpItem.UserMessage(1_000_001, "first", emptyList()),
            AcpItem.AgentText(1_000_002, "reply one"),
            AcpItem.UserMessage(1_000_003, "edited", emptyList()),
        )
        val json = AcpTimelineCodec.encode(items)
        val restored = AcpTimelineCodec.decode(json)
        assertEquals(items, restored.items)
        assertEquals(emptySet<Long>(), restored.checkpoints.undoableMessageIds)
        assertEquals(null, restored.checkpoints.ghostFromItemId)
        assertTrue(restored.checkpoints.redoStack.isEmpty())
    }

    @Test
    fun roundTripKeepsCheckpointMapsGhostAndRedoFrame() {
        val items = listOf(
            AcpItem.UserMessage(1_000_001, "first", emptyList()),
            AcpItem.AgentText(1_000_002, "reply one"),
            AcpItem.UserMessage(1_000_010, "ghost", emptyList()),
        )
        val frameItems = items + AcpItem.AgentText(1_000_011, "later")
        val snapshot = AcpPersistedTimeline(
            items = items,
            checkpoints = com.termfold.app.acp.checkpoints.AcpCheckpointUiState(
                preTurnByMessageId = mapOf(1_000_001L to "abc123abc123abc123abc123abc123abc123abc1"),
                undoableMessageIds = setOf(1_000_001L),
                ghostFromItemId = 1_000_010L,
                redoStack = listOf(
                    com.termfold.app.acp.checkpoints.AcpRedoFrame(
                        items = frameItems,
                        ghostFromItemId = null,
                        preTurnByMessageId = mapOf(1_000_001L to "abc123abc123abc123abc123abc123abc123abc1"),
                        undoableMessageIds = setOf(1_000_001L),
                        worktreeTipCommit = "def456def456def456def456def456def456def4",
                        wireSessionId = "wire-old",
                    ),
                ),
                wireResyncing = true,
                busy = com.termfold.app.acp.checkpoints.CheckpointBusy.Undo(1_000_001L),
            ),
        )
        val restored = AcpTimelineCodec.decode(AcpTimelineCodec.encode(snapshot))
        assertEquals(items, restored.items)
        assertEquals(mapOf(1_000_001L to "abc123abc123abc123abc123abc123abc123abc1"), restored.checkpoints.preTurnByMessageId)
        assertEquals(setOf(1_000_001L), restored.checkpoints.undoableMessageIds)
        assertEquals(1_000_010L, restored.checkpoints.ghostFromItemId)
        assertEquals(1, restored.checkpoints.redoStack.size)
        assertEquals(frameItems, restored.checkpoints.redoStack[0].items)
        assertEquals("wire-old", restored.checkpoints.redoStack[0].wireSessionId)
        assertEquals("def456def456def456def456def456def456def4", restored.checkpoints.redoStack[0].worktreeTipCommit)
        assertFalse(restored.checkpoints.wireResyncing)
        assertEquals(null, restored.checkpoints.busy)
    }

    @Test
    fun itemsOnlyJsonDecodesEmptyCheckpoints() {
        val json = """{"items":[{"type":"user","id":1000001,"text":"hi","images":[],"files":[]}]}"""
        val restored = AcpTimelineCodec.decode(json)
        assertEquals(1, restored.items.size)
        assertEquals(emptySet<Long>(), restored.checkpoints.undoableMessageIds)
        assertEquals(null, restored.checkpoints.ghostFromItemId)
        assertTrue(restored.checkpoints.redoStack.isEmpty())
    }

    @Test
    fun nextIdContinuesAfterRestoredTimeline() {
        assertEquals(43L, AcpTimelineCodec.nextCounter(maxItemId = 1_000_042))
    }
}
