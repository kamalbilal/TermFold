package com.termfold.app.automation

import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationPromptAssemblerTest {
    @Test
    fun anchorTagPresentInFormat() {
        val sample = buildString {
            appendLine("<${AutomationPromptAssembler.ANCHOR_TAG}>")
            appendLine("## Session documents")
            appendLine("constraints")
            appendLine("</${AutomationPromptAssembler.ANCHOR_TAG}>")
        }
        assertTrue(sample.contains(AutomationPromptAssembler.ANCHOR_TAG))
    }
}
