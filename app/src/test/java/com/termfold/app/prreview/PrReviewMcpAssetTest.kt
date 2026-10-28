package com.termfold.app.prreview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PrReviewMcpAssetTest {
    @Test
    fun guestScriptDeclaresSubmitTool() {
        val root = File(System.getProperty("user.dir"))
        val file = sequenceOf(
            File(root, "app/src/main/assets/termfold-pr-review.js"),
            File(root, "src/main/assets/termfold-pr-review.js"),
        ).firstOrNull { it.isFile } ?: error("termfold-pr-review.js not found under $root")
        val text = file.readText()
        assertTrue(text.contains("submit_pr_review_report"))
        assertTrue(text.contains("pr_review_submit_report"))
    }
}
