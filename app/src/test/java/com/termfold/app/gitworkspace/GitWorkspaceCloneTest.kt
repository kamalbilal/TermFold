package com.termfold.app.gitworkspace

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitWorkspaceCloneTest {
    @Test
    fun sessionCloneUsesCopyNotHardlinks() {
        val script = GitWorkspace.sessionCloneScript("/data/projects/golden", "/tmp/termfold-ws-abc")
        assertTrue(script.contains("git clone --no-hardlinks"))
        assertFalse(script.contains("--local"))
    }

    @Test
    fun sessionCloneCanCheckoutBranchAtCloneTime() {
        val script = GitWorkspace.sessionCloneScript("/data/projects/golden", "/tmp/ws", "feature-x")
        assertTrue(script.contains("-b 'feature-x'"))
    }
}
