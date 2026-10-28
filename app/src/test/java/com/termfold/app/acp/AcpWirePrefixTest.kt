package com.termfold.app.acp

import com.termfold.app.projectrules.ProjectRulesPromptAssembler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AcpWirePrefixTest {
    @Test
    fun concatenatesAutomationAndProjectRules() {
        val rules = ProjectRulesPromptAssembler.assemble(
            listOf(com.termfold.app.projectrules.ProjectRule("r", "T", "B")),
        )
        val wire = composeWirePromptText(
            pendingWireContext = null,
            automationPrefix = "<auto>",
            agentMemoryPrefix = "<mem>",
            extraPrefix = null,
            projectRulesPrefix = rules,
            userText = "hello",
        )
        assertTrue(wire.contains("<mem>"))
        assertTrue(wire.contains("<auto>"))
        assertTrue(wire.contains(ProjectRulesPromptAssembler.ANCHOR_TAG))
        assertTrue(wire.endsWith("hello"))
    }

    @Test
    fun omitsProjectRulesTagWhenEmpty() {
        val wire = composeWirePromptText(null, null, null, null, "", "hi")
        assertFalse(wire.contains(ProjectRulesPromptAssembler.ANCHOR_TAG))
        assertEquals("hi", wire)
    }
}
