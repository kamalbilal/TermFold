package com.termfold.app.gitworkspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SessionWorkspaceMigrationTest {
    private val golden = "/root/projects/migrate-test"

    @Test
    fun moveLegacySessionHostRenamesDirectory() {
        val root = File.createTempFile("termfold-migrate-", null).parentFile!!
        val legacy = File(root, "legacy-${System.nanoTime()}").apply { mkdirs() }
        File(legacy, "marker").writeText("ok")
        val target = File(root, "target-${System.nanoTime()}")
        assertTrue(SessionWorkspaceMigration.moveLegacySessionHost(legacy, target))
        assertTrue(target.isDirectory)
        assertTrue(File(target, "marker").isFile)
        assertFalse(legacy.exists())
        target.deleteRecursively()
    }

    @Test
    fun moveLegacySessionHostFailsWhenTargetExists() {
        val root = File.createTempFile("termfold-migrate-", null).parentFile!!
        val legacy = File(root, "legacy-${System.nanoTime()}").apply { mkdirs() }
        val target = File(root, "target-${System.nanoTime()}").apply { mkdirs() }
        assertFalse(SessionWorkspaceMigration.moveLegacySessionHost(legacy, target))
        assertTrue(legacy.isDirectory)
        legacy.deleteRecursively()
        target.deleteRecursively()
    }

    @Test
    fun targetGuestPathSkipsExternalRecord() {
        val sessionId = "sess-3"
        val externalGuest = ProjectWorkspaces.sessionGuestPath(golden, sessionId)
        val record = sampleRecord(sessionId, externalGuest)
        assertEquals(
            externalGuest,
            SessionWorkspaceMigration.targetGuestPathAfterLegacy(golden, record),
        )
    }

    @Test
    fun targetGuestPathMapsLegacyToExternal() {
        val sessionId = "sess-4"
        val legacyGuest = "${golden}/${ProjectWorkspaces.LEGACY_SESSIONS_DIR}/$sessionId"
        val record = sampleRecord(sessionId, legacyGuest)
        assertEquals(
            ProjectWorkspaces.sessionGuestPath(golden, sessionId),
            SessionWorkspaceMigration.targetGuestPathAfterLegacy(golden, record),
        )
    }

    private fun sampleRecord(sessionId: String, guestPath: String) = WorkspaceRecord(
        id = sessionId,
        goldenProjectId = "folder-1",
        guestPath = guestPath,
        createdAt = 0L,
        sourceBranch = "main",
        lastKnownBranch = "main",
        complete = true,
        label = null,
    )
}
