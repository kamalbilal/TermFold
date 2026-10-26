package com.termfold.app.prreview

object PrReviewKeys {
    const val PREFIX = "pr-review"

    fun acpSessionKey(reviewSessionId: String): String = "$PREFIX/$reviewSessionId"
}
