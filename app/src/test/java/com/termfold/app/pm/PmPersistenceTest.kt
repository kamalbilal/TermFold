package com.termfold.app.pm

import org.junit.Assert.assertEquals
import org.junit.Test

class PmPersistenceTest {
    @Test
    fun roundTripSession() {
        val original = PmData(
            sessions = listOf(
                PmSession(
                    id = "pm-1",
                    name = "Q4 discovery",
                    folderId = "folder-1",
                    mode = PmMode.DISCOVERY,
                    viewBranch = "main",
                    competitors = listOf(PmCompetitor("Acme", "https://acme.example")),
                    includeCompetitiveResearch = true,
                    workspaceId = null,
                ),
            ),
        )
        val decoded = PmPersistence.decode(PmPersistence.encode(original))
        assertEquals(original, decoded)
    }
}
