package com.termfold.app.prreview

data class GitHubPrRef(
    val owner: String,
    val repo: String,
    val number: Int,
) {
    val repoKey: String get() = GitHubOrigin.key(owner, repo)
}

object GitHubPrUrl {
    private val pattern = Regex(
        """^https?://(?:www\.)?github\.com/([^/]+)/([^/]+)/pull/(\d+)(?:[/?#].*)?$""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(text: String): GitHubPrRef? {
        val trimmed = text.trim()
        val match = pattern.matchEntire(trimmed) ?: return null
        val owner = match.groupValues[1]
        val repo = match.groupValues[2].removeSuffix(".git")
        val number = match.groupValues[3].toIntOrNull() ?: return null
        if (owner.isBlank() || repo.isBlank() || number <= 0) return null
        return GitHubPrRef(owner = owner, repo = repo, number = number)
    }
}
