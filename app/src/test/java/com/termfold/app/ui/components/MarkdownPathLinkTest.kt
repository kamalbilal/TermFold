package com.termfold.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownPathLinkTest {
    @Test
    fun filePathRegexMatchesAbsoluteGuestPaths() {
        val path = "/root/.cursor/projects/foo/assets/image-2k.jpg"
        assertTrue(FILE_PATH.containsMatchIn(path))
    }

    @Test
    fun filePathRegexMatchesBasenames() {
        assertTrue(FILE_PATH.containsMatchIn("arabic-talbiyah-minimal-2k.jpg"))
    }

    @Test
    fun inlineEmbedsPathInAnnotatedText() {
        val path = "/root/tmp/demo.kt"
        val ann = inline("see $path", onOpenPath = {})
        assertEquals("see $path", ann.text)
    }

    @Test
    fun backtickWrappedPathsAreLinkified() {
        val path = "/root/.cursor/projects/foo/assets/image.jpg"
        val ann = inline("`$path`", onOpenPath = {})
        assertTrue(ann.text.contains("image.jpg"))
    }
}
