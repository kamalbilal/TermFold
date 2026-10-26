package com.termfold.app.acp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposerFuzzyMatchTest {
    @Test
    fun partialMatchOnSubstring() {
        val score = ComposerFuzzyMatch.score("bkapi", "BACKEND_API_RESPONSE")
        assertTrue(score >= ComposerFuzzyMatch.MIN_SCORE)
    }

    @Test
    fun rankPrefersBetterMatch() {
        val names = listOf("init", "resume", "new")
        val ranked = ComposerFuzzyMatch.rank(
            items = names.asSequence(),
            query = "res",
            limit = 5,
            searchable = { it },
        )
        assertEquals("resume", ranked.first())
    }

    @Test
    fun emptyQuerySortsAlphabetically() {
        val items = listOf("zebra", "alpha", "middle")
        val ranked = ComposerFuzzyMatch.rank(
            items = items.asSequence(),
            query = "",
            limit = 10,
            searchable = { it },
        )
        assertEquals(listOf("alpha", "middle", "zebra"), ranked)
    }

    @Test
    fun rankPrefersPrefixOnName() {
        val ranked = ComposerFuzzyMatch.rank(
            items = listOf("supervisor", "summarize", "resume").asSequence(),
            query = "sum",
            limit = 5,
            searchable = { it },
        )
        assertEquals("summarize", ranked.first())
    }

    @Test
    fun matchTierExactBeforeFuzzy() {
        assertTrue(ComposerFuzzyMatch.matchTier("main", "main") < ComposerFuzzyMatch.matchTier("main", "feature/main-qa"))
        assertTrue(ComposerFuzzyMatch.matchTier("kamal", "kamal-bilal") < ComposerFuzzyMatch.matchTier("kamal", "remove-posthog-alorica-prod-kamal-bilal"))
        assertEquals(0, ComposerFuzzyMatch.matchTier("main-qa", "main-qa"))
    }
}
