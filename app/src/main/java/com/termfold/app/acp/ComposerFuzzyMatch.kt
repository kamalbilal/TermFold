package com.termfold.app.acp

import me.xdrop.fuzzywuzzy.FuzzySearch

/**
 * Ranks composer menu candidates with fuzzy string matching ([FuzzySearch]).
 */
object ComposerFuzzyMatch {
    /** Below this score, a row is dropped when the query is non-empty. */
    const val MIN_SCORE = 58

    fun score(query: String, candidate: String): Int {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return 0
        if (candidate.isEmpty()) return 0
        val c = candidate.lowercase()
        val ratio = FuzzySearch.ratio(q, c)
        val partial = FuzzySearch.partialRatio(q, c)
        val token = FuzzySearch.tokenSetRatio(q, c)
        var best = maxOf(ratio, partial, token)
        if (c == q) best = 100
        else if (c.startsWith(q)) best = 100
        val idx = c.indexOf(q)
        if (idx >= 0) best = maxOf(best, (94 - idx.coerceAtMost(36)).coerceAtLeast(MIN_SCORE))
        for (segment in c.split('/', '\\', '.', '-', '_').filter { it.isNotEmpty() }) {
            if (segment == q) best = maxOf(best, 99)
            if (segment.startsWith(q)) best = maxOf(best, 95)
        }
        return best.coerceIn(0, 100)
    }

    /**
     * Lower sorts earlier in branch-picker lists: full-name exact, prefix, segment exact,
     * segment prefix, then substring index, then fuzzy-only.
     */
    fun matchTier(query: String, candidate: String): Int {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return 0
        val c = candidate.lowercase()
        if (c == q) return 0
        if (c.startsWith(q)) return 1
        for (segment in c.split('/', '\\', '.', '-', '_').filter { it.isNotEmpty() }) {
            if (segment == q) return 2
            if (segment.startsWith(q)) return 3
        }
        val idx = c.indexOf(q)
        if (idx >= 0) return 4 + idx.coerceAtMost(500)
        return 10_000
    }

    fun <T> rank(
        items: Sequence<T>,
        query: String,
        limit: Int,
        minScore: Int = MIN_SCORE,
        searchable: (T) -> String,
    ): List<T> {
        val q = query.trim()
        if (q.isEmpty()) {
            return items
                .sortedBy { searchable(it).lineSequence().first().lowercase() }
                .take(limit)
                .toList()
        }
        return items
            .map { item ->
                val fields = searchable(item)
                val best = fields
                    .split('\n')
                    .filter { it.isNotBlank() }
                    .maxOfOrNull { score(q, it) }
                    ?: 0
                item to best
            }
            .filter { it.second >= minScore }
            .sortedWith(
                compareBy<Pair<T, Int>> {
                    matchTier(q, searchable(it.first).lineSequence().first())
                }
                    .thenByDescending { it.second }
                    .thenBy { primaryMatchStart(q, searchable(it.first)) }
                    .thenBy { searchable(it.first).lineSequence().first().length }
                    .thenBy { searchable(it.first).lineSequence().first().lowercase() },
            )
            .take(limit)
            .map { it.first }
            .toList()
    }

    /** Lower is better: index of [query] in the primary searchable line, or last if none. */
    private fun primaryMatchStart(query: String, searchable: String): Int {
        val q = query.lowercase()
        val primary = searchable.lineSequence().first().lowercase()
        if (primary.startsWith(q)) return 0
        val idx = primary.indexOf(q)
        return if (idx >= 0) idx else Int.MAX_VALUE
    }
}
