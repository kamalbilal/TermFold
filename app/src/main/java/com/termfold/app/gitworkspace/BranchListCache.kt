package com.termfold.app.gitworkspace

/** In-memory branch picker lists keyed by checkout + catalog guest paths. */
object BranchListCache {
    private val lists = mutableMapOf<String, List<String>>()

    fun cacheKey(checkoutGuestPath: String, catalogGuestPath: String?): String =
        "${checkoutGuestPath.trimEnd('/')}|${catalogGuestPath?.trimEnd('/') ?: ""}"

    fun get(key: String): List<String>? = lists[key]

    fun put(key: String, branches: List<String>) {
        lists[key] = branches
    }

    fun invalidate(key: String) {
        lists.remove(key)
    }
}
