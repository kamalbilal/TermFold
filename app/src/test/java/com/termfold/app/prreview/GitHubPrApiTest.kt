package com.termfold.app.prreview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubPrApiTest {
    private val ref = GitHubPrRef("owner", "repo", 12)

    @Test
    fun parsesGhJson() {
        val body = """{"title":"Fix login","headRefName":"fix-login","url":"https://github.com/owner/repo/pull/12","body":"Fixes the form.","baseRefName":"main"}"""
        val result = GitHubPrApi.parseGhBody(body, ref)
        val ok = result as GitHubPrFetch.Ok
        assertEquals("Fix login", ok.info.title)
        assertEquals("fix-login", ok.info.headRef)
        assertEquals("Fixes the form.", ok.info.body)
        assertEquals("main", ok.info.baseRef)
    }

    @Test
    fun parsesHttpHeadRefAndTitle() {
        val body = """{"title":"Fix login","html_url":"https://github.com/owner/repo/pull/12","body":"Fixes the form.","head":{"ref":"fix-login"},"base":{"ref":"develop"}}"""
        val result = GitHubPrApi.parseHttpBody(body, ref)
        val ok = result as GitHubPrFetch.Ok
        assertEquals("Fix login", ok.info.title)
        assertEquals("fix-login", ok.info.headRef)
        assertEquals("Fixes the form.", ok.info.body)
        assertEquals("develop", ok.info.baseRef)
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

    @Test
    fun commentsEmptyWhenBothArraysEmpty() {
        val text = GitHubPrApi.fetchComments(ref) { _ -> 200 to "[]" }
        assertEquals("No GitHub comments.\n", text)
    }

    @Test
    fun commentsMixIssueAndReview() {
        val http = PrHttpGet { url ->
            when {
                url.contains("/issues/") -> 200 to """[{"user":{"login":"ann"},"body":"please test"}]"""
                else -> 200 to """[{"user":{"login":"bob"},"body":"npe here","path":"Foo.kt","line":12}]"""
            }
        }
        val text = GitHubPrApi.fetchComments(ref, http)
        assertTrue(text.contains("@ann"))
        assertTrue(text.contains("please test"))
        assertTrue(text.contains("@bob on Foo.kt:12"))
        assertTrue(text.contains("npe here"))
    }

    @Test
    fun commentsTruncateLongBodiesAndList() {
        val many = buildString {
            append("[")
            repeat(GitHubPrComments.MAX_COMMENTS + 2) { i ->
                if (i > 0) append(",")
                append("""{"user":{"login":"u$i"},"body":"${"x".repeat(GitHubPrComments.MAX_BODY_CHARS + 10)}"}""")
            }
            append("]")
        }
        val text = GitHubPrComments.formatMarkdown(GitHubPrApi.parseCommentArray(many, review = false))
        assertTrue(text.contains("truncated"))
        assertEquals(GitHubPrComments.MAX_COMMENTS, text.lines().count { it.startsWith("### @") })
        val firstBody = text.substringAfter("### @u0\n").substringBefore("\n###")
        assertEquals(GitHubPrComments.MAX_BODY_CHARS, firstBody.trim().length)
    }
}
