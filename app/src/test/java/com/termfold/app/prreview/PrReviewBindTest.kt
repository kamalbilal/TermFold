package com.termfold.app.prreview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrReviewBindTest {
    private val ref = GitHubPrRef("owner", "repo", 1)
    private val session = PrReviewSession(
        id = "s1",
        name = "PR",
        url = "https://github.com/owner/repo/pull/1",
        owner = "owner",
        repo = "repo",
        number = 1,
        headBranch = "old",
        folderId = "f1",
        workspaceId = "ws1",
    )

    @Test
    fun missingLocalRepoErrors() {
        val action = PrReviewBind.decide(ref, "feat", OriginMatch.None, null, null, false)
        assertEquals(BindAction.Error(PrReviewBind.ERR_NOT_CLONED), action)
    }

    @Test
    fun firstPrProvisions() {
        val action = PrReviewBind.decide(ref, "feat", OriginMatch.Unique("f1"), null, null, false)
        assertEquals(BindAction.Provision("f1", "feat"), action)
    }

    @Test
    fun secondPrReusesCopy() {
        val action = PrReviewBind.decide(
            incoming = GitHubPrRef("owner", "repo", 2),
            headBranch = "other",
            match = OriginMatch.Unique("f1"),
            session = session,
            copyWorkspaceId = "ws1",
            copyDirty = false,
        )
        assertEquals(BindAction.ReuseSwitch("ws1", "other"), action)
    }

    @Test
    fun dirtyCopyRefusesSwitch() {
        val action = PrReviewBind.decide(ref, "feat", OriginMatch.Unique("f1"), session, "ws1", true)
        assertEquals(BindAction.Error(PrReviewBind.ERR_DIRTY_SWITCH), action)
    }

    @Test
    fun differentRepoRefusesWhileCopyExists() {
        val other = GitHubPrRef("acme", "other", 9)
        val action = PrReviewBind.decide(other, "main", OriginMatch.Unique("f2"), session, "ws1", false)
        assertEquals(BindAction.Error(PrReviewBind.ERR_DIFFERENT_REPO), action)
    }

    @Test
    fun afterDoneProvisionsAgain() {
        val action = PrReviewBind.decide(
            incoming = ref,
            headBranch = "feat",
            match = OriginMatch.Unique("f1"),
            session = session.copy(workspaceId = null),
            copyWorkspaceId = null,
            copyDirty = false,
        )
        assertEquals(BindAction.Provision("f1", "feat"), action)
    }

    @Test
    fun doneKinds() {
        assertEquals(DoneKind.AlreadyGone, PrReviewBind.decideDone(false, dirty = true, unpushed = true))
        assertEquals(DoneKind.ConfirmDirtyOrUnpushed, PrReviewBind.decideDone(true, dirty = true, unpushed = false))
        assertEquals(DoneKind.ConfirmDirtyOrUnpushed, PrReviewBind.decideDone(true, dirty = false, unpushed = true))
        assertEquals(DoneKind.DeleteNow, PrReviewBind.decideDone(true, dirty = false, unpushed = false))
    }

    @Test
    fun recreateClientWhenCwdChanges() {
        assertTrue(PrReviewBind.shouldRecreateClient("/copy-a", "/copy-b"))
        assertTrue(!PrReviewBind.shouldRecreateClient("/copy-a", "/copy-a"))
    }
}
