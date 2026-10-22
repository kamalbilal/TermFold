package com.termfold.app.gitworkspace

import android.content.Context
import com.termfold.app.shell.Projects
import java.io.File
import java.security.MessageDigest
import java.util.UUID

object ProjectWorkspaces {
    /** Legacy in-repo layout (pre external-session-storage). */
    const val LEGACY_SESSIONS_DIR = ".termfold/sessions"

    /** Guest root for session workspace clones (outside golden project trees). */
    const val EXTERNAL_SESSIONS_ROOT = "/root/.termfold/project-sessions"

    fun newSessionId(): String = UUID.randomUUID().toString()

    fun normalizeGoldenGuestPath(goldenGuestPath: String): String {
        val trimmed = goldenGuestPath.trimEnd('/')
        return trimmed.ifEmpty { "/" }
    }

    fun projectKey(goldenGuestPath: String): String {
        val normalized = normalizeGoldenGuestPath(goldenGuestPath)
        val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    fun externalSessionsRoot(goldenGuestPath: String): String =
        "${EXTERNAL_SESSIONS_ROOT.trimEnd('/')}/${projectKey(goldenGuestPath)}"

    fun legacySessionsRoot(goldenGuestPath: String): String =
        "${normalizeGoldenGuestPath(goldenGuestPath)}/$LEGACY_SESSIONS_DIR"

    fun sessionGuestPath(goldenGuestPath: String, sessionId: String): String {
        val base = externalSessionsRoot(goldenGuestPath).trimEnd('/')
        return "$base/$sessionId"
    }

    fun sessionsRoot(goldenGuestPath: String): String = externalSessionsRoot(goldenGuestPath)

    fun isLegacyManagedSessionPath(goldenGuestPath: String, guestPath: String): Boolean {
        val root = legacySessionsRoot(goldenGuestPath).trimEnd('/') + "/"
        val path = guestPath.trimEnd('/') + "/"
        return path.startsWith(root) && path.length > root.length
    }

    fun isExternalManagedSessionPath(goldenGuestPath: String, guestPath: String): Boolean {
        val root = externalSessionsRoot(goldenGuestPath).trimEnd('/') + "/"
        val path = guestPath.trimEnd('/') + "/"
        return path.startsWith(root) && path.length > root.length
    }

    /**
     * True when [guestPath] is a session workspace under the external root or legacy in-repo tree.
     */
    fun isManagedSessionPath(goldenGuestPath: String, guestPath: String): Boolean =
        isExternalManagedSessionPath(goldenGuestPath, guestPath) ||
            isLegacyManagedSessionPath(goldenGuestPath, guestPath)

    /** Session copy path without the golden guest path (lazy upstream repair, changes pane). */
    fun looksLikeSessionWorkspace(guestPath: String): Boolean {
        val normalized = guestPath.trimEnd('/')
        val externalPrefix = EXTERNAL_SESSIONS_ROOT.trimEnd('/') + "/"
        if (normalized.startsWith(externalPrefix)) {
            val rest = normalized.removePrefix(externalPrefix)
            return rest.isNotEmpty() && rest.contains('/')
        }
        return normalized.contains("/$LEGACY_SESSIONS_DIR/")
    }

    fun legacySessionId(goldenGuestPath: String, legacyGuestPath: String): String? {
        val root = legacySessionsRoot(goldenGuestPath).trimEnd('/') + "/"
        val path = legacyGuestPath.trimEnd('/') + "/"
        if (!path.startsWith(root)) return null
        val id = path.removePrefix(root).trimEnd('/')
        if (id.isEmpty() || id.contains('/')) return null
        return id
    }

    fun ensureSessionStorageDirs(context: Context, goldenGuestPath: String) {
        Projects.hostPath(context, EXTERNAL_SESSIONS_ROOT.trimEnd('/')).mkdirs()
        Projects.hostPath(context, externalSessionsRoot(goldenGuestPath)).mkdirs()
    }

    fun hostSessionDir(context: Context, goldenGuestPath: String, sessionId: String): File {
        val guest = sessionGuestPath(goldenGuestPath, sessionId)
        return Projects.hostPath(context, guest)
    }

    fun deleteTree(dir: File) {
        if (!dir.exists()) return
        dir.walkBottomUp().forEach { it.delete() }
    }
}
