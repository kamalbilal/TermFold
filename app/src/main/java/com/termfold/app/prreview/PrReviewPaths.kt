package com.termfold.app.prreview

import android.content.Context
import com.termfold.app.shell.Projects
import java.io.File

object PrReviewPaths {
    const val GUEST_ROOT = "/root/.termfold/pr-reviews"

    fun sessionGuest(reviewSessionId: String): String =
        "${GUEST_ROOT.trimEnd('/')}/$reviewSessionId"

    fun hostSessionDir(context: Context, reviewSessionId: String): File =
        Projects.hostPath(context, sessionGuest(reviewSessionId))

    fun ensureStub(context: Context, reviewSessionId: String): File {
        val dir = hostSessionDir(context, reviewSessionId)
        dir.mkdirs()
        return dir
    }

    fun filesGuestRoot(copyGuestPath: String?): String? = copyGuestPath
}
