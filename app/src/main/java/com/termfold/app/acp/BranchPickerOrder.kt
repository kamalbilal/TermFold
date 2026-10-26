package com.termfold.app.acp

/** Branch list ordering for picker dialogs (recents, search, exact-before-fuzzy). */
object BranchPickerOrder {

    fun order(
        all: List<String>,
        recent: List<String>,
        query: String,
    ): List<String> {
        if (query.isBlank()) {
            val recentHead = recent.filter { it in all }
            return recentHead + all.filter { it !in recentHead }
        }
        val recentSet = recent.toSet()
        return all
            .map { name -> name to ComposerFuzzyMatch.score(query, name) }
            .filter { it.second >= ComposerFuzzyMatch.MIN_SCORE }
            .sortedWith(
                compareBy<Pair<String, Int>> { ComposerFuzzyMatch.matchTier(query, it.first) }
                    .thenByDescending { it.second }
                    .thenByDescending { if (it.first in recentSet) 1 else 0 }
                    .thenBy { it.first.lowercase() },
            )
            .map { it.first }
    }
}
