package com.termfold.app.acp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditResendFlowTest {

    @Test
    fun revertFilesRunsOptimisticTurnBeforeWireResync() {
        val steps = editResendSteps(revertFiles = true)
        assertTrue(steps.indexOf(EditResendStep.BeginTurn) < steps.indexOf(EditResendStep.WireResync))
        assertTrue(steps.indexOf(EditResendStep.WireResync) < steps.indexOf(EditResendStep.CompleteTurn))
    }

    @Test
    fun keepFilesSkipsRestoreAndResync() {
        val steps = editResendSteps(revertFiles = false)
        assertEquals(
            listOf(EditResendStep.Truncate, EditResendStep.BeginTurn, EditResendStep.CompleteTurn),
            steps,
        )
    }

    @Test
    fun restoreFailureSkipsTruncate() {
        val steps = editResendSteps(revertFiles = true)
        assertEquals(EditResendStep.Restore, steps.first())
        assertEquals(EditResendStep.Truncate, steps[1])
    }

    @Test
    fun hasPromptPayloadIncludesPendingContext() {
        assertTrue(hasPromptPayload("summary", "", 0, 0))
        assertFalse(hasPromptPayload(null, "", 0, 0))
        assertTrue(hasPromptPayload(null, "hi", 0, 0))
    }
}
