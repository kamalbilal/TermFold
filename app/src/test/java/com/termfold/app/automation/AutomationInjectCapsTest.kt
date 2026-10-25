package com.termfold.app.automation

import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationInjectCapsTest {
    @Test
    fun trimBlockEnforcesMaxChars() {
        val huge = "x".repeat(10_000)
        val trimmed = AutomationInjectCaps.trimBlock(huge)
        assertTrue(trimmed.length <= AutomationInjectCaps.MAX_TOTAL_CHARS + 4)
        assertTrue(trimmed.endsWith("…"))
    }
}
