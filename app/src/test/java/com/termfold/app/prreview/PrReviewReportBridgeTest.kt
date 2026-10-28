package com.termfold.app.prreview

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrReviewReportBridgeTest {
    private val minimalReport = JSONObject()
        .put("schemaVersion", 1)
        .put("summary", "ok")
        .put("risk", "low")
        .put("findings", JSONArray())
        .put("coverage", JSONObject().put("reviewed", JSONArray()).put("skipped", JSONArray()))
        .put("missingTests", JSONArray())
        .put("openQuestions", JSONArray())

    @Test
    fun rejectsWithoutActiveSession() {
        PrReviewSessionContext.activeSessionId = null
        val out = PrReviewReportBridge.handle(
            PrReviewReportBridge.SUBMIT_ACTION,
            JSONObject().put("report", minimalReport),
        )
        assertTrue(out.has("error"))
    }

    @Test
    fun acceptsValidReport() {
        PrReviewSessionContext.activeSessionId = "s1"
        PrReviewSessionContext.activeUserMessageId = 42L
        var capturedUserId = 0L
        val owner = Any()
        PrReviewReportBridge.setOnSubmit(owner) { _, uid, _ -> capturedUserId = uid }
        try {
            val out = PrReviewReportBridge.handle(
                PrReviewReportBridge.SUBMIT_ACTION,
                JSONObject().put("report", minimalReport),
            )
            assertTrue(out.optBoolean("ok"))
            assertEquals(42L, capturedUserId)
        } finally {
            PrReviewReportBridge.clearOnSubmit(owner)
            PrReviewSessionContext.activeSessionId = null
            PrReviewSessionContext.activeUserMessageId = null
        }
    }

    @Test
    fun rejectsWhenNoUiListener() {
        PrReviewSessionContext.activeSessionId = "s1"
        PrReviewSessionContext.activeUserMessageId = 1L
        PrReviewReportBridge.clearOnSubmitForTests()
        val out = PrReviewReportBridge.handle(
            PrReviewReportBridge.SUBMIT_ACTION,
            JSONObject().put("report", minimalReport),
        )
        assertEquals("No active PR Review UI listener", out.optString("error"))
        PrReviewSessionContext.activeSessionId = null
        PrReviewSessionContext.activeUserMessageId = null
    }

    @Test
    fun rejectsSessionMismatch() {
        PrReviewSessionContext.activeSessionId = "s1"
        PrReviewSessionContext.activeUserMessageId = 1L
        val out = PrReviewReportBridge.handle(
            PrReviewReportBridge.SUBMIT_ACTION,
            JSONObject()
                .put("session_id", "other")
                .put("report", minimalReport),
        )
        assertEquals("PR Review session mismatch", out.optString("error"))
        PrReviewSessionContext.activeSessionId = null
        PrReviewSessionContext.activeUserMessageId = null
    }

    @Test
    fun rejectsInvalidSchema() {
        PrReviewSessionContext.activeSessionId = "s1"
        PrReviewSessionContext.activeUserMessageId = 1L
        val out = PrReviewReportBridge.handle(
            PrReviewReportBridge.SUBMIT_ACTION,
            JSONObject().put("report", JSONObject().put("schemaVersion", 99)),
        )
        assertTrue(out.optString("error").contains("schemaVersion"))
        PrReviewSessionContext.activeSessionId = null
        PrReviewSessionContext.activeUserMessageId = null
    }
}
