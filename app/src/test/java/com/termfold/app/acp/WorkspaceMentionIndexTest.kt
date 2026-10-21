package com.termfold.app.acp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WorkspaceMentionIndexTest {
    @Test
    fun collectAndFilterWorkspacePaths() {
        val root = File(System.getProperty("java.io.tmpdir"), "mention-index-${System.nanoTime()}").apply { mkdirs() }
        val src = File(root, "src").apply { mkdirs() }
        File(src, "App.kt").writeText("x")
        File(root, "README.md").writeText("y")
        try {
            val collected = WorkspaceMentionIndex.collectFromRoot(root)
            val names = collected.map { it.relativePath }
            assertTrue(names.contains("README.md"))
            assertTrue(names.contains("src"))
            assertTrue(names.contains("src/App.kt"))
            val filtered = WorkspaceMentionIndex.filterEntries(collected, "app", 10)
            assertEquals("src/App.kt", filtered.first().relativePath)
            val filesOnly = collected.filter { !it.isDirectory }
            val fileSearch = WorkspaceMentionIndex.filterEntries(filesOnly, "readme", 10)
            assertEquals("README.md", fileSearch.first().relativePath)
            val lines = WorkspaceMentionIndex.displayLines("src/App.kt")
            assertEquals("App.kt", lines.mentionName)
            assertEquals("src/App.kt", lines.fullPathFromRoot)
            val rootFile = WorkspaceMentionIndex.displayLines("README.md")
            assertEquals("README.md", rootFile.mentionName)
            assertEquals("README.md", rootFile.fullPathFromRoot)
        } finally {
            root.deleteRecursively()
        }
    }
}
