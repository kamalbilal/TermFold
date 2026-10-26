package com.termfold.app.prreview

object PrReviewTurnSync {
    fun apply(session: PrReviewSession, parse: PrReviewParse): PrReviewSession = when (parse) {
        is PrReviewParse.Ok -> session.copy(
            lastReportJson = PrReviewReportParser.encode(parse.report).toString(),
            lastParseError = null,
        )
        PrReviewParse.Missing -> session.copy(lastParseError = "missing")
        is PrReviewParse.Invalid -> session.copy(lastParseError = parse.reason)
        PrReviewParse.Skipped -> session
    }
}
