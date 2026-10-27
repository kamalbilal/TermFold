package com.termfold.app.pm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PmGitQuotesTest {
    @Test
    fun shellQuoteEscapesSingleQuotes() {
        assertEquals("'a'\\''b'", PmGitQuotes.shellQuote("a'b"))
    }

    @Test
    fun validateRefRejectsFlags() {
        assertNotNull(PmGitQuotes.validateRef("-main"))
        assertNull(PmGitQuotes.validateRef("main"))
    }

    @Test
    fun validatePathRejectsNewlines() {
        assertNotNull(PmGitQuotes.validateRepoPath("foo\nbar"))
    }
}
