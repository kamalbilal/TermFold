package com.termfold.app.gitworkspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitWorkspaceHeadTest {
    @Test
    fun parseRefHead() {
        assertEquals("main", GitWorkspace.parseGitHeadContent("ref: refs/heads/main\n"))
        assertEquals("feature/foo", GitWorkspace.parseGitHeadContent("ref: refs/heads/feature/foo"))
    }

    @Test
    fun parseDetachedHead() {
        assertEquals("abc1234", GitWorkspace.parseGitHeadContent("abc1234567890abcdef"))
    }

    @Test
    fun parseEmpty() {
        assertNull(GitWorkspace.parseGitHeadContent(""))
        assertNull(GitWorkspace.parseGitHeadContent("   \n"))
    }

    @Test
    fun skipOriginFetchWhenLocalExists() {
        assertFalse(GitWorkspace.shouldFetchOriginBeforeSwitch(hasLocal = true))
        assertTrue(GitWorkspace.shouldFetchOriginBeforeSwitch(hasLocal = false))
    }
}
