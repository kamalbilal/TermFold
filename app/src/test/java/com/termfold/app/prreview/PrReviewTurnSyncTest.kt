package com.termfold.app.prreview

import com.termfold.app.acp.AcpItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrReviewTurnSyncTest {
    private val session = PrReviewSession(
        id = "s",
        name = "n",
        url = "https://github.com/o/r/pull/1",
        owner = "o",
        repo = "r",
        number = 1,
        headBranch = "b",
        folderId = "f",
        lastReportJson = """{"keep":true}""",
    )

    private val validJson =
        """{"schemaVersion":1,"summary":"ok","risk":"low","findings":[],"coverage":{"reviewed":[],"skipped":[]},"missingTests":[],"openQuestions":[]}"""

    @Test
    fun validReportSavedAndClearsError() {
        val items = listOf(
            AcpItem.UserMessage(1, "go", emptyList()),
            AcpItem.AgentText(2, "```pr-review-report\n$validJson\n```"),
        )
        val next = PrReviewTurnSync.apply(
            session.copy(lastParseError = "missing"),
            PrReviewReportParser.parseTurn(items),
            userMessageId = 1L,
        )
        assertNull(next.lastParseError)
        assertNotNull(next.lastReportJson)
        val stored = PrReviewReportParser.decodeStored(org.json.JSONObject(next.lastReportJson!!))
        assertEquals("ok", stored?.summary)
        assertEquals(next.lastReportJson, next.reportsByUserMessageId[1L])
    }

    @Test
    fun okUpsertsMapWithoutDroppingPriorTurn() {
        val jsonTwo = validJson.replace("ok", "two")
        val turnOne = listOf(
            AcpItem.UserMessage(1, "first", emptyList()),
            AcpItem.AgentText(2, "```pr-review-report\n$validJson\n```"),
        )
        val turnTwo = listOf(
            AcpItem.UserMessage(3, "second", emptyList()),
            AcpItem.AgentText(4, "```pr-review-report\n$jsonTwo\n```"),
        )
        val first = PrReviewTurnSync.apply(
            session,
            PrReviewReportParser.parseTurnSlice(turnOne, turnFinished = true),
            userMessageId = 1L,
        )
        val second = PrReviewTurnSync.apply(
            first,
            PrReviewReportParser.parseTurnSlice(turnTwo, turnFinished = true),
            userMessageId = 3L,
        )
        assertEquals("ok", PrReviewReportParser.decodeStored(org.json.JSONObject(second.reportsByUserMessageId[1L]!!))?.summary)
        assertEquals("two", PrReviewReportParser.decodeStored(org.json.JSONObject(second.reportsByUserMessageId[3L]!!))?.summary)
    }

    @Test
    fun missingKeepsChatReportAndSetsError() {
        val items = listOf(
            AcpItem.UserMessage(1, "go", emptyList()),
            AcpItem.AgentText(2, "no fence here"),
        )
        val next = PrReviewTurnSync.apply(session, PrReviewReportParser.parseTurn(items), userMessageId = 1L)
        assertEquals("missing", next.lastParseError)
        assertEquals("""{"keep":true}""", next.lastReportJson)
        assertTrue(next.reportsByUserMessageId.isEmpty())
    }

    @Test
    fun skippedLeavesSessionUnchanged() {
        val items = listOf(AcpItem.AgentText(1, "hello"))
        val next = PrReviewTurnSync.apply(
            session.copy(lastParseError = null),
            PrReviewReportParser.parseTurn(items),
            userMessageId = 1L,
        )
        assertNull(next.lastParseError)
        assertEquals("""{"keep":true}""", next.lastReportJson)
    }

    @Test
    fun realignPrunesOrphanTurnAndRefreshesLastReportJson() {
        val jsonOne = validJson
        val jsonTwo = validJson.replace("ok", "two")
        val withTwo = session.copy(
            reportsByUserMessageId = mapOf(1L to jsonOne, 3L to jsonTwo),
            lastReportJson = jsonTwo,
        )
        val items = listOf(
            AcpItem.UserMessage(1, "a", emptyList()),
            AcpItem.AgentText(2, "```pr-review-report\n$jsonOne\n```"),
        )
        val next = PrReviewTurnSync.realignToTimeline(withTwo, items)
        assertEquals(mapOf(1L to jsonOne), next.reportsByUserMessageId)
        assertEquals(jsonOne, next.lastReportJson)
    }
}
