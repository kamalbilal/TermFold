package com.termfold.app.automation.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryChunkFiltersTest {
    private fun chunk(
        sessionId: String = "",
        site: String = "shop.com",
        kind: String = "note",
        title: String = "t",
        body: String = "b",
        updated: Long = 1L,
    ) = MemoryChunkEntity(
        automationSessionId = sessionId,
        siteKey = site,
        kind = kind,
        title = title,
        body = body,
        updatedAtMs = updated,
    )

    @Test
    fun globalScopeExcludesSessionChunks() {
        val all = listOf(
            chunk(sessionId = "", site = "a.com", updated = 2),
            chunk(sessionId = "proj:s1", site = "a.com", updated = 3),
        )
        val result = MemoryChunkFilters.filter(all, global = true, sessionKey = null)
        assertEquals(1, result.size)
        assertTrue(result.single().automationSessionId.isEmpty())
    }

    @Test
    fun sessionScopeMatchesKey() {
        val all = listOf(
            chunk(sessionId = "p:s1", title = "one"),
            chunk(sessionId = "p:s2", title = "two"),
        )
        val result = MemoryChunkFilters.filter(all, global = false, sessionKey = "p:s1")
        assertEquals("one", result.single().title)
    }

    @Test
    fun siteAndKindFiltersAreSubstring() {
        val all = listOf(
            chunk(site = "mail.example.com", kind = "selector"),
            chunk(site = "other.com", kind = "note"),
        )
        val result = MemoryChunkFilters.filter(
            all,
            global = true,
            sessionKey = null,
            siteKeyFilter = "example",
            kindFilter = "select",
        )
        assertEquals(1, result.size)
        assertEquals("mail.example.com", result.single().siteKey)
    }

    @Test
    fun textFilterMatchesBody() {
        val all = listOf(chunk(body = "click the checkout button"), chunk(body = "unrelated"))
        val result = MemoryChunkFilters.filter(
            all,
            global = true,
            sessionKey = null,
            textFilter = "checkout",
        )
        assertEquals(1, result.size)
    }
}
