package com.termfold.app.shell

import org.junit.Assert.assertEquals
import org.junit.Test

class GuestStorageAnalyzerTest {

    @Test
    fun parseDuOutputReadsTabSeparatedLines() {
        val text = """
            1048576	/root/projects/myapp
            2048	/var/cache/apt
        """.trimIndent()
        val parsed = GuestStorageAnalyzer.parseDuOutput(text)
        assertEquals(2, parsed.size)
        assertEquals("/root/projects/myapp", parsed[0].first)
        assertEquals(1_048_576L, parsed[0].second)
    }

    @Test
    fun parseDuChildrenKeepsDirectSubfoldersOnly() {
        val lines = listOf(
            "/root" to 1_000L,
            "/root/projects" to 800L,
            "/root/.cache" to 50L,
            "/root/projects/app" to 700L,
        )
        val children = GuestStorageAnalyzer.parseDuChildren("/root", lines)
        val paths = children.map { it.first }.toSet()
        assertEquals(setOf("/root/projects", "/root/.cache"), paths)
    }
}
