package com.termfold.app.gitworkspace

/**
 * Upstream tracking for session workspace copies: only set when [originBranchExists]
 * after fetch; otherwise clear stale merge config copied from golden.
 */
object SessionCopyUpstreamRepair {

    enum class Action {
        SET_UPSTREAM_TO_ORIGIN,
        UNSET_UPSTREAM,
        NONE,
    }

    fun action(
        originBranchExists: Boolean,
        isCheckedOutBranch: Boolean,
        fetchSucceeded: Boolean = true,
    ): Action = when {
        !fetchSucceeded -> Action.NONE
        originBranchExists -> Action.SET_UPSTREAM_TO_ORIGIN
        isCheckedOutBranch -> Action.UNSET_UPSTREAM
        else -> Action.NONE
    }

    fun branchArgs(branch: String, repair: Action): String? = when (repair) {
        Action.SET_UPSTREAM_TO_ORIGIN -> "branch --set-upstream-to=origin/${quote(branch)} ${quote(branch)}"
        Action.UNSET_UPSTREAM -> "branch --unset-upstream"
        Action.NONE -> null
    }

    private fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
