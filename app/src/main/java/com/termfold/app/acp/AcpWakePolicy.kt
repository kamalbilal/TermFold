package com.termfold.app.acp

/**
 * When [com.termfold.app.notify.SessionWatcher] should hold a partial wake lock for an ACP chat.
 * Stale tool rows in the timeline must not keep the CPU awake after the turn ends.
 */
object AcpWakePolicy {

    fun keepsCpuAwake(
        turnInFlight: Boolean,
        agentBusy: Boolean,
        pendingPermission: Boolean,
        wireResyncing: Boolean,
    ): Boolean {
        if (pendingPermission) return true
        if (wireResyncing) return true
        if (turnInFlight || agentBusy) return true
        return false
    }
}
