package com.termfold.app.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutomationPersistenceTest {
    @Test
    fun roundTripProjectWithLinkAndSessions() {
        val original = AutomationData(
            projects = listOf(
                AutomationProject(
                    id = "p1",
                    name = "Checkout",
                    tint = 2,
                    linkedFolderId = "folder-abc",
                    sessions = listOf(
                        AutomationSession(id = "s1", name = "Smoke", acpAgentId = "cursor"),
                    ),
                ),
            ),
        )
        val json = AutomationPersistence.encode(original)
        val decoded = AutomationPersistence.decode(json)
        assertEquals(original, decoded)
    }

    @Test
    fun unlinkClearsLinkField() {
        val data = AutomationPersistence.decode(
            """{"projects":[{"id":"p","name":"X","tint":0,"linkedFolderId":"","sessions":[]}]}""",
        )
        assertNull(data.projects.single().linkedFolderId)
    }
}
