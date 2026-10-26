package com.termfold.app.prreview

import com.termfold.app.acp.AcpItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    @Test
    fun validReportSavedAndClearsError() {
        val json = """{"schemaVersion":1,"summary":"ok","risk":"low","findings":[],"coverage":{"reviewed":[],"skipped":[]}}"""
        val items = listOf(
            AcpItem.UserMessage(1, "go", emptyList()),
            AcpItem.AgentText(2, "```pr-review-report\n$json\n```"),
        )
        val next = PrReviewTurnSync.apply(session.copy(lastParseError = "missing"), PrReviewReportParser.parseTurn(items))
        assertNull(next.lastParseError)
        assertNotNull(next.lastReportJson)
        val stored = PrReviewReportParser.decodeStored(org.json.JSONObject(next.lastReportJson!!))
        assertEquals("ok", stored?.summary)
    }

    @Test
    fun missingKeepsChatReportAndSetsError() {
        val items = listOf(
            AcpItem.UserMessage(1, "go", emptyList()),
            AcpItem.AgentText(2, "no fence here"),
        )
        val next = PrReviewTurnSync.apply(session, PrReviewReportParser.parseTurn(items))
        assertEquals("missing", next.lastParseError)
        assertEquals("""{"keep":true}""", next.lastReportJson)
    }

    @Test
    fun skippedLeavesSessionUnchanged() {
        val items = listOf(AcpItem.AgentText(1, "hello"))
        val next = PrReviewTurnSync.apply(session.copy(lastParseError = null), PrReviewReportParser.parseTurn(items))
        assertNull(next.lastParseError)
        assertEquals("""{"keep":true}""", next.lastReportJson)
    }
}
