package com.termfold.app.gitworkspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitChangeSummaryTest {

    @Test
    fun porcelainSplitsStagedAndUnstagedLikeVsCode() {
        val lines = listOf(
            "M  staged-only.kt",
            " M unstaged-only.kt",
            "MM both.kt",
            "?? new.txt",
            "A  added.kt",
        )
        val (staged, unstaged) = parsePorcelain(lines)
        assertEquals(listOf("staged-only.kt", "both.kt", "added.kt"), staged.map { it.path })
        assertEquals(
            listOf("unstaged-only.kt", "both.kt", "new.txt"),
            unstaged.map { it.path },
        )
    }

    @Test
    fun numstatParsesLineCounts() {
        val map = parseNumstat(listOf("12\t3\tfoo.kt", "-\t-\tbinary.dat"))
        assertEquals(12 to 3, map["foo.kt"])
        assertEquals(0 to 0, map["binary.dat"])
    }

    @Test
    fun syncCountsHiddenWithoutUpstream() {
        val summary = GitChangeSummary("main", null, 2, 3, emptyList(), emptyList())
        assertFalse(gitSyncCountsVisible(summary))
    }

    @Test
    fun buildChangeFilesUsesStagedFlagForStatus() {
        val entries = listOf(PorcelainEntry("x.kt", 'A', ' '))
        val files = buildChangeFiles(entries, staged = true, numstat = mapOf("x.kt" to (5 to 0)))
        assertEquals(GitFileStatus.ADDED, files.single().status)
        assertTrue(files.single().staged)
        assertEquals(5, files.single().additions)
    }
}
