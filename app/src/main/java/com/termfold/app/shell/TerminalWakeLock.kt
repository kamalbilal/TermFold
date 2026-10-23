package com.termfold.app.shell

/**
 * Whether a terminal session should contribute to background wake locks, from Wake / Auto prefs.
 */
object TerminalWakeLock {

    fun wantsWakeLock(
        running: Boolean,
        working: Boolean,
        wakeEnabled: Boolean,
        wakeAuto: Boolean,
    ): Boolean {
        if (!wakeEnabled || !running) return false
        return if (wakeAuto) working else true
    }

    fun wantsWakeLock(session: TerminalHost.LiveSession): Boolean =
        wantsWakeLock(session.running, session.working, session.wakeEnabled, session.wakeAuto)

    fun anyTerminalWantsWakeLock(sessions: List<TerminalHost.LiveSession>): Boolean =
        sessions.any { wantsWakeLock(it) }
}
