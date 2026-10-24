package com.termfold.app.acp

import org.junit.Assert.assertEquals
import org.junit.Test

class CursorInstallPathsTest {

    @Test
    fun cursorAgentBinMatchesInstallScriptLayout() {
        assertEquals("/root/.local/bin/agent", AcpInstaller.CURSOR_AGENT_BIN)
    }
}
