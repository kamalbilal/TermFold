package com.termfold.app.prreview

data class PrReviewSession(
    val id: String,
    val name: String,
    val url: String,
    val owner: String,
    val repo: String,
    val number: Int,
    val headBranch: String,
    val folderId: String,
    val workspaceId: String? = null,
    val tint: Int = 0,
    val acpAgentId: String = "cursor",
) {
    val repoKey: String get() = GitHubOrigin.key(owner, repo)
}

data class PrReviewData(
    val sessions: List<PrReviewSession> = emptyList(),
) {
    fun session(id: String?): PrReviewSession? = sessions.firstOrNull { it.id == id }

    fun upsert(session: PrReviewSession): PrReviewData {
        val index = sessions.indexOfFirst { it.id == session.id }
        if (index < 0) return copy(sessions = sessions + session)
        val next = sessions.toMutableList()
        next[index] = session
        return copy(sessions = next)
    }

    fun without(sessionId: String): PrReviewData =
        copy(sessions = sessions.filterNot { it.id == sessionId })
}
