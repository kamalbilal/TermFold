package com.termfold.app.acp.checkpoints

import com.termfold.app.acp.AcpItem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationSummaryTest {

    @Test
    fun fromItems_usesOnlyKeptTurnsBeforeGhost() {
        val items = listOf(
            AcpItem.UserMessage(1L, "first", emptyList()),
            AcpItem.AgentText(2L, "reply one"),
            AcpItem.UserMessage(10L, "ghost", emptyList()),
            AcpItem.AgentText(11L, "ghost reply"),
        )
        val summary = ConversationSummary.fromItems(items, ghostFromItemId = 10L)
        assertTrue(summary.contains("first"))
        assertTrue(summary.contains("reply one"))
        assertFalse(summary.contains("ghost reply"))
    }
}
