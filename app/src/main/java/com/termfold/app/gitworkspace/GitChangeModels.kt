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

data class GitChangeSummary(
    val branch: String,
    val upstream: String?,
    val outgoingCommits: Int?,
    val incomingCommits: Int?,
    val staged: List<GitChangeFile>,
    val unstaged: List<GitChangeFile>,
)
