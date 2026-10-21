package com.termfold.app.gitworkspace

/**
 * Branch labels from `git for-each-ref` and the `git switch` targets that actually work
 * on a fresh local clone (local branch vs create-and-track from remote).
 */
object BranchNames {
    fun canonicalLocalName(picked: String): String =
        picked.trim().removePrefix("origin/").trim()

    fun normalizeForPicker(refs: List<String>): List<String> {
        val locals = mutableSetOf<String>()
        val remoteOnly = mutableSetOf<String>()
        for (raw in refs) {
            val ref = raw.trim()
            if (ref.isBlank() || ref.endsWith("/HEAD")) continue
            val slash = ref.indexOf('/')
            if (slash < 0) {
                if (ref.equals("origin", ignoreCase = true)) continue
                locals.add(ref)
                continue
            }
            val name = ref.substring(slash + 1)
            if (name.isBlank() || name == "HEAD" || name.equals("origin", ignoreCase = true)) continue
            if (name in locals) continue
            remoteOnly.add(name)
        }
        return (locals + remoteOnly).sorted()
    }

    fun switchArgs(localName: String, hasLocalHead: Boolean, hasOriginRemote: Boolean): List<String> {
        if (localName.isBlank()) return emptyList()
        val attempts = mutableListOf<String>()
        if (hasLocalHead) {
            attempts.add("switch ${shellQuote(localName)}")
        }
        if (hasOriginRemote) {
            val remote = "origin/$localName"
            attempts.add("switch -c ${shellQuote(localName)} --track ${shellQuote(remote)}")
            attempts.add("checkout -B ${shellQuote(localName)} ${shellQuote(remote)}")
        }
        return attempts.distinct()
    }

    fun importBranchFetchCommands(localName: String): List<String> {
        val n = canonicalLocalName(localName)
        if (n.isBlank()) return emptyList()
        val remote = "refs/remotes/origin/$n"
        val local = "refs/heads/$n"
        return listOf(
            "fetch origin ${shellQuote(remote)}:${shellQuote(remote)}",
            "fetch origin ${shellQuote(local)}:${shellQuote(remote)}",
            "fetch origin ${shellQuote(local)}:${shellQuote(local)}",
        )
    }

    private fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"
}
