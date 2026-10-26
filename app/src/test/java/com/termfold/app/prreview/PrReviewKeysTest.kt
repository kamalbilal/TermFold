package com.termfold.app.prreview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PrReviewKeysTest {
    @Test
    fun acpKeyIgnoresWorkspaceId() {
        val key = PrReviewKeys.acpSessionKey("abc")
        assertEquals("pr-review/abc", key)
        assertFalse(key.contains("workspace"))
        assertEquals(key, PrReviewKeys.acpSessionKey("abc"))
    }
}
