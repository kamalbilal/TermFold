package com.termfold.app.gitworkspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BranchNamesTest {
    @Test
    fun normalizePrefersLocalOverOriginDuplicate() {
        val names = BranchNames.normalizeForPicker(listOf("main", "origin/main", "origin/feature"))
        assertEquals(listOf("feature", "main"), names)
    }

    @Test
    fun normalizeRemoteOnlyDropsOriginPrefix() {
        val names = BranchNames.normalizeForPicker(listOf("origin/alorica-cefr-leave-navigation-main-qa-kamal-bilal"))
        assertEquals(
            listOf("alorica-cefr-leave-navigation-main-qa-kamal-bilal"),
            names,
        )
    }

    @Test
    fun canonicalLocalNameStripsOrigin() {
        assertEquals(
            "Fixing-Ahmad--From-Trajector-ahmad-changing-new-changes",
            BranchNames.canonicalLocalName("origin/Fixing-Ahmad--From-Trajector-ahmad-changing-new-changes"),
        )
    }

    @Test
    fun switchArgsNeverUsesBareOriginPathspec() {
        val attempts = BranchNames.switchArgs("feature-x", hasLocalHead = false, hasOriginRemote = true)
        assertFalse(attempts.any { it == "switch 'origin/feature-x'" })
        assertTrue(attempts.any { it.contains("--track") && it.contains("origin/feature-x") })
    }

    @Test
    fun switchArgsEmptyWhenRefMissing() {
        assertTrue(BranchNames.switchArgs("missing", false, false).isEmpty())
    }

    @Test
    fun normalizeDropsBareOriginRemoteLabel() {
        val names = BranchNames.normalizeForPicker(listOf("main", "origin", "origin/main-qa"))
        assertEquals(listOf("main", "main-qa"), names)
    }

    @Test
    fun importBranchFetchCommandsCoverLocalAndRemoteGoldenRefs() {
        val cmds = BranchNames.importBranchFetchCommands("feature-x")
        assertEquals(3, cmds.size)
        assertTrue(cmds[0].contains("refs/remotes/origin/feature-x"))
        assertTrue(cmds[1].contains("refs/heads/feature-x"))
    }
}
