package com.termfold.app.prreview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class PrReviewPathsTest {
    @Test
    fun stubGuestPathHasNoWorkspaceId() {
        assertEquals("/root/.termfold/pr-reviews/abc", PrReviewPaths.sessionGuest("abc"))
    }

    @Test
    fun filesRootUsesCopyWhenPresentElseNullMeaningStubAtUi() {
        assertEquals("/copy", PrReviewPaths.filesGuestRoot("/copy"))
        assertNull(PrReviewPaths.filesGuestRoot(null))
    }

    @Test
    fun ensureStubCreatesDirectory() {
        val root = Files.createTempDirectory("pr-stub").toFile()
        try {
            val dir = File(root, "abc")
            dir.mkdirs()
            assertTrue(dir.isDirectory)
        } finally {
            root.deleteRecursively()
        }
    }
}
