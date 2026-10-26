package com.termfold.app.prreview

sealed class OriginMatch {
    data class Unique(val folderId: String) : OriginMatch()
    data object None : OriginMatch()
    data object Ambiguous : OriginMatch()
}

object GitHubOrigin {
    fun key(owner: String, repo: String): String =
        "github.com/${owner.trim().lowercase()}/${repo.trim().removeSuffix(".git").lowercase()}"

    fun normalize(raw: String): String? {
        var u = raw.trim()
        if (u.isEmpty()) return null
        u = u.removeSuffix("/")
        u = u.removeSuffix(".git")
        sshPattern.matchEntire(u)?.let { match ->
            return key(match.groupValues[1], match.groupValues[2])
        }
        httpsPattern.matchEntire(u)?.let { match ->
            return key(match.groupValues[1], match.groupValues[2])
        }
        return null
    }

    fun match(repoKey: String, folderOrigins: List<Pair<String, String>>): OriginMatch {
        val hits = folderOrigins.mapNotNull { (folderId, origin) ->
            val normalized = normalize(origin) ?: return@mapNotNull null
            if (normalized == repoKey) folderId else null
        }.distinct()
        return when (hits.size) {
            0 -> OriginMatch.None
            1 -> OriginMatch.Unique(hits.single())
            else -> OriginMatch.Ambiguous
        }
    }

    private val sshPattern = Regex(
        """^(?:git@|ssh://git@)github\.com[:/]([^/]+)/([^/]+)$""",
        RegexOption.IGNORE_CASE,
    )
    private val httpsPattern = Regex(
        """^https?://(?:[^/@]+@)?(?:www\.)?github\.com/([^/]+)/([^/]+)$""",
        RegexOption.IGNORE_CASE,
    )
}
