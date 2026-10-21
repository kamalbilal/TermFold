package com.termfold.app.gitworkspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedDiffParserTest {

    @Test
    fun parsesHunkWithContextAddAndRemove() {
        val raw = """
            --- a/foo.kt
            +++ b/foo.kt
            @@ -10,3 +10,4 @@ fun main() {
             context one
            -removed
            +added
             context two
        """.trimIndent()

        val parsed = parseUnifiedDiff(raw)
        assertFalse(parsed.isBinary)
        assertEquals(1, parsed.hunks.size)
        val lines = parsed.hunks.single().lines
        assertEquals(DiffLineKind.CONTEXT, lines[0].kind)
        assertEquals("context one", lines[0].text)
        assertEquals(10, lines[0].oldLineNumber)
        assertEquals(10, lines[0].newLineNumber)
        assertEquals(DiffLineKind.REMOVE, lines[1].kind)
        assertEquals("removed", lines[1].text)
        assertEquals(DiffLineKind.ADD, lines[2].kind)
        assertEquals("added", lines[2].text)
    }

    @Test
    fun sideBySidePairsBlankOppositeSide() {
        val raw = """
            @@ -1,2 +1,2 @@
            -old
            +new
        """.trimIndent()
        val parsed = parseUnifiedDiff(raw)
        val rows = buildSideBySideRows(parsed.hunks).single().second
        assertEquals(2, rows.size)
        assertEquals(DiffLineKind.REMOVE, rows[0].left!!.kind)
        assertEquals(null, rows[0].right)
        assertEquals(null, rows[1].left)
        assertEquals(DiffLineKind.ADD, rows[1].right!!.kind)
    }

    @Test
    fun detectsBinaryDiff() {
        val parsed = parseUnifiedDiff("Binary files a/x.png and b/x.png differ")
        assertTrue(parsed.isBinary)
        assertTrue(parsed.hunks.isEmpty())
    }

    @Test
    fun foldsLongContextRunsIntoExpandableRow() {
        val rows = buildList {
            add(DiffDisplayRow.HunkHeader("@@ -1,10 +1,10 @@"))
            repeat(10) { index ->
                add(
                    DiffDisplayRow.Unified(
                        ' ',
                        SideBySideCell(index + 1, "ctx$index", DiffLineKind.CONTEXT),
                    ),
                )
            }
            add(
                DiffDisplayRow.Unified(
                    '-',
                    SideBySideCell(11, "removed", DiffLineKind.REMOVE),
                ),
            )
        }
        val folded = applyContextFolding(rows, "f:", expandedFolds = emptySet(), threshold = 8)
        assertEquals(1, folded.count { it is DiffDisplayRow.ContextFold })
        val expanded = applyContextFolding(rows, "f:", expandedFolds = setOf("f:0"), threshold = 8)
        assertEquals(0, expanded.count { it is DiffDisplayRow.ContextFold })
        assertTrue(expanded.size > folded.size)
    }

    @Test
    fun prepareDiffPresentationCachesBothModes() {
        val raw = """
            @@ -1,2 +1,2 @@
            -a
            +b
        """.trimIndent()
        val prepared = prepareDiffPresentation(raw)
        assertFalse(prepared.isBinary)
        assertTrue(prepared.rowsUnified.isNotEmpty())
        assertTrue(prepared.rowsSplit.isNotEmpty())
        assertTrue(prepared.fallbackLines.isEmpty())
    }
}
