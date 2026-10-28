package com.termfold.app.projectrules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteOriginKeyTest {
    @Test
    fun githubHttpsAndSsh() {
        assertEquals("github.com/owner/repo", RemoteOriginKey.normalize("https://github.com/owner/repo.git"))
        assertEquals("github.com/owner/repo", RemoteOriginKey.normalize("git@github.com:owner/repo.git"))
    }

    @Test
    fun gitlabRemote() {
        assertEquals("gitlab.com/acme/api", RemoteOriginKey.normalize("https://gitlab.com/acme/api.git"))
    }

    @Test
    fun localPathRejected() {
        assertNull(RemoteOriginKey.normalize("/home/user/projects/foo"))
        assertNull(RemoteOriginKey.normalize("file:///data/foo"))
    }

    @Test
    fun storageKeyFromBareHostPath() {
        assertEquals(
            "github.com/acme/app",
            RemoteOriginKey.normalizeOriginKeyForStorage("github.com/acme/app"),
        )
    }
}
