package com.termfold.app.automation

import com.termfold.app.memory.AgentMemoryGuidance
import com.termfold.app.memory.AgentMemoryWorkflow
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationPromptAssemblerTest {
    @Test
    fun memoryGuidanceMentionsSessionAndNavTools() {
        val block = AgentMemoryGuidance.promptBlock(AgentMemoryWorkflow.AUTOMATION)
        assertTrue(block.contains("session_memory_search"))
        assertTrue(block.contains("session_memory_write"))
        assertTrue(block.contains("nav_memory_search"))
        assertTrue(block.contains("nav_memory_write"))
    }

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
