package com.termfold.app.gitworkspace

import android.content.Context
import com.termfold.app.shell.Projects
import java.io.File

object SessionWorkspaceMigration {
    /**
     * Guest path after legacy layout normalization; null when [record] is not a migratable legacy session.
     */
    internal fun targetGuestPathAfterLegacy(goldenGuestPath: String, record: WorkspaceRecord): String? {
        if (!record.complete) return null
        if (ProjectWorkspaces.isExternalManagedSessionPath(goldenGuestPath, record.guestPath)) {
            return record.guestPath
        }
        if (!ProjectWorkspaces.isLegacyManagedSessionPath(goldenGuestPath, record.guestPath)) {
            return null
        }
        val sessionId = ProjectWorkspaces.legacySessionId(goldenGuestPath, record.guestPath) ?: return null
        return ProjectWorkspaces.sessionGuestPath(goldenGuestPath, sessionId)
    }

    internal fun moveLegacySessionHost(legacyHost: File, targetHost: File): Boolean {
        if (!legacyHost.isDirectory) return false
        if (targetHost.exists()) return false
        targetHost.parentFile?.mkdirs()
        return legacyHost.renameTo(targetHost)
    }

    /**
     * Moves a legacy in-repo session directory to the external session root when possible.
     * Returns the record unchanged when migration does not apply or fails.
     */
    fun migrateIfNeeded(
        context: Context,
        goldenGuestPath: String,
        record: WorkspaceRecord,
    ): WorkspaceRecord {
        val targetGuest = targetGuestPathAfterLegacy(goldenGuestPath, record) ?: return record
        if (record.guestPath == targetGuest) return record

        val legacyHost = Projects.hostPath(context, record.guestPath)
        if (!legacyHost.isDirectory) return record

        val targetHost = Projects.hostPath(context, targetGuest)
        if (targetHost.exists()) {
            return if (targetHost.isDirectory) record.copy(guestPath = targetGuest) else record
        }
        if (!moveLegacySessionHost(legacyHost, targetHost)) return record
        return record.copy(guestPath = targetGuest)
    }
}
