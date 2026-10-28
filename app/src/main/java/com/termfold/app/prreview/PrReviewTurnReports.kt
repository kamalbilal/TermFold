package com.termfold.app.prreview

import com.termfold.app.acp.AcpItem

/** Items from one user message through the next user message (exclusive). */
fun prReviewTurnItems(all: List<AcpItem>, userMessageId: Long): List<AcpItem> {
    val start = all.indexOfFirst { it is AcpItem.UserMessage && it.id == userMessageId }
    if (start < 0) return emptyList()
    var end = start + 1
    while (end < all.size && all[end] !is AcpItem.UserMessage) end++
    return all.subList(start, end)
}

fun prReviewUserMessageIds(items: List<AcpItem>): Set<Long> =
    items.filterIsInstance<AcpItem.UserMessage>().mapTo(HashSet()) { it.id }

/** Drop report entries for user turns no longer in the chat timeline (undo / edit-resend truncate). */
fun prReviewReportsPrunedToTimeline(
    items: List<AcpItem>,
    reportsByUserMessageId: Map<Long, String>,
): Map<Long, String> {
    val live = prReviewUserMessageIds(items)
    return reportsByUserMessageId.filterKeys { it in live }
}

/** Parse fenced reports from chat for turns missing in [reportsByUserMessageId]. */
fun prReviewReportsAlignedToTimeline(
    items: List<AcpItem>,
    reportsByUserMessageId: Map<Long, String>,
): Map<Long, String> {
    var map = prReviewReportsPrunedToTimeline(items, reportsByUserMessageId)
    for (user in items.filterIsInstance<AcpItem.UserMessage>()) {
        if (user.id in map) continue
        val turnItems = prReviewTurnItems(items, user.id)
        val parse = PrReviewReportParser.parseTurnSlice(turnItems, turnFinished = true)
        if (parse is PrReviewParse.Ok) {
            map = map + (user.id to PrReviewReportParser.encode(parse.report).toString())
        }
    }
    return map
}

/** Mirror [lastReportJson] to the newest user turn that still has a saved report. */
fun prReviewLastReportJsonMirror(items: List<AcpItem>, reportsByUserMessageId: Map<Long, String>): String? {
    val users = items.filterIsInstance<AcpItem.UserMessage>()
    for (i in users.indices.reversed()) {
        reportsByUserMessageId[users[i].id]?.let { return it }
    }
    return null
}

/** Saved report JSON for one turn footer, or null when nothing to show yet. */
fun prReviewJsonForTurn(
    userMessageId: Long,
    turnItems: List<AcpItem>,
    turnFinished: Boolean,
    reportsByUserMessageId: Map<Long, String>,
    lastUserMessageId: Long?,
    sessionLastReportJson: String?,
): String? {
    reportsByUserMessageId[userMessageId]?.let { return it }
    when (val parse = PrReviewReportParser.parseTurnSlice(turnItems, turnFinished)) {
        is PrReviewParse.Ok -> return PrReviewReportParser.encode(parse.report).toString()
        else -> Unit
    }
    if (
        reportsByUserMessageId.isEmpty() &&
        userMessageId == lastUserMessageId &&
        prReviewTurnHasAgentReply(turnItems) &&
        !sessionLastReportJson.isNullOrBlank()
    ) {
        return sessionLastReportJson
    }
    return null
}

fun prReviewParseErrorFallback(
    userMessageId: Long,
    lastUserMessageId: Long?,
    reportsByUserMessageId: Map<Long, String>,
    sessionLastParseError: String?,
): String? {
    if (reportsByUserMessageId.isNotEmpty()) return null
    if (userMessageId != lastUserMessageId) return null
    return sessionLastParseError
}

fun prReviewChromeFromParse(
    parse: PrReviewParse,
    fallbackReport: PrReviewReport?,
    fallbackParseError: String?,
): Pair<PrReviewReport?, String?> {
    if (parse is PrReviewParse.Ok) return parse.report to null
    if (fallbackReport != null) {
        return when (parse) {
            PrReviewParse.Missing -> fallbackReport to fallbackParseError
            is PrReviewParse.Invalid -> fallbackReport to parse.reason
            PrReviewParse.Skipped -> fallbackReport to fallbackParseError
            is PrReviewParse.Ok -> parse.report to null
        }
    }
    return when (parse) {
        PrReviewParse.Missing -> null to "missing"
        is PrReviewParse.Invalid -> null to parse.reason
        PrReviewParse.Skipped -> null to null
        is PrReviewParse.Ok -> parse.report to null
    }
}

private fun prReviewTurnHasAgentReply(turnItems: List<AcpItem>): Boolean =
    turnItems.drop(1).any { it is AcpItem.AgentText && it.text.isNotBlank() }
