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
            interactiveAskInjection = InteractiveAskWireInjection.Full,
            automationPrefix = "<auto>",
            agentMemoryPrefix = "<mem>",
            extraPrefix = null,
            projectRulesPrefix = rules,
            userText = "hello",
        )
        assertTrue(wire.contains(InteractiveAskPromptRules.ANCHOR_TAG))
        assertTrue(wire.contains("<mem>"))
        assertTrue(wire.contains("<auto>"))
        assertTrue(wire.contains(ProjectRulesPromptAssembler.ANCHOR_TAG))
        assertTrue(wire.endsWith("hello"))
    }

    @Test
    fun omitsProjectRulesTagWhenEmpty() {
        val wire = composeWirePromptText(
            pendingWireContext = null,
            interactiveAskInjection = InteractiveAskWireInjection.None,
            automationPrefix = null,
            agentMemoryPrefix = null,
            extraPrefix = null,
            projectRulesPrefix = "",
            userText = "hi",
        )
        assertFalse(wire.contains(ProjectRulesPromptAssembler.ANCHOR_TAG))
        assertFalse(wire.contains(InteractiveAskPromptRules.ANCHOR_TAG))
        assertEquals("hi", wire)
    }

    @Test
    fun cursorAgentAlwaysInjectsInteractiveAskEvenWithoutOtherPrefixes() {
        val wire = composeWirePromptText(
            pendingWireContext = null,
            interactiveAskInjection = InteractiveAskWireInjection.Full,
            automationPrefix = null,
            agentMemoryPrefix = null,
            extraPrefix = null,
            projectRulesPrefix = null,
            userText = "only question",
        )
        assertTrue(wire.startsWith("<${InteractiveAskPromptRules.ANCHOR_TAG}>"))
        assertTrue(wire.contains("ask_user"))
        assertTrue(wire.endsWith("only question"))
    }

    @Test
    fun compactInteractiveAskStillHasAnchorTag() {
        val wire = composeWirePromptText(
            pendingWireContext = null,
            interactiveAskInjection = InteractiveAskWireInjection.Compact,
            automationPrefix = null,
            agentMemoryPrefix = null,
            extraPrefix = null,
            projectRulesPrefix = null,
            userText = "follow up",
        )
        assertTrue(wire.contains(InteractiveAskPromptRules.ANCHOR_TAG))
        assertTrue(wire.contains("ask_user"))
        assertFalse(wire.contains("mandatory — very critical"))
        assertTrue(wire.endsWith("follow up"))
    }
}
