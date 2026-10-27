package com.termfold.app.pm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PmPromptTest {
    @Test
    fun instructionsAreForLinkedProductNotTermfoldItself() {
        val text = PmPrompt.instructions(
            PmSession(
                id = "s",
                name = "n",
                folderId = "f",
                mode = PmMode.DISCOVERY,
                viewBranch = "main",
            ),
        )
        assertTrue(text.contains("not TermFold"))
        assertFalse(text.contains("Product Manager for TermFold"))
        assertTrue(text.contains("```json"))
        assertTrue(text.contains("scope_in"))
        assertTrue(text.contains("git show main:path"))
        assertTrue(text.contains("Do not edit"))
    }

    @Test
    fun releaseModeForbidsSilentImplementation() {
        val text = PmPrompt.instructions(
            PmSession(
                id = "s",
                name = "n",
                folderId = "f",
                mode = PmMode.RELEASE,
                viewBranch = "rel",
                branch = "rel",
            ),
        )
        assertTrue(text.contains("Plan only"))
    }

    @Test
    fun sessionFactsFlagMissingCompetitors() {
        val text = PmPrompt.sessionFacts(
            PmSession(
                id = "s",
                name = "n",
                folderId = "f",
                mode = PmMode.DISCOVERY,
                viewBranch = "main",
                includeCompetitiveResearch = true,
            ),
        )
        assertTrue(text.contains("none listed"))
        assertTrue(text.contains("Competitive research: ON"))
    }
}
