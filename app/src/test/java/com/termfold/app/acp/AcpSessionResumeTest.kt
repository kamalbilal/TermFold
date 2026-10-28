package com.termfold.app.acp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun priorWireStaysVisibleAfterNewSessionWhenNotHidden() {
        val listed = listOf(
            AcpPastSession("wire-old", "Code spacing", "2026-01-01"),
            AcpPastSession("wire-new", "New chat", "2026-01-02"),
        )
        val visible = AcpPastSessions.visible(listed, hiddenIds = emptySet())
        assertEquals(listOf("wire-old", "wire-new"), visible.map { it.sessionId })
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

class AcpConversationWiresTest {

    @Test
    fun bindKeepsListedIdAndRotatesActiveWire() {
        val map = AcpConversationWires.bind(emptyMap(), listed = "listed-1", active = "listed-1")
        val afterCompress = AcpConversationWires.bind(map, listed = "listed-1", active = "hidden-2")
        assertEquals("hidden-2", AcpConversationWires.activeFor(afterCompress, "listed-1"))
        assertEquals("listed-2", AcpConversationWires.activeFor(afterCompress, "listed-2"))
        val encoded = AcpConversationWires.encode(afterCompress)
        assertEquals(afterCompress, AcpConversationWires.parse(encoded))
    }

    @Test
    fun withoutRemovesOneConversation() {
        val map = AcpConversationWires.bind(
            AcpConversationWires.bind(emptyMap(), "a", "a-active"),
            "b",
            "b",
        )
        assertEquals(mapOf("b" to "b"), AcpConversationWires.without(map, "a"))
    }
}

class AcpTimelineCodecTest {

    @Test
    fun roundTripKeepsAskQuestionMap() {
        val snapshot = AskQuestionTurnSnapshot(
            toolCallId = "tc",
            title = "T",
            questions = listOf(
                AskQuestionSpec("q", "Q?", listOf(AskQuestionOption("a", "A")), false),
            ),
            phase = AskQuestionPhase.Answered,
            answers = listOf(AskQuestionAnswer("q", listOf("a"))),
        )
        val timeline = AcpPersistedTimeline(
            items = listOf(AcpItem.UserMessage(1_000_001, "hi", emptyList())),
            askQuestionsByUserMessageId = mapOf(1_000_001L to snapshot),
        )
        val restored = AcpTimelineCodec.decode(AcpTimelineCodec.encode(timeline))
        assertEquals(snapshot, restored.askQuestionsByUserMessageId[1_000_001L])
    }

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
    fun roundTripPreservesCompressedForUserMessageIdAndCheckpointMaps() {
        val items = listOf(
            AcpItem.UserMessage(1_000_001, "first", emptyList()),
            AcpItem.UserMessage(1_000_002, "/summarize", emptyList()),
            AcpItem.AgentText(1_000_003, "summary body"),
        )
        val snapshot = AcpPersistedTimeline(
            items = items,
            checkpoints = com.termfold.app.acp.checkpoints.AcpCheckpointUiState(
                preTurnByMessageId = mapOf(1_000_001L to "abc123abc123abc123abc123abc123abc123abc1"),
                undoableMessageIds = setOf(1_000_001L),
                compressedForUserMessageId = 1_000_002L,
            ),
            pendingWireContext = "Session context (compressed from /summarize):\n\nsummary body",
        )
        val restored = AcpTimelineCodec.decode(AcpTimelineCodec.encode(snapshot))
        assertEquals(items, restored.items)
        assertEquals(1_000_002L, restored.checkpoints.compressedForUserMessageId)
        assertEquals(mapOf(1_000_001L to "abc123abc123abc123abc123abc123abc123abc1"), restored.checkpoints.preTurnByMessageId)
        assertEquals(setOf(1_000_001L), restored.checkpoints.undoableMessageIds)
        assertEquals("Session context (compressed from /summarize):\n\nsummary body", restored.pendingWireContext)
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

    @Test
    fun roundTripToolCallFileDiffs() {
        val items = listOf(
            AcpItem.ToolCall(
                id = 1_000_001,
                toolCallId = "tc-1",
                title = "Write",
                kind = "edit",
                status = "completed",
                detail = "",
                paths = listOf("/proj/a.kt"),
                fileDiffs = listOf(
                    AcpToolFileDiff(
                        path = "/proj/a.kt",
                        operation = AcpToolFileDiff.Operation.MODIFY,
                        patchText = "--- /proj/a.kt\n+++ /proj/a.kt\n@@ -1 +1 @@\n-x\n+y",
                    ),
                ),
            ),
        )
        val restored = AcpTimelineCodec.decode(AcpTimelineCodec.encode(items))
        assertEquals(items, restored.items)
    }

    @Test
    fun legacyToolWithoutFileDiffsDecodes() {
        val json = """{"items":[{"type":"tool","id":1000001,"toolCallId":"x","title":"Read","kind":"read","status":"completed","detail":"ok","paths":[]}]}"""
        val item = AcpTimelineCodec.decode(json).items.single() as AcpItem.ToolCall
        assertEquals("ok", item.detail)
        assertTrue(item.fileDiffs.isEmpty())
    }
}
