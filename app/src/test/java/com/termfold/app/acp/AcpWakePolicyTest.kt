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
                wireResyncing = false,
            ),
        )
        assertTrue(
            AcpWakePolicy.keepsCpuAwake(
                turnInFlight = false,
                agentBusy = true,
                pendingPermission = false,
                wireResyncing = false,
            ),
        )
        assertTrue(
            AcpWakePolicy.keepsCpuAwake(
                turnInFlight = false,
                agentBusy = false,
                pendingPermission = true,
                wireResyncing = false,
            ),
        )
        assertTrue(
            AcpWakePolicy.keepsCpuAwake(
                turnInFlight = false,
                agentBusy = false,
                pendingPermission = false,
                wireResyncing = true,
            ),
        )
    }
}
