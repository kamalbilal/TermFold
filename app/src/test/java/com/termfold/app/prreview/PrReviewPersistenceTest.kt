package com.termfold.app.prreview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrReviewPersistenceTest {
    @Test
    fun roundTripOptionalWorkspaceId() {
        val original = PrReviewData(
            sessions = listOf(
                PrReviewSession(
                    id = "s1",
                    name = "Fix login",
                    url = "https://github.com/owner/repo/pull/12",
                    owner = "owner",
                    repo = "repo",
                    number = 12,
                    headBranch = "fix-login",
                    folderId = "folder-1",
                    workspaceId = "ws-1",
                ),
            ),
        )
        val decoded = PrReviewPersistence.decode(PrReviewPersistence.encode(original))
        assertEquals(original, decoded)
        val gone = decoded.sessions.single().copy(workspaceId = null)
        val again = PrReviewPersistence.decode(PrReviewPersistence.encode(PrReviewData(listOf(gone))))
        assertNull(again.sessions.single().workspaceId)
    }

    @Test
    fun roundTripLastReport() {
        val report = PrReviewReport(
            schemaVersion = 1,
            summary = "Adds login",
            risk = PrReviewRisk.LOW,
            findings = listOf(
                PrReviewFinding(
                    id = "A-1",
                    surface = PrReviewSurface.ANDROID,
                    severity = PrReviewSeverity.NIT,
                    title = "typo",
                    location = "a.kt:1",
                    inDiff = true,
                    evidence = "e",
                    suggestion = "s",
                ),
            ),
            coverage = PrReviewCoverage(listOf("a.kt"), emptyList()),
            missingTests = emptyList(),
            openQuestions = listOf("ok?"),
        )
        val original = PrReviewData(
            sessions = listOf(
                PrReviewSession(
                    id = "s1",
                    name = "Fix login",
                    url = "https://github.com/owner/repo/pull/12",
                    owner = "owner",
                    repo = "repo",
                    number = 12,
                    headBranch = "fix-login",
                    folderId = "folder-1",
                    lastReportJson = PrReviewReportParser.encode(report).toString(),
                    lastParseError = null,
                ),
            ),
        )
        val decoded = PrReviewPersistence.decode(PrReviewPersistence.encode(original))
        assertEquals(original, decoded)
        val stored = PrReviewReportParser.decodeStored(
            org.json.JSONObject(decoded.sessions.single().lastReportJson!!),
        )
        assertEquals("Adds login", stored?.summary)
        assertEquals("A-1", stored?.findings?.single()?.id)
    }

    @Test
    fun roundTripReportsByUserMessageId() {
        val jsonOne =
            """{"schemaVersion":1,"summary":"one","risk":"low","findings":[],"coverage":{"reviewed":[],"skipped":[]},"missingTests":[],"openQuestions":[]}"""
        val jsonTwo =
            """{"schemaVersion":1,"summary":"two","risk":"low","findings":[],"coverage":{"reviewed":[],"skipped":[]},"missingTests":[],"openQuestions":[]}"""
        val original = PrReviewData(
            sessions = listOf(
                PrReviewSession(
                    id = "s1",
                    name = "Fix login",
                    url = "https://github.com/owner/repo/pull/12",
                    owner = "owner",
                    repo = "repo",
                    number = 12,
                    headBranch = "fix-login",
                    folderId = "folder-1",
                    reportsByUserMessageId = mapOf(1L to jsonOne, 3L to jsonTwo),
                ),
            ),
        )
        val decoded = PrReviewPersistence.decode(PrReviewPersistence.encode(original))
        assertEquals(original, decoded)
    }

    @Test
    fun upsertKeepsPosition() {
        val first = PrReviewSession(
            id = "a",
            name = "A",
            url = "https://github.com/o/r/pull/1",
            owner = "o",
            repo = "r",
            number = 1,
            headBranch = "a",
            folderId = "f",
        )
        val second = first.copy(id = "b", name = "B", number = 2)
        val data = PrReviewData(listOf(first, second)).upsert(first.copy(name = "A2"))
        assertEquals(listOf("A2", "B"), data.sessions.map { it.name })
    }

    @Test
    fun boundToNewPrClearsPreviousReport() {
        val session = PrReviewSession(
            id = "s1",
            name = "Old",
            url = "https://github.com/owner/repo/pull/12",
            owner = "owner",
            repo = "repo",
            number = 12,
            headBranch = "old",
            folderId = "folder-1",
            lastReportJson = """{"schemaVersion":1}""",
            lastParseError = "missing",
            acpAgentId = "cursor",
        )
        val next = session.boundToNewPr(
            info = GitHubPrInfo("New", "new-head", "https://github.com/owner/repo/pull/13", "body", "main"),
            ref = GitHubPrRef("owner", "repo", 13),
            headBranch = "new-head",
            workspaceId = "ws-2",
        )
        assertEquals("New", next.name)
        assertEquals(13, next.number)
        assertEquals("ws-2", next.workspaceId)
        assertNull(next.lastReportJson)
        assertNull(next.lastParseError)
        assertTrue(next.reportsByUserMessageId.isEmpty())
        assertEquals("cursor", next.acpAgentId)
    }
}
