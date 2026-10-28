package com.termfold.app.prreview

object PrReviewSessionContext {
    @Volatile
    var activeSessionId: String? = null

    /** Latest user message id in the PR Review ACP timeline (for MCP submit attribution). */
    @Volatile
    var activeUserMessageId: Long? = null
}
