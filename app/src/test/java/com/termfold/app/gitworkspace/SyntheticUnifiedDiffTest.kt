package com.termfold.app.gitworkspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyntheticUnifiedDiffTest {

    @Test
    fun synthesizeAddFileParses() {
        val raw = synthesizeUnifiedDiff("/proj/new.kt", null, "line one\nline two")
        assertTrue(raw.contains("+++ /proj/new.kt"))
        val parsed = parseUnifiedDiff(raw)
        assertFalse(parsed.isBinary)
        assertTrue(parsed.hunks.isNotEmpty())
        assertEquals(2, parsed.hunks.single().lines.count { it.kind == DiffLineKind.ADD })
    }

    @Test
    fun synthesizeDeleteFileParses() {
        val raw = synthesizeUnifiedDiff("/proj/old.kt", "gone", null)
        assertTrue(raw.contains("--- /proj/old.kt"))
        val parsed = parseUnifiedDiff(raw)
        assertEquals(1, parsed.hunks.single().lines.count { it.kind == DiffLineKind.REMOVE })
    }

    @Test
    fun synthesizeModifyParses() {
        val raw = synthesizeUnifiedDiff("/proj/a.kt", "alpha\nbeta", "alpha\ngamma")
        val parsed = parseUnifiedDiff(raw)
        val kinds = parsed.hunks.single().lines.map { it.kind }
        assertTrue(kinds.contains(DiffLineKind.REMOVE))
        assertTrue(kinds.contains(DiffLineKind.ADD))
    }

    @Test
    fun synthesizeModifyLargeFileUsesSmallHunk() {
        val lines = (1..200).map { "line $it" }
        val old = lines.joinToString("\n")
        val new = lines.toMutableList().also { it[5] = "line SIX changed" }.joinToString("\n")
        val raw = synthesizeUnifiedDiff("/big.txt", old, new)
        val (added, removed) = diffLineStats(raw)
        assertEquals(1, added)
        assertEquals(1, removed)
        assertTrue(raw.lines().count { it.startsWith("@@") } <= 2)
    }

    @Test
    fun splitTwoFilePatch() {
        val patch = """
            diff --git /a/x.txt /a/x.txt
            --- /a/x.txt
            +++ /a/x.txt
            @@ -1 +1 @@
            -old
            +new
            diff --git /a/y.txt /a/y.txt
            --- /a/y.txt
            +++ /a/y.txt
            @@ -1 +1 @@
            +only
        """.trimIndent()
        val parts = splitGitPatchByFile(patch)
        assertEquals(2, parts.size)
        assertTrue(parts[0].contains("x.txt"))
        assertTrue(parts[1].contains("y.txt"))
    }

    @Test
    fun capToolPatchTextAddsMarker() {
        val huge = "x".repeat(MAX_TOOL_PATCH_CHARS + 100)
        val capped = capToolPatchText(huge)
        assertTrue(capped.contains(TOOL_PATCH_TRUNCATED_MARKER.trim()))
        assertTrue(capped.length < huge.length)
    }
}
