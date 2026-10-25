package com.termfold.app.acp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IdleDisconnectPrefsTest {

    @Test
    fun defaults() {
        assertTrue(IdleDisconnectPrefs.DEFAULT_ENABLED)
        assertEquals(10, IdleDisconnectPrefs.DEFAULT_TIMEOUT_MINUTES)
    }

    @Test
    fun clampMinutes() {
        assertEquals(1, IdleDisconnectPrefs.clampMinutes(0))
        assertEquals(1, IdleDisconnectPrefs.clampMinutes(-5))
        assertEquals(120, IdleDisconnectPrefs.clampMinutes(999))
        assertEquals(30, IdleDisconnectPrefs.clampMinutes(30))
    }
}
