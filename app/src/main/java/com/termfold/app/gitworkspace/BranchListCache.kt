package com.termfold.app.gitworkspace

/** In-memory branch picker lists keyed by checkout + catalog guest paths (successful loads only). */
object BranchListCache {
    private val lists = mutableMapOf<String, List<String>>()

    fun cacheKey(checkoutGuestPath: String, catalogGuestPath: String?): String =
        "${checkoutGuestPath.trimEnd('/')}|${catalogGuestPath?.trimEnd('/') ?: ""}"

    fun getSuccessful(key: String): List<String>? = lists[key]

    fun putSuccessful(key: String, branches: List<String>) {
        lists[key] = branches
    }

    fun invalidate(key: String) {
        lists.remove(key)
    }
}

/** Whether a git branch enumeration should run instead of using [cachedBranches]. */
internal fun shouldFetchBranchListFromGit(forceRefresh: Boolean, cachedBranches: List<String>?): Boolean {
    if (forceRefresh) return true
    if (cachedBranches == null) return true
    if (cachedBranches.isEmpty()) return true
    return false
}
