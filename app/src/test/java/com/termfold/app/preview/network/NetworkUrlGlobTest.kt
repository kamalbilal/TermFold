package com.termfold.app.preview.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkUrlGlobTest {
    @Test
    fun exactUrl() {
        val url = "https://cdn.example.com/track.js"
        assertTrue(NetworkUrlGlob.matches(url, url))
        assertFalse(NetworkUrlGlob.matches(url, "https://cdn.example.com/other.js"))
    }

    @Test
    fun basePathWildcard() {
        val pattern = "https://api.foo.com/v1/*"
        assertTrue(NetworkUrlGlob.matches(pattern, "https://api.foo.com/v1/user"))
        assertTrue(NetworkUrlGlob.matches(pattern, "https://api.foo.com/v1/cart"))
        assertFalse(NetworkUrlGlob.matches(pattern, "https://api.foo.com/v2/user"))
    }

    @Test
    fun quotesRegexMetacharacters() {
        assertTrue(NetworkUrlGlob.matches("https://api.foo.com/v1/user.json", "https://api.foo.com/v1/user.json"))
        assertFalse(NetworkUrlGlob.matches("https://api.foo.com/v1/user.json", "https://api.foo.com/v1/userXjson"))
    }

    @Test
    fun firstMatchWinsBlockBeforeOverride() {
        val block = NetworkRule("1", NetworkRuleKind.BLOCK, "https://api.foo.com/*")
        val override = NetworkRule(
            "2",
            NetworkRuleKind.OVERRIDE,
            "https://api.foo.com/v1/user",
            body = "{}",
        )
        val url = "https://api.foo.com/v1/user"
        val winner = listOf(block, override).firstOrNull { NetworkUrlGlob.matches(it.pattern, url) }
        assertEquals(NetworkRuleKind.BLOCK, winner?.kind)
    }
}
