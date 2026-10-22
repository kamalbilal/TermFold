package com.termfold.app.gitworkspace

import org.junit.Assert.assertEquals
import org.junit.Test

class GitWorkspacePickerBranchesTest {
    @Test
    fun filterKeepsBranchesOnCopy() {
        val golden = listOf("main", "feature-a", "feature-b")
        val onCopy = setOf("main")
        val resolvable = setOf("main", "feature-a", "feature-b")
        assertEquals(
            listOf("main", "feature-a", "feature-b"),
            GitWorkspace.filterPickerBranches(golden, onCopy, resolvable),
        )
    }

    @Test
    fun filterDropsUnreachableWhenNotOnCopyOrCatalog() {
        val golden = listOf("main", "ghost")
        val onCopy = setOf("main")
        val resolvable = setOf("main")
        assertEquals(
            listOf("main"),
            GitWorkspace.filterPickerBranches(golden, onCopy, resolvable),
        )
    }
}
