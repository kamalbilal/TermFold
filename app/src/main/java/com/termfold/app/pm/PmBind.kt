package com.termfold.app.pm

import com.termfold.app.prreview.DoneKind

object PmBind {
    fun decideDone(copyExists: Boolean, dirty: Boolean, unpushed: Boolean): DoneKind = when {
        !copyExists -> DoneKind.AlreadyGone
        dirty || unpushed -> DoneKind.ConfirmDirtyOrUnpushed
        else -> DoneKind.DeleteNow
    }
}
