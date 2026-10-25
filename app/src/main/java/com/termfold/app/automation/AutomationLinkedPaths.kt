package com.termfold.app.automation

import android.content.Context
import com.termfold.app.core.Folder
import com.termfold.app.shell.Projects
import java.io.File

object AutomationLinkedPaths {
    fun linkedGuestRoot(context: Context, folder: Folder?): String? {
        if (folder == null) return null
        return Projects.guestPath(context, folder.path) ?: folder.path.takeIf { it.startsWith("/") }
    }

    fun isReadOnlyLinkedPath(context: Context, guestPath: String, linkedFolder: Folder?): Boolean =
        AutomationReadOnlyPolicy.isUnderReadOnlyRoot(
            linkedGuestRoot(context, linkedFolder),
            guestPath,
        )

    fun previewProjectDir(
        context: Context,
        project: AutomationProject,
        sessionId: String,
        linkedFolder: Folder?,
    ): String {
        val linked = linkedGuestRoot(context, linkedFolder)
        if (linked != null) return linked
        return AutomationPaths.sessionGuest(project.id, sessionId)
    }

    fun sessionWorkspaceGuest(projectId: String, sessionId: String): String =
        AutomationPaths.sessionGuest(projectId, sessionId)
}
