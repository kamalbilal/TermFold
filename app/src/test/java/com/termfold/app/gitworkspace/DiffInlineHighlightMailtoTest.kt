package com.termfold.app.gitworkspace

import org.junit.Assert.assertTrue
import org.junit.Test

class DiffInlineHighlightMailtoTest {

    @Test
    fun mailtoWrapHighlightsInsertionsOnNewSide() {
        val old = """Contact dataprivacy@weareeverise.com or Legal@weareeverise.com for help."""
        val new = """Contact <a href="mailto:dataprivacy@weareeverise.com">dataprivacy@weareeverise.com</a> or <a href="mailto:Legal@weareeverise.com">Legal@weareeverise.com</a> for help."""
        val (oldSpans, newSpans) = inlineDiffSegments(old, new)
        assertTrue(newSpans.any { it.emphasized && it.text.contains("<a") })
    }
}
