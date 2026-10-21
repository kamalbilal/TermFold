package com.termfold.app.gitworkspace

import com.termfold.app.shell.Projects
import java.io.File
import java.util.UUID

object ProjectWorkspaces {
    const val SESSIONS_DIR = ".termfold/sessions"

    fun newSessionId(): String = UUID.randomUUID().toString()

    fun sessionGuestPath(goldenGuestPath: String, sessionId: String): String {
        val base = goldenGuestPath.trimEnd('/')
        return "$base/$SESSIONS_DIR/$sessionId"
    }

    fun sessionsRoot(goldenGuestPath: String): String =
        "${goldenGuestPath.trimEnd('/')}/$SESSIONS_DIR"

    /**
     * True when [guestPath] is a session workspace directory under [goldenGuestPath]'s sessions tree.
     */
    fun isManagedSessionPath(goldenGuestPath: String, guestPath: String): Boolean {
        val root = sessionsRoot(goldenGuestPath).trimEnd('/') + "/"
        val path = guestPath.trimEnd('/') + "/"
        return path.startsWith(root) && path.length > root.length
    }

    fun hostSessionDir(context: android.content.Context, goldenGuestPath: String, sessionId: String): File {
        val guest = sessionGuestPath(goldenGuestPath, sessionId)
        return Projects.hostPath(context, guest)
    }

    fun deleteTree(dir: File) {
        if (!dir.exists()) return
        dir.walkBottomUp().forEach { it.delete() }
    }
}
