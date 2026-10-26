package com.termfold.app.prreview

object PrReviewPrompt {
    fun prefix(session: PrReviewSession): String = buildString {
        appendLine("You are reviewing GitHub pull request #${session.number} in ${session.owner}/${session.repo}.")
        appendLine("URL: ${session.url}")
        appendLine("Head branch (already checked out in this workspace): ${session.headBranch}")
        appendLine()
        appendLine("Do a deep, accurate review of the PR changes and the surrounding code.")
        appendLine("List every issue you find.")
        appendLine("Then ask the user which issues to fix (all, a subset, or none).")
        appendLine("Do not apply code changes until the user chooses.")
        appendLine("Do not push. Never modify the golden project tree; this workspace is the isolated PR copy.")
    }
}
