package com.termfold.app.pm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PmResearchPackPromptTest {
    @Test
    fun summarizeKeepsRepoExcerptsAndCompetitiveUrls() {
        val combined = """
            ## Repo pack
            {
              "viewBranch": "main",
              "recentCommits": "abc README",
              "facts": [
                {"claim": "Repo excerpt: README.md", "source": "README.md@main", "excerpt": "Our billing module lives in src/billing"}
              ]
            }

            ## Competitive pack
            {
              "competitors": [
                {"name": "Acme", "url": "https://acme.example", "excerpt": "Acme prices start at 9"}
              ]
            }
        """.trimIndent()
        val summary = PmResearchPack.summarizeForPrompt(combined)
        assertTrue(summary.contains("src/billing"))
        assertTrue(summary.contains("[web: https://acme.example]"))
        assertTrue(summary.contains("Acme prices start at 9"))
        assertTrue(summary.contains("README.md@main"))
    }

    @Test
    fun pickFilesIsRepoAgnostic() {
        val picked = PmResearchPack.pickFiles(
            listOf("README.md", "package.json", "src", "app", "random.bin"),
        )
        assertTrue(picked.contains("README.md"))
        assertTrue(picked.contains("package.json"))
        assertFalse(picked.contains("app/src/main/java"))
    }
}
