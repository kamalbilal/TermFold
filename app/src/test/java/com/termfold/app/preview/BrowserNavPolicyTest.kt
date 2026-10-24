package com.termfold.app.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowserNavPolicyTest {

    @Test
    fun normalizeHostStripsSchemeAndPath() {
        assertEquals("api.foo.com", BrowserNavPolicy.normalizeHost("https://API.FOO.com/path"))
        assertEquals("localhost", BrowserNavPolicy.normalizeHost("localhost:5173"))
    }

    @Test
    fun checkAgentOpenAllowsPublicHosts() {
        assertNull(BrowserNavPolicy.checkAgentOpen("https://www.google.com/"))
        assertNull(BrowserNavPolicy.checkAgentOpen("http://127.0.0.1:5173/"))
    }
}
