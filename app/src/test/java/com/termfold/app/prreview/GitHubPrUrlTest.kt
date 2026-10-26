package com.termfold.app.prreview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GitHubPrUrlTest {
    @Test
    fun parsesCanonicalPullUrl() {
        val ref = GitHubPrUrl.parse("https://github.com/owner/repo/pull/123")
        assertEquals(GitHubPrRef("owner", "repo", 123), ref)
    }

    @Test
    fun allowsFilesPathAndQuery() {
        val ref = GitHubPrUrl.parse("https://github.com/owner/repo/pull/9/files?w=1")
        assertEquals(GitHubPrRef("owner", "repo", 9), ref)
    }

    @Test
    fun rejectsNonPrText() {
        assertNull(GitHubPrUrl.parse("https://github.com/owner/repo"))
        assertNull(GitHubPrUrl.parse("owner/repo#123"))
        assertNull(GitHubPrUrl.parse("not a url"))
    }
}
