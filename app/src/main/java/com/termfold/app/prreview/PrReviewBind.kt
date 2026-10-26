package com.termfold.app.prreview

sealed class BindAction {
    data class Provision(val folderId: String, val branch: String) : BindAction()
    data class ReuseSwitch(val workspaceId: String, val branch: String) : BindAction()
    data class Error(val message: String) : BindAction()
}

enum class DoneKind {
    DeleteNow,
    ConfirmDirtyOrUnpushed,
    AlreadyGone,
}

object PrReviewBind {
    const val ERR_NOT_CLONED =
        "This PR is for a GitHub repo that is not cloned on this phone. Clone the project under Projects first, then retry."
    const val ERR_AMBIGUOUS =
        "More than one local project has this GitHub origin. Keep a single clone, then retry."
    const val ERR_DIRTY_SWITCH =
        "The temp copy has uncommitted changes. Tap Done or discard them before reviewing another PR."
    const val ERR_DIFFERENT_REPO =
        "This review session is bound to another repository. Tap Done, then paste the new PR, or start a new review."

    fun decide(
        incoming: GitHubPrRef,
        headBranch: String,
        match: OriginMatch,
        session: PrReviewSession?,
        copyWorkspaceId: String?,
        copyDirty: Boolean,
    ): BindAction {
        val folderId = when (match) {
            OriginMatch.None -> return BindAction.Error(ERR_NOT_CLONED)
            OriginMatch.Ambiguous -> return BindAction.Error(ERR_AMBIGUOUS)
            is OriginMatch.Unique -> match.folderId
        }
        if (session != null && session.repoKey != incoming.repoKey && copyWorkspaceId != null) {
            return BindAction.Error(ERR_DIFFERENT_REPO)
        }
        if (copyWorkspaceId != null) {
            if (copyDirty) return BindAction.Error(ERR_DIRTY_SWITCH)
            return BindAction.ReuseSwitch(workspaceId = copyWorkspaceId, branch = headBranch)
        }
        return BindAction.Provision(folderId = folderId, branch = headBranch)
    }

    fun decideDone(copyExists: Boolean, dirty: Boolean, unpushed: Boolean): DoneKind = when {
        !copyExists -> DoneKind.AlreadyGone
        dirty || unpushed -> DoneKind.ConfirmDirtyOrUnpushed
        else -> DoneKind.DeleteNow
    }

    fun shouldRecreateClient(previousGuestDir: String?, nextGuestDir: String): Boolean =
        previousGuestDir != nextGuestDir
}
