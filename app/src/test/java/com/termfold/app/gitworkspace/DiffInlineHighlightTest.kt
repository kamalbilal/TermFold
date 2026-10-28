package com.termfold.app.gitworkspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiffInlineHighlightTest {

    @Test
    fun emphasizesTypoFixWithinLongLine() {
        val old = "... nor will it a ffect processing ..."
        val new = "... nor will it affect processing ..."
        val (oldSpans, newSpans) = inlineDiffSegments(old, new)
        assertEquals(old, oldSpans.joinToString("") { it.text })
        assertEquals(new, newSpans.joinToString("") { it.text })
        assertTrue(oldSpans.any { it.emphasized })
        assertTrue(newSpans.any { it.emphasized })
    }

    @Test
    fun identicalLinesHaveNoEmphasis() {
        val text = "same line"
        val (a, b) = inlineDiffSegments(text, text)
        assertEquals(listOf(InlineDiffSegment(text, false)), a)
        assertEquals(listOf(InlineDiffSegment(text, false)), b)
    }

    @Test
    fun wordReplaceHighlightsOnlyChangedWord() {
        val (oldSpans, newSpans) = inlineDiffSegments(
            "Komal Bilal is great.",
            "Komal Bilal is awesome.",
        )
        assertEquals("great", oldSpans.single { it.emphasized }.text)
        assertEquals("awesome", newSpans.single { it.emphasized }.text)
    }

    @Test
    fun applyInlineHighlightsPairsAddThenRemove() {
        val lines = listOf(
            DiffLine(DiffLineKind.ADD, "Komal Bilal is awesome.", null, 1),
            DiffLine(DiffLineKind.REMOVE, "Komal Bilal is great.", 1, null),
        )
        val out = applyInlineHighlights(lines)
        assertEquals("awesome", out[0].inlineSpans!!.single { it.emphasized }.text)
        assertEquals("great", out[1].inlineSpans!!.single { it.emphasized }.text)
    }

    @Test
    fun prepareDiffPresentationKeepsWordSpansWhenPlusComesFirst() {
        val raw = """
            @@ -1,2 +1,2 @@
            +Komal Bilal is awesome.
            -Komal Bilal is great.
        """.trimIndent()
        val rows = prepareDiffPresentation(raw).rowsUnified.filterIsInstance<DiffDisplayRow.Unified>()
        val added = rows.single { it.prefix == '+' }.cell
        val removed = rows.single { it.prefix == '-' }.cell
        assertTrue(added.inlineSpans!!.any { it.emphasized && it.text == "awesome" })
        assertTrue(removed.inlineSpans!!.any { it.emphasized && it.text == "great" })
    }
}
