package com.termfold.app.prreview

import com.termfold.app.acp.AcpItem
import com.termfold.app.ui.screens.prReviewReportFromSessionJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrReviewTurnReportsTest {
    private val validJson =
        """{"schemaVersion":1,"summary":"ok","risk":"low","findings":[],"coverage":{"reviewed":[],"skipped":[]},"missingTests":[],"openQuestions":[]}"""

    private val turnOneItems = listOf(
        AcpItem.UserMessage(1, "first", emptyList()),
        AcpItem.AgentText(2, "```pr-review-report\n$validJson\n```"),
    )

    @Test
    fun jsonForTurnUsesMapBeforeLiveParse() {
        val stored = """{"schemaVersion":1,"summary":"stored","risk":"low","findings":[],"coverage":{"reviewed":[],"skipped":[]},"missingTests":[],"openQuestions":[]}"""
        val json = prReviewJsonForTurn(
            userMessageId = 1L,
            turnItems = turnOneItems,
            turnFinished = true,
            reportsByUserMessageId = mapOf(1L to stored),
            lastUserMessageId = 3L,
            sessionLastReportJson = null,
        )
        assertEquals(stored, json)
    }

    @Test
    fun jsonForTurnKeepsFirstTurnAfterSecondUserMessage() {
        val stored = PrReviewReportParser.encode(
            (PrReviewReportParser.parseTurnSlice(turnOneItems, turnFinished = true) as PrReviewParse.Ok).report,
        ).toString()
        val turnTwoOnlyUser = listOf(
            AcpItem.UserMessage(3, "second", emptyList()),
        )
        val json = prReviewJsonForTurn(
            userMessageId = 1L,
            turnItems = turnOneItems,
            turnFinished = true,
            reportsByUserMessageId = mapOf(1L to stored),
            lastUserMessageId = 3L,
            sessionLastReportJson = null,
        )
        assertEquals(stored, json)
        assertNull(
            prReviewJsonForTurn(
                userMessageId = 3L,
                turnItems = turnTwoOnlyUser,
                turnFinished = false,
                reportsByUserMessageId = mapOf(1L to stored),
                lastUserMessageId = 3L,
                sessionLastReportJson = stored,
            ),
        )
    }

    @Test
    fun chromeSkippedWithoutMapOrFallback() {
        val turnTwoStreaming = listOf(
            AcpItem.UserMessage(3, "second", emptyList()),
            AcpItem.AgentText(4, "working"),
        )
        val parse = PrReviewReportParser.parseTurnSlice(turnTwoStreaming, turnFinished = false)
        val (report, error) = prReviewChromeFromParse(parse, null, null)
        assertNull(report)
        assertNull(error)
    }

    @Test
    fun chromeUsesMapOnSkippedForSameTurn() {
        val stored = """{"schemaVersion":1,"summary":"saved","risk":"low","findings":[],"coverage":{"reviewed":[],"skipped":[]},"missingTests":[],"openQuestions":[]}"""
        val streaming = listOf(
            AcpItem.UserMessage(1, "go", emptyList()),
            AcpItem.AgentText(2, "still working"),
        )
        val parse = PrReviewReportParser.parseTurnSlice(streaming, turnFinished = false)
        val report = prReviewReportFromSessionJson(stored)!!
        val (shown, _) = prReviewChromeFromParse(parse, report, null)
        assertEquals("saved", shown?.summary)
    }

    @Test
    fun pruneDropsReportsForRemovedTurns() {
        val jsonOne = validJson.replace("ok", "one")
        val jsonTwo = validJson.replace("ok", "two")
        val full = listOf(
            AcpItem.UserMessage(1, "a", emptyList()),
            AcpItem.AgentText(2, "```pr-review-report\n$jsonOne\n```"),
            AcpItem.UserMessage(3, "b", emptyList()),
            AcpItem.AgentText(4, "```pr-review-report\n$jsonTwo\n```"),
        )
        val truncated = full.take(2)
        val pruned = prReviewReportsPrunedToTimeline(full, mapOf(1L to jsonOne, 3L to jsonTwo))
        assertEquals(setOf(1L, 3L), pruned.keys)
        assertEquals(mapOf(1L to jsonOne), prReviewReportsPrunedToTimeline(truncated, pruned))
    }

    @Test
    fun alignBackfillsFromChatWhenMapEmpty() {
        val aligned = prReviewReportsAlignedToTimeline(turnOneItems, emptyMap())
        assertEquals(1, aligned.size)
        assertEquals("ok", PrReviewReportParser.decodeStored(org.json.JSONObject(aligned.getValue(1L)))?.summary)
    }

    @Test
    fun lastReportMirrorUsesNewestRemainingTurn() {
        val jsonOne = validJson.replace("ok", "one")
        val jsonTwo = validJson.replace("ok", "two")
        val items = listOf(
            AcpItem.UserMessage(1, "a", emptyList()),
            AcpItem.UserMessage(3, "b", emptyList()),
        )
        val mirror = prReviewLastReportJsonMirror(items, mapOf(1L to jsonOne, 3L to jsonTwo))
        assertEquals(jsonTwo, mirror)
    }

    @Test
    fun mapEntryUsedWhenFenceMissingAfterMcpSubmit() {
        val jsonStored =
            """{"schemaVersion":1,"summary":"mcp","risk":"low","findings":[],"coverage":{"reviewed":[],"skipped":[]},"missingTests":[],"openQuestions":[]}"""
        val items = listOf(
            AcpItem.UserMessage(1, "review", emptyList()),
            AcpItem.AgentText(2, "Which ids should I fix?"),
        )
        val json = prReviewJsonForTurn(
            userMessageId = 1L,
            turnItems = items,
            turnFinished = true,
            reportsByUserMessageId = mapOf(1L to jsonStored),
            lastUserMessageId = 1L,
            sessionLastReportJson = null,
        )
        assertEquals(jsonStored, json)
    }
}
