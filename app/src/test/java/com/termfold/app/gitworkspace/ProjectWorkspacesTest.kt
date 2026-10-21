package com.termfold.app.gitworkspace

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectWorkspacesTest {
    @Test
    fun sessionPathUnderGolden() {
        val golden = "/root/projects/app"
        val session = ProjectWorkspaces.sessionGuestPath(golden, "abc-123")
        assertTrue(ProjectWorkspaces.isManagedSessionPath(golden, session))
    }

    @Test
    fun goldenItselfNotSessionPath() {
        val golden = "/root/projects/app"
        assertFalse(ProjectWorkspaces.isManagedSessionPath(golden, golden))
    }

    @Test
    fun outsidePathRejected() {
        val golden = "/root/projects/app"
        assertFalse(ProjectWorkspaces.isManagedSessionPath(golden, "/root/projects/other"))
    }
}
