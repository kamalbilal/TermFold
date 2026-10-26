package com.termfold.app.prreview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
