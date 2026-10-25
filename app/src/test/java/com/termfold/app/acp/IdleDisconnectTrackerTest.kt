package com.termfold.app.acp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IdleDisconnectTrackerTest {

    private val timeout = 10 * 60_000L

    @Test
    fun disabledNeverDisconnects() {
        val result = IdleDisconnectTracker.tick(
            IdleDisconnectTracker.TickInput(
                nowMs = 0,
                policyEnabled = false,
                inForeground = false,
                agentNeedsWake = false,
                eligibleSinceMs = null,
                timeoutMs = timeout,
            ),
        )
        assertNull(result.eligibleSinceMs)
        assertFalse(result.shouldDisconnect)
    }

    @Test
    fun foregroundResetsEligibility() {
        val result = IdleDisconnectTracker.tick(
            IdleDisconnectTracker.TickInput(
                nowMs = 1000,
                policyEnabled = true,
                inForeground = true,
                agentNeedsWake = false,
                eligibleSinceMs = 0,
                timeoutMs = timeout,
            ),
        )
        assertNull(result.eligibleSinceMs)
        assertFalse(result.shouldDisconnect)
    }

    @Test
    fun busyAgentResetsEligibility() {
        val result = IdleDisconnectTracker.tick(
            IdleDisconnectTracker.TickInput(
                nowMs = 1000,
                policyEnabled = true,
                inForeground = false,
                agentNeedsWake = true,
                eligibleSinceMs = 0,
                timeoutMs = timeout,
            ),
        )
        assertNull(result.eligibleSinceMs)
        assertFalse(result.shouldDisconnect)
    }

    @Test
    fun disconnectAfterTimeout() {
        val start = 1_000L
        val first = IdleDisconnectTracker.tick(
            IdleDisconnectTracker.TickInput(
                nowMs = start,
                policyEnabled = true,
                inForeground = false,
                agentNeedsWake = false,
                eligibleSinceMs = null,
                timeoutMs = timeout,
            ),
        )
        assertEquals(start, first.eligibleSinceMs)
        assertFalse(first.shouldDisconnect)

        val fire = IdleDisconnectTracker.tick(
            IdleDisconnectTracker.TickInput(
                nowMs = start + timeout,
                policyEnabled = true,
                inForeground = false,
                agentNeedsWake = false,
                eligibleSinceMs = first.eligibleSinceMs,
                timeoutMs = timeout,
            ),
        )
        assertTrue(fire.shouldDisconnect)
        assertNull(fire.eligibleSinceMs)
    }
}
