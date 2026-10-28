package com.termfold.app.prreview

import org.junit.Assert.assertTrue
import org.junit.Test

class PrReviewPromptTest {
    @Test
    fun includesRepoNumberAndBranch() {
        val text = PrReviewPrompt.prefix(
            PrReviewSession(
                id = "s",
                name = "n",
                url = "https://github.com/acme/app/pull/44",
                owner = "acme",
                repo = "app",
                number = 44,
                headBranch = "fix-npe",
                folderId = "f",
            ),
        )
        assertTrue(text.contains("acme/app"))
        assertTrue(text.contains("#44"))
        assertTrue(text.contains("fix-npe"))
        assertTrue(text.contains("which finding ids to fix"))
        assertTrue(text.contains(".termfold-pr-review/pr.diff"))
        assertTrue(text.contains("submit_pr_review_report"))
        assertTrue(text.contains("termfold-pr-review"))
        assertTrue(text.contains("pr-review-report"))
        assertTrue(text.contains("linux-guest"))
        assertTrue(text.contains("ios only if files.txt"))
        assertTrue(text.contains("unless the user explicitly asks to verify online"))
        assertTrue(text.contains("Do not apply code changes until the user chooses ids"))
        assertTrue(text.contains("everyday words"))
        assertTrue(text.contains("What's wrong"))
        assertTrue(text.contains("How to fix"))
    }
}
