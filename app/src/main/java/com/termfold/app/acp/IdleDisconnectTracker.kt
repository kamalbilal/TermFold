package com.termfold.app.acp

/**
 * Pure idle-timer logic for background ACP disconnect. Eligibility requires policy on,
 * app in background, and agent not needing CPU wake.
 */
object IdleDisconnectTracker {

    data class TickInput(
        val nowMs: Long,
        val policyEnabled: Boolean,
        val inForeground: Boolean,
        val agentNeedsWake: Boolean,
        val eligibleSinceMs: Long?,
        val timeoutMs: Long,
    )

    data class TickResult(
        val eligibleSinceMs: Long?,
        val shouldDisconnect: Boolean,
    )

    fun tick(input: TickInput): TickResult {
        val eligible = input.policyEnabled && !input.inForeground && !input.agentNeedsWake
        if (!eligible) {
            return TickResult(eligibleSinceMs = null, shouldDisconnect = false)
        }
        val since = input.eligibleSinceMs ?: input.nowMs
        val elapsed = input.nowMs - since
        if (elapsed >= input.timeoutMs) {
            return TickResult(eligibleSinceMs = null, shouldDisconnect = true)
        }
        return TickResult(eligibleSinceMs = since, shouldDisconnect = false)
    }
}
