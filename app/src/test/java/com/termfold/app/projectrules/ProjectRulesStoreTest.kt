package com.termfold.app.projectrules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectRulesStoreTest {
    @Test
    fun encodeDecodeRoundTrip() {
        val data = ProjectRulesData(
            profiles = listOf(
                ProjectRuleProfile(
                    id = "p1",
                    displayName = "App",
                    originKey = "github.com/acme/app",
                    rules = listOf(ProjectRule("r1", "Build", "run gradle", 0)),
                ),
            ),
            sessionBindings = mapOf("sess" to listOf("r1")),
        )
        val decoded = ProjectRulesPersistence.decode(ProjectRulesPersistence.encode(data))
        assertEquals(data, decoded)
    }

    @Test
    fun deletedRuleIdsSkippedInPrompt() {
        val data = ProjectRulesData(sessionBindings = mapOf("s" to listOf("missing")))
        assertTrue(ProjectRulesContextResolver.enabledRulesForPrompt(data, "s").isEmpty())
    }

    @Test
    fun assemblerIncludesMandatoryPreamble() {
        val out = ProjectRulesPromptAssembler.assemble(listOf(ProjectRule("r", "Lint", "run ktlint")))
        assertTrue(out.contains("MUST follow"))
        assertTrue(out.contains(ProjectRulesPromptAssembler.ANCHOR_TAG))
    }
}
