package com.termfold.app.gitworkspace

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BranchListLoadPolicyTest {
    @Test
    fun forceRefreshAlwaysFetches() {
        assertTrue(shouldFetchBranchListFromGit(forceRefresh = true, cachedBranches = listOf("main")))
    }

    @Test
    fun noCacheFetches() {
        assertTrue(shouldFetchBranchListFromGit(forceRefresh = false, cachedBranches = null))
    }

    @Test
    fun emptyCacheRefetches() {
        assertTrue(shouldFetchBranchListFromGit(forceRefresh = false, cachedBranches = emptyList()))
    }

    @Test
    fun nonEmptyCacheSkipsFetch() {
        assertFalse(shouldFetchBranchListFromGit(forceRefresh = false, cachedBranches = listOf("main")))
    }
}
