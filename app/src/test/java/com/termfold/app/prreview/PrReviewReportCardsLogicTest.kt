package com.termfold.app.prreview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrReviewReportCardsLogicTest {
    @Test
    fun groupsInDiffBySurfaceAndSeparatesOutOfDiff() {
        val report = PrReviewReport(
            schemaVersion = 1,
            summary = "s",
            risk = PrReviewRisk.HIGH,
            findings = listOf(
                finding("A-1", PrReviewSurface.ANDROID, true),
                finding("G-1", PrReviewSurface.LINUX_GUEST, true),
                finding("P-1", PrReviewSurface.SHARED, false),
            ),
            coverage = PrReviewCoverage(listOf("a.kt"), listOf("lock")),
            missingTests = listOf("t"),
            openQuestions = listOf("q"),
        )
        assertEquals(listOf("A-1"), report.inDiffBySurface()[PrReviewSurface.ANDROID]?.map { it.id })
        assertEquals(listOf("G-1"), report.inDiffBySurface()[PrReviewSurface.LINUX_GUEST]?.map { it.id })
        assertTrue(report.inDiffBySurface()[PrReviewSurface.IOS].isNullOrEmpty())
        assertEquals(listOf("P-1"), report.outOfDiff().map { it.id })
    }

    private fun finding(id: String, surface: PrReviewSurface, inDiff: Boolean) = PrReviewFinding(
        id = id,
        surface = surface,
        severity = PrReviewSeverity.SHOULD_FIX,
        title = id,
        location = "f:1",
        inDiff = inDiff,
        evidence = "",
        suggestion = "",
    )
}
