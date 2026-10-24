package com.termfold.app.acp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CursorAcpPrefsTest {

    @Test
    fun prefersAllowAlways() {
        val options = listOf(
            AcpPermissionOption("reject-once", "Reject", "reject_once"),
            AcpPermissionOption("allow-once", "Allow once", "allow_once"),
            AcpPermissionOption("allow-always", "Allow always", "allow_always"),
        )
        assertEquals("allow-always", CursorAcpPrefs.preferredAllowOptionId(options))
    }

    @Test
    fun fallsBackToAllowOnce() {
        val options = listOf(
            AcpPermissionOption("reject-once", "Reject", "reject_once"),
            AcpPermissionOption("allow-once", "Allow once", "allow_once"),
        )
        assertEquals("allow-once", CursorAcpPrefs.preferredAllowOptionId(options))
    }

    @Test
    fun apiKeyEnvironmentTrimsAndUsesCursorEnvName() {
        assertTrue(CursorAcpPrefs.apiKeyEnvironment(null).isEmpty())
        assertTrue(CursorAcpPrefs.apiKeyEnvironment("  ").isEmpty())
        assertEquals(
            mapOf("CURSOR_API_KEY" to "key_test"),
            CursorAcpPrefs.apiKeyEnvironment("  key_test  "),
        )
    }
}
