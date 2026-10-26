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
        assertTrue(text.contains("ask the user which issues to fix"))
    }
}
