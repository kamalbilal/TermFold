package com.termfold.app.gitworkspace

enum class GitFileStatus {
    ADDED,
    MODIFIED,
    DELETED,
    RENAMED,
    COPIED,
    UNTRACKED,
    UNMERGED,
}

data class GitChangeFile(
    val path: String,
    val status: GitFileStatus,
    val additions: Int?,
    val deletions: Int?,
    val staged: Boolean,
)

/** True when the changes pane should show incoming/outgoing sync counts (matches [SyncLine] rules). */
fun gitSyncCountsVisible(summary: GitChangeSummary): Boolean {
    if (summary.upstream == null) return false
    val out = summary.outgoingCommits ?: 0
    val inc = summary.incomingCommits ?: 0
    return out > 0 || inc > 0
}

data class GitChangeSummary(
    val branch: String,
    val upstream: String?,
    val outgoingCommits: Int?,
    val incomingCommits: Int?,
    val staged: List<GitChangeFile>,
    val unstaged: List<GitChangeFile>,
)
