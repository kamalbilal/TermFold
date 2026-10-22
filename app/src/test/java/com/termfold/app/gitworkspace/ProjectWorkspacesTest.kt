package com.termfold.app.gitworkspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectWorkspacesTest {
    private val golden = "/root/projects/app"

    @Test
    fun projectKeyStableForSameGoldenPath() {
        val a = ProjectWorkspaces.projectKey(golden)
        val b = ProjectWorkspaces.projectKey("$golden/")
        assertEquals(a, b)
        assertEquals(64, a.length)
    }

    @Test
    fun projectKeyDiffersForDistinctGoldenPaths() {
        val a = ProjectWorkspaces.projectKey("/root/projects/app-a")
        val b = ProjectWorkspaces.projectKey("/root/projects/app-b")
        assertNotEquals(a, b)
    }

    @Test
    fun sessionPathOutsideGoldenTree() {
        val session = ProjectWorkspaces.sessionGuestPath(golden, "abc-123")
        assertFalse(session.startsWith("$golden/"))
        assertTrue(session.startsWith(ProjectWorkspaces.EXTERNAL_SESSIONS_ROOT))
        assertTrue(session.endsWith("/abc-123"))
    }

    @Test
    fun externalSessionIsManagedPath() {
        val session = ProjectWorkspaces.sessionGuestPath(golden, "abc-123")
        assertTrue(ProjectWorkspaces.isManagedSessionPath(golden, session))
        assertTrue(ProjectWorkspaces.isExternalManagedSessionPath(golden, session))
    }

    @Test
    fun legacySessionIsManagedPath() {
        val legacy = "${golden}/${ProjectWorkspaces.LEGACY_SESSIONS_DIR}/abc-123"
        assertTrue(ProjectWorkspaces.isManagedSessionPath(golden, legacy))
        assertTrue(ProjectWorkspaces.isLegacyManagedSessionPath(golden, legacy))
        assertFalse(ProjectWorkspaces.isExternalManagedSessionPath(golden, legacy))
    }

    @Test
    fun goldenItselfNotSessionPath() {
        assertFalse(ProjectWorkspaces.isManagedSessionPath(golden, golden))
    }

    @Test
    fun outsidePathRejected() {
        assertFalse(ProjectWorkspaces.isManagedSessionPath(golden, "/root/projects/other"))
    }

    @Test
    fun looksLikeSessionWorkspaceDetectsExternalAndLegacyPaths() {
        val golden = "/root/projects/app"
        val external = ProjectWorkspaces.sessionGuestPath(golden, "sess-1")
        assertTrue(ProjectWorkspaces.looksLikeSessionWorkspace(external))
        val legacy = "${golden}/.termfold/sessions/sess-2"
        assertTrue(ProjectWorkspaces.looksLikeSessionWorkspace(legacy))
        assertFalse(ProjectWorkspaces.looksLikeSessionWorkspace(golden))
    }

    @Test
    fun legacySessionIdParsed() {
        val legacy = "${golden}/${ProjectWorkspaces.LEGACY_SESSIONS_DIR}/ws-9"
        assertEquals("ws-9", ProjectWorkspaces.legacySessionId(golden, legacy))
    }
}
