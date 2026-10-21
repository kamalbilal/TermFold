package com.termfold.app.gitworkspace

import org.junit.Assert.assertEquals
import org.junit.Test

class GitWorkspaceProgressTest {
    @Test
    fun percentFromBytes() {
        val half = GitWorkspace.ProvisionProgress(50, 100)
        assertEquals(50, half.percent)
        val full = GitWorkspace.ProvisionProgress(100, 100)
        assertEquals(100, full.percent)
        val empty = GitWorkspace.ProvisionProgress(0, 0)
        assertEquals(0, empty.percent)
    }
}
