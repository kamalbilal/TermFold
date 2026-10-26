package com.termfold.app.prreview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubOriginTest {
    @Test
    fun httpsGitAndSshNormalizeTheSame() {
        val expected = "github.com/owner/repo"
        assertEquals(expected, GitHubOrigin.normalize("https://github.com/owner/repo.git"))
        assertEquals(expected, GitHubOrigin.normalize("git@github.com:owner/repo.git"))
        assertEquals(expected, GitHubOrigin.normalize("https://github.com/Owner/Repo"))
        assertEquals(expected, GitHubOrigin.normalize("https://user:token@github.com/owner/repo.git"))
    }

    @Test
    fun uniqueMatch() {
        val match = GitHubOrigin.match(
            "github.com/owner/repo",
            listOf("a" to "git@github.com:owner/repo.git", "b" to "https://github.com/other/x.git"),
        )
        assertEquals(OriginMatch.Unique("a"), match)
    }

    @Test
    fun noMatch() {
        assertEquals(
            OriginMatch.None,
            GitHubOrigin.match("github.com/owner/repo", listOf("a" to "https://github.com/other/x.git")),
        )
    }

    @Test
    fun twoFolderClash() {
        val match = GitHubOrigin.match(
            "github.com/owner/repo",
            listOf(
                "a" to "https://github.com/owner/repo.git",
                "b" to "git@github.com:owner/repo.git",
            ),
        )
        assertTrue(match is OriginMatch.Ambiguous)
    }
}
