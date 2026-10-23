package com.termfold.app.shell

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalWakeLockTest {

    @Test
    fun wakeOffNeverRequestsLock() {
        assertFalse(TerminalWakeLock.wantsWakeLock(running = true, working = true, wakeEnabled = false, wakeAuto = true))
        assertFalse(TerminalWakeLock.wantsWakeLock(running = true, working = true, wakeEnabled = false, wakeAuto = false))
    }

    @Test
    fun autoOnOnlyWhenWorking() {
        assertFalse(TerminalWakeLock.wantsWakeLock(running = true, working = false, wakeEnabled = true, wakeAuto = true))
        assertTrue(TerminalWakeLock.wantsWakeLock(running = true, working = true, wakeEnabled = true, wakeAuto = true))
    }

    @Test
    fun autoOffLatchedWhileRunning() {
        assertTrue(TerminalWakeLock.wantsWakeLock(running = true, working = false, wakeEnabled = true, wakeAuto = false))
        assertFalse(TerminalWakeLock.wantsWakeLock(running = false, working = false, wakeEnabled = true, wakeAuto = false))
    }

    @Test
    fun notRunningNeverRequestsLock() {
        assertFalse(TerminalWakeLock.wantsWakeLock(running = false, working = true, wakeEnabled = true, wakeAuto = true))
    }
}
