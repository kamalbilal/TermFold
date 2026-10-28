package com.termfold.app.projectrules

/**
 * Normalizes network git remotes to `host/owner/repo` (lowercase). Local paths and file URLs return null.
 */
object RemoteOriginKey {
    fun normalize(raw: String): String? {
        var u = raw.trim()
        if (u.isEmpty()) return null
        if (u.startsWith("/") || u.startsWith("file://")) return null
        u = u.removeSuffix("/").removeSuffix(".git")
        sshPattern.matchEntire(u)?.let { match ->
            return key(match.groupValues[1], match.groupValues[2])
        }
        httpsPattern.matchEntire(u)?.let { match ->
            return key(match.groupValues[1], match.groupValues[2])
        }
        return null
    }

    /** Accepts full remotes or an already-normalized `host/path` key from Settings. */
    fun normalizeOriginKeyForStorage(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        normalize(trimmed)?.let { return it }
        val bare = trimmed.removeSuffix("/").removeSuffix(".git").trim('/')
        val slash = bare.indexOf('/')
        if (slash <= 0 || slash >= bare.length - 1) return null
        val host = bare.substring(0, slash)
        val path = bare.substring(slash + 1)
        if (!host.contains('.')) return null
        return key(host, path)
    }

    private fun key(host: String, path: String): String? {
        val h = host.trim().lowercase()
        val p = path.trim().trim('/').lowercase()
        if (h.isEmpty() || p.isEmpty()) return null
        return "$h/$p"
    }

    private val sshPattern = Regex(
        """^(?:git@|ssh://git@)([^:/]+)[:/](.+)$""",
        RegexOption.IGNORE_CASE,
    )
    private val httpsPattern = Regex(
        """^https?://(?:[^/@]+@)?([^/]+)/(.+)$""",
        RegexOption.IGNORE_CASE,
    )
}
