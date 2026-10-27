package com.termfold.app.pm

import android.content.Context
import com.termfold.app.shell.Projects
import java.io.File

object PmPaths {
    const val GUEST_ROOT = "/root/.termfold/pm-sessions"

    fun sessionGuest(sessionId: String): String =
        "${GUEST_ROOT.trimEnd('/')}/$sessionId"

    fun hostSessionDir(context: Context, sessionId: String): File =
        Projects.hostPath(context, sessionGuest(sessionId))

    fun ensureStub(context: Context, sessionId: String): File {
        val dir = hostSessionDir(context, sessionId)
        dir.mkdirs()
        return dir
    }

    fun memoryStoreName(sessionId: String): String = "pm_memory_$sessionId"
}
