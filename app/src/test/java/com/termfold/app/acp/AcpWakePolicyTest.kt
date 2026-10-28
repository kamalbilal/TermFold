package com.termfold.app.acp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AcpWakePolicyTest {

    @Test
    fun idleTurnDoesNotWakeEvenWithStaleToolsInHistory() {
        assertFalse(
            AcpWakePolicy.keepsCpuAwake(
                turnInFlight = false,
                agentBusy = false,
                pendingPermission = false,
                pendingAskQuestion = false,
                wireResyncing = false,
            ),
        )
    }

    @Test
    fun activeTurnOrPermissionWakes() {
        assertTrue(
            AcpWakePolicy.keepsCpuAwake(
                turnInFlight = true,
                agentBusy = false,
                pendingPermission = false,
                pendingAskQuestion = false,
                wireResyncing = false,
            ),
        )
        assertTrue(
            AcpWakePolicy.keepsCpuAwake(
                turnInFlight = false,
                agentBusy = true,
                pendingPermission = false,
                pendingAskQuestion = false,
                wireResyncing = false,
            ),
        )
        assertTrue(
            AcpWakePolicy.keepsCpuAwake(
                turnInFlight = false,
                agentBusy = false,
                pendingPermission = true,
                pendingAskQuestion = false,
                wireResyncing = false,
            ),
        )
        assertTrue(
            AcpWakePolicy.keepsCpuAwake(
                turnInFlight = false,
                agentBusy = false,
                pendingPermission = false,
                pendingAskQuestion = true,
                wireResyncing = false,
            ),
        )
        assertTrue(
            AcpWakePolicy.keepsCpuAwake(
                turnInFlight = false,
                agentBusy = false,
                pendingPermission = false,
                pendingAskQuestion = false,
                wireResyncing = true,
            ),
        )
    }
}
