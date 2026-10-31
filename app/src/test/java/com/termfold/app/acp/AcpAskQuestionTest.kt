package com.termfold.app.acp

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AcpAskQuestionParserTest {

    @Test
    fun parseRequestAcceptsAllowMultipleSnakeCase() {
        val params = JSONObject(
            """
            {
              "toolCallId": "call_1",
              "title": "Need input",
              "questions": [{
                "id": "q1",
                "prompt": "Pick one?",
                "allow_multiple": true,
                "options": [
                  {"id": "a", "label": "Alpha"},
                  {"id": "b", "label": "Beta"}
                ]
              }]
            }
            """.trimIndent(),
        )
        val parsed = AcpAskQuestionParser.parseRequest(params)
        assertNotNull(parsed)
        assertTrue(parsed!!.questions.single().allowMultiple)
    }

    @Test
    fun parseRequestGeneratesToolCallIdWhenAllowed() {
        val params = JSONObject(
            """
            {
              "questions": [{
                "id": "q1",
                "prompt": "Pick?",
                "options": [{"id": "a", "label": "A"}]
              }]
            }
            """.trimIndent(),
        )
        val parsed = AcpAskQuestionParser.parseRequest(params, allowGeneratedToolCallId = true)
        assertNotNull(parsed)
        assertTrue(parsed!!.toolCallId.startsWith("ask_"))
    }

    @Test
    fun invalidPayloadReturnsNull() {
        assertNull(AcpAskQuestionParser.parseRequest(JSONObject().put("toolCallId", "")))
    }

    @Test
    fun answeredResultMatchesCursorShape() {
        val result = AcpAskQuestionParser.buildAnsweredResult(
            listOf(AskQuestionAnswer("q1", listOf("a"))),
        )
        val outcome = result.getJSONObject("outcome")
        assertEquals("answered", outcome.getString("outcome"))
        assertEquals("q1", outcome.getJSONArray("answers").getJSONObject(0).getString("questionId"))
    }

    @Test
    fun skippedResultUsesCursorReason() {
        val outcome = AcpAskQuestionParser.buildSkippedResult().getJSONObject("outcome")
        assertEquals("skipped", outcome.getString("outcome"))
        assertEquals(CURSOR_ASK_SKIPPED_REASON, outcome.getString("reason"))
    }
}

class AskQuestionTimelinePruneTest {

    @Test
    fun pruneDropsOrphanUserTurns() {
        val items = listOf(
            AcpItem.UserMessage(1L, "a", emptyList()),
            AcpItem.AgentText(2L, "reply"),
        )
        val map = mapOf(
            1L to listOf(AskQuestionTurnSnapshot("t1", null, emptyList(), AskQuestionPhase.Answered)),
            99L to listOf(AskQuestionTurnSnapshot("t2", null, emptyList(), AskQuestionPhase.Skipped)),
        )
        assertEquals(1, askQuestionsPrunedToTimeline(items, map).size)
    }

    @Test
    fun scrubStalePendingWithoutLiveRpc() {
        val pendingSnap = AskQuestionTurnSnapshot("t", null, emptyList(), AskQuestionPhase.Pending)
        val map = mapOf(
            1L to listOf(pendingSnap, pendingSnap.copy(toolCallId = "t2")),
        )
        val scrubbed = askQuestionsScrubStalePending(map, pending = null)
        assertEquals(AskQuestionPhase.Cancelled, scrubbed[1L]!![0].phase)
        assertEquals(AskQuestionPhase.Cancelled, scrubbed[1L]!![1].phase)
    }
    @Test
    fun appendKeepsPreviousAnsweredCardsOnSameTurn() {
        val first = AskQuestionTurnSnapshot("t1", "Step 1", emptyList(), AskQuestionPhase.Answered)
        val second = AskQuestionTurnSnapshot("t2", "Step 2", emptyList(), AskQuestionPhase.Pending)
        val map = appendAskSnapshotForTurn(
            appendAskSnapshotForTurn(emptyMap(), 1L, first),
            1L,
            second,
        )
        assertEquals(2, map[1L]?.size)
        assertEquals("t1", map[1L]!![0].toolCallId)
        assertEquals("t2", map[1L]!![1].toolCallId)
    }
}

class AskQuestionAnswersValidTest {

    @Test
    fun rejectsIncompleteAnswers() {
        val questions = listOf(
            AskQuestionSpec("q1", "One?", listOf(AskQuestionOption("a", "A")), false),
            AskQuestionSpec("q2", "Two?", listOf(AskQuestionOption("b", "B")), false),
        )
        assertFalse(
            askQuestionAnswersValid(
                questions,
                listOf(AskQuestionAnswer("q1", listOf("a"))),
            ),
        )
    }

    @Test
    fun freeformRequiresText() {
        val questions = listOf(
            AskQuestionSpec("q1", "One?", listOf(AskQuestionOption("a", "A")), false),
        )
        assertFalse(
            askQuestionAnswersValid(
                questions,
                listOf(AskQuestionAnswer("q1", listOf(CURSOR_ASK_FREEFORM_OPTION_ID))),
            ),
        )
        assertTrue(
            askQuestionAnswersValid(
                questions,
                listOf(
                    AskQuestionAnswer(
                        "q1",
                        listOf(CURSOR_ASK_FREEFORM_OPTION_ID),
                        freeformText = "custom",
                    ),
                ),
            ),
        )
    }

    @Test
    fun acceptsPerQuestionSkip() {
        val questions = listOf(
            AskQuestionSpec("q1", "One?", listOf(AskQuestionOption("a", "A")), false),
            AskQuestionSpec("q2", "Two?", listOf(AskQuestionOption("b", "B")), false),
        )
        assertTrue(
            askQuestionAnswersValid(
                questions,
                listOf(
                    AskQuestionAnswer("q1", listOf("a")),
                    AskQuestionAnswer("q2", emptyList(), skipped = true),
                ),
            ),
        )
    }
}

class AskQuestionSnapshotCodecTest {

    @Test
    fun roundTripTerminalSnapshot() {
        val snapshot = AskQuestionTurnSnapshot(
            toolCallId = "c1",
            title = "Title",
            questions = listOf(
                AskQuestionSpec(
                    id = "q1",
                    prompt = "Which?",
                    options = listOf(AskQuestionOption("a", "A")),
                    allowMultiple = false,
                ),
            ),
            phase = AskQuestionPhase.Answered,
            answers = listOf(AskQuestionAnswer("q1", listOf("a"))),
        )
        val encoded = AskQuestionSnapshotCodec.encodeMap(mapOf(1_000_001L to listOf(snapshot)))
        val decoded = AskQuestionSnapshotCodec.decodeMap(encoded)
        assertEquals(snapshot, decoded[1_000_001L]?.single())
    }

    @Test
    fun pendingPhaseNotPersistedOnDecode() {
        val snapshot = AskQuestionTurnSnapshot("c", null, emptyList(), AskQuestionPhase.Pending)
        val json = AskQuestionSnapshotCodec.encodeMap(mapOf(1L to listOf(snapshot)))
        assertTrue(AskQuestionSnapshotCodec.decodeMap(json).isEmpty())
    }
}
