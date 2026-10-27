package com.termfold.app.pm

import com.termfold.app.prreview.DoneKind
import org.junit.Assert.assertEquals
import org.junit.Test

class PmBindTest {
    @Test
    fun doneKindMatchesPrReviewer() {
        assertEquals(DoneKind.DeleteNow, PmBind.decideDone(copyExists = true, dirty = false, unpushed = false))
        assertEquals(DoneKind.ConfirmDirtyOrUnpushed, PmBind.decideDone(copyExists = true, dirty = true, unpushed = false))
        assertEquals(DoneKind.AlreadyGone, PmBind.decideDone(copyExists = false, dirty = false, unpushed = false))
    }
}
