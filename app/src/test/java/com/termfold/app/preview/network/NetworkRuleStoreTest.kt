package com.termfold.app.preview.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.io.path.createTempDirectory

class NetworkRuleStoreTest {
    private fun store(): NetworkRuleStore {
        val dir = createTempDirectory("network-rules").toFile()
        dir.deleteOnExit()
        return NetworkRuleStore(dir)
    }

    @Test
    fun isolationAndRestore() {
        val dir = createTempDirectory("network-rules").toFile()
        val store = NetworkRuleStore(dir)
        store.addBlock("coding-a", "https://api.example.com/*")
        store.addOverride("coding-b", "https://api.example.com/*", "{\"from\":\"b\"}")
        assertEquals(1, store.list("coding-a").size)
        assertEquals(NetworkRuleKind.BLOCK, store.list("coding-a").single().kind)
        assertEquals("{\"from\":\"b\"}", store.list("coding-b").single().body)
        val restored = NetworkRuleStore(dir)
        assertEquals("https://api.example.com/*", restored.list("coding-a").single().pattern)
        assertEquals("{\"from\":\"b\"}", restored.list("coding-b").single().body)
    }

    @Test
    fun deleteSessionRemovesOnlyThatKey() {
        val store = store()
        store.addBlock("coding-a", "*://a.com/*")
        store.addBlock("coding-b", "*://b.com/*")
        store.deleteSession("coding-a")
        assertTrue(store.list("coding-a").isEmpty())
        assertEquals(1, store.list("coding-b").size)
    }

    @Test
    fun firstMatchUsesListOrder() {
        val store = store()
        store.addBlock("s", "https://api.foo.com/*")
        store.addOverride("s", "https://api.foo.com/v1/user", "{}")
        assertEquals(NetworkRuleKind.BLOCK, store.firstMatch("s", "https://api.foo.com/v1/user")?.kind)
    }

    @Test
    fun slashAndUnderscoreKeysDoNotCollide() {
        val store = store()
        store.addBlock("coding-a/b", "https://slash.test/*")
        store.addBlock("coding-a_b", "https://under.test/*")
        assertEquals("https://slash.test/*", store.list("coding-a/b").single().pattern)
        assertEquals("https://under.test/*", store.list("coding-a_b").single().pattern)
    }

    @Test
    fun overrideBodyCap() {
        val store = store()
        val tooBig = "x".repeat(NetworkRuleStore.MAX_BODY_BYTES + 1)
        val thrown = runCatching { store.addOverride("s", "https://x/", tooBig) }.exceptionOrNull()
        assertTrue(thrown is IllegalArgumentException)
        assertNull(store.firstMatch("s", "https://x/"))
    }

}
