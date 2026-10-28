package com.termfold.app.prreview

import com.termfold.app.acp.AcpItem

object PrReviewTurnSync {
    fun apply(
        session: PrReviewSession,
        parse: PrReviewParse,
        userMessageId: Long,
    ): PrReviewSession = when (parse) {
        is PrReviewParse.Ok -> {
            val json = PrReviewReportParser.encode(parse.report).toString()
            session.copy(
                lastReportJson = json,
                lastParseError = null,
                reportsByUserMessageId = session.reportsByUserMessageId + (userMessageId to json),
            )
        }
        PrReviewParse.Missing -> session.copy(lastParseError = "missing")
        is PrReviewParse.Invalid -> session.copy(lastParseError = parse.reason)
        PrReviewParse.Skipped -> session
    }

    /** Prune stale turn keys, backfill from chat fences, refresh [lastReportJson] mirror. */
    fun realignToTimeline(session: PrReviewSession, items: List<AcpItem>): PrReviewSession {
        val aligned = prReviewReportsAlignedToTimeline(items, session.reportsByUserMessageId)
        val lastJson = prReviewLastReportJsonMirror(items, aligned)
        return session.copy(
            reportsByUserMessageId = aligned,
            lastReportJson = lastJson,
        )
    }
}
