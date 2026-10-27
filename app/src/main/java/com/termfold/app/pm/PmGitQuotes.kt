package com.termfold.app.pm

object PmGitQuotes {
    fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"

    /** Reject refs that could break out of a quoted shell argument or run extra git flags. */
    fun validateRef(ref: String): String? {
        val trimmed = ref.trim()
        if (trimmed.isEmpty()) return "Ref is empty"
        if (trimmed.startsWith("-")) return "Ref cannot start with -"
        if (trimmed.contains('\n') || trimmed.contains('\r')) return "Ref cannot contain newlines"
        if (trimmed.contains('`') || trimmed.contains('$')) return "Ref contains invalid characters"
        return null
    }

    fun validateRepoPath(path: String): String? {
        val trimmed = path.trim()
        if (trimmed.isEmpty()) return "Path is empty"
        if (trimmed.startsWith("-")) return "Path cannot start with -"
        if (trimmed.contains('\n') || trimmed.contains('\r')) return "Path cannot contain newlines"
        return null
    }
}
