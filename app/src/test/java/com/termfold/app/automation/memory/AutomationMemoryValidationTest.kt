package com.termfold.app.automation.memory

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationMemoryValidationTest {
    @Test
    fun rejectsCredentialShapedGlobalWrites() {
        assertTrue(AutomationMemoryRepository.looksLikeSecret("password=hunter2"))
        assertTrue(AutomationMemoryRepository.looksLikeSecret("Bearer eyJhbG"))
        assertFalse(AutomationMemoryRepository.looksLikeSecret("Checkout button moved to settings"))
    }
}
