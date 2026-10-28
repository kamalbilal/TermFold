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
            1L to AskQuestionTurnSnapshot("t1", null, emptyList(), AskQuestionPhase.Answered),
            99L to AskQuestionTurnSnapshot("t2", null, emptyList(), AskQuestionPhase.Skipped),
        )
        assertEquals(1, askQuestionsPrunedToTimeline(items, map).size)
    }

    @Test
    fun scrubStalePendingWithoutLiveRpc() {
        val pending = AskQuestionTurnSnapshot("t", null, emptyList(), AskQuestionPhase.Pending)
        val map = mapOf(1L to pending, 2L to pending.copy(toolCallId = "t2"))
        val scrubbed = askQuestionsScrubStalePending(map, pending = null)
        assertEquals(AskQuestionPhase.Cancelled, scrubbed[1L]!!.phase)
        assertEquals(AskQuestionPhase.Cancelled, scrubbed[2L]!!.phase)
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
        val encoded = AskQuestionSnapshotCodec.encodeMap(mapOf(1_000_001L to snapshot))
        val decoded = AskQuestionSnapshotCodec.decodeMap(encoded)
        assertEquals(snapshot.copy(), decoded[1_000_001L])
    }

    @Test
    fun pendingPhaseNotPersistedOnDecode() {
        val snapshot = AskQuestionTurnSnapshot("c", null, emptyList(), AskQuestionPhase.Pending)
        val json = AskQuestionSnapshotCodec.encodeMap(mapOf(1L to snapshot))
        assertTrue(AskQuestionSnapshotCodec.decodeMap(json).isEmpty())
    }
}
