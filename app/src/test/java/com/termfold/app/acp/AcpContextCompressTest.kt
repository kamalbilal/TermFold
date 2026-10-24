package com.termfold.app.acp

import com.termfold.app.acp.checkpoints.ConversationSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AcpContextCompressTest {

    @Test
    fun isSummarizePrompt_matchesSlashSummarize() {
        assertTrue(AcpContextCompress.isSummarizePrompt("/summarize"))
        assertTrue(AcpContextCompress.isSummarizePrompt("  /Summarize  "))
        assertFalse(AcpContextCompress.isSummarizePrompt("/summarize extra"))
        assertFalse(AcpContextCompress.isSummarizePrompt("summarize"))
    }

    @Test
    fun agentSummaryAfterUser_collectsAgentTextUntilNextUser() {
        val items = listOf(
            AcpItem.UserMessage(1L, "/summarize", emptyList()),
            AcpItem.Thought(2L, "thinking"),
            AcpItem.AgentText(3L, "Part one."),
            AcpItem.AgentText(4L, "Part two."),
            AcpItem.UserMessage(5L, "next", emptyList()),
            AcpItem.AgentText(6L, "later"),
        )
        assertEquals("Part one.\n\nPart two.", AcpContextCompress.agentSummaryAfterUser(items, 1L))
    }

    @Test
    fun formatWireContext_includesHeader() {
        val formatted = AcpContextCompress.formatWireContext("hello")
        assertTrue(formatted.startsWith("Session context (compressed from /summarize):"))
        assertTrue(formatted.contains("hello"))
    }

    @Test
    fun resolveWireSummary_prefersOverride() {
        val items = listOf(
            AcpItem.UserMessage(1L, "hi", emptyList()),
        )
        val mechanical = ConversationSummary.fromItems(items, null)
        val override = "custom summary"
        assertEquals(override, ConversationSummary.resolveWireSummary(items, null, override))
        assertEquals(mechanical, ConversationSummary.resolveWireSummary(items, null, null))
    }
}
