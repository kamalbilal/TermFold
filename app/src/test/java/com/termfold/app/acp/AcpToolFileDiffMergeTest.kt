package com.termfold.app.acp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AcpToolFileDiffMergeTest {

    @Test
    fun mergeUpdateKeepsTighterPatch() {
        val tight = AcpToolFileDiff(
            path = "/f.html",
            operation = AcpToolFileDiff.Operation.MODIFY,
            patchText = """
                --- /f.html
                +++ /f.html
                @@ -6,1 +6,1 @@
                -old title
                +new title
            """.trimIndent(),
        )
        val huge = tight.copy(
            patchText = """
                --- /f.html
                +++ /f.html
                @@ -1,3 +1,3 @@
                -a
                +A
                -b
                +B
                -c
                +C
            """.trimIndent(),
        )
        val merged = mergeToolFileDiffUpdates(listOf(tight), listOf(huge))
        assertEquals(1, merged.size)
        assertTrue(merged.single().patchText!!.contains("old title"))
    }

    @Test
    fun mergeUpdateAcceptsImprovedPatch() {
        val empty = AcpToolFileDiff("/f.kt", AcpToolFileDiff.Operation.MODIFY, patchText = null)
        val withPatch = empty.copy(patchText = "@@\n-x\n+y")
        val merged = mergeToolFileDiffUpdates(listOf(empty), listOf(withPatch))
        assertEquals("@@\n-x\n+y", merged.single().patchText)
    }
}
