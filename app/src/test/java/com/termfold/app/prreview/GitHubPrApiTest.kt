package com.termfold.app.prreview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubPrApiTest {
    private val ref = GitHubPrRef("owner", "repo", 12)

    @Test
    fun parsesGhJson() {
        val body = """{"title":"Fix login","headRefName":"fix-login","url":"https://github.com/owner/repo/pull/12"}"""
        val result = GitHubPrApi.parseGhBody(body, ref)
        val ok = result as GitHubPrFetch.Ok
        assertEquals("Fix login", ok.info.title)
        assertEquals("fix-login", ok.info.headRef)
    }

    @Test
    fun parsesHttpHeadRefAndTitle() {
        val body = """{"title":"Fix login","html_url":"https://github.com/owner/repo/pull/12","head":{"ref":"fix-login"}}"""
        val result = GitHubPrApi.parseHttpBody(body, ref)
        val ok = result as GitHubPrFetch.Ok
        assertEquals("Fix login", ok.info.title)
        assertEquals("fix-login", ok.info.headRef)
    }

    @Test
    fun unauthorizedDoesNotLookLikeSuccess() {
        val result = GitHubPrApi.fetch(ref) { 401 to """{"message":"Bad credentials"}""" }
        assertTrue(result is GitHubPrFetch.Error)
        assertTrue((result as GitHubPrFetch.Error).message.contains("401"))
    }

    @Test
    fun notFoundIsClearError() {
        val result = GitHubPrApi.fetch(ref) { 404 to "" }
        assertTrue(result is GitHubPrFetch.Error)
        assertTrue((result as GitHubPrFetch.Error).message.contains("404"))
    }

}
