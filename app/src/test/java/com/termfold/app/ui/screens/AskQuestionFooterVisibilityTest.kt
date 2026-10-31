package com.termfold.app.ui.screens

import com.termfold.app.acp.AcpItem
import com.termfold.app.acp.AcpPendingAskQuestion
import com.termfold.app.acp.AskQuestionPhase
import com.termfold.app.acp.AskQuestionSpec
import com.termfold.app.acp.AskQuestionTurnSnapshot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AskQuestionFooterVisibilityTest {

    @Test
    fun showsForPendingOnLatestTurn() {
        val pending = AcpPendingAskQuestion(1, "tc", null, emptyList(), userMessageId = 10L)
        assertTrue(askFooterVisibleForTurn(10L, 10L, pending, emptyList()))
    }

    @Test
    fun pendingSnapshotVisibleOnMatchingTurn() {
        val list = listOf(AskQuestionTurnSnapshot("tc", null, emptyList(), AskQuestionPhase.Pending))
        assertTrue(askFooterVisibleForTurn(10L, 10L, null, list))
    }

    @Test
    fun showsHistoricalAnsweredSnapshot() {
        val list = listOf(AskQuestionTurnSnapshot("tc", null, emptyList(), AskQuestionPhase.Answered))
        assertTrue(askFooterVisibleForTurn(10L, 20L, null, list))
    }
}
