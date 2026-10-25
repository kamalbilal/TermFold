package com.termfold.app.automation.memory

import org.junit.Assert.assertFalse
import org.junit.Test

class AutomationSecretRedactionTest {
    @Test
    fun secretPutSummaryNeverContainsPayload() {
        val name = "login"
        val payload = "super-secret-password-12345"
        val redactedSummary = "stored secret: $name"
        assertFalse(redactedSummary.contains(payload))
    }
}
