package com.termfold.app.gitworkspace

import com.termfold.app.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionCopyOriginSyncTest {

    @Test
    fun keepsNetworkOriginOnSessionCopy() {
        val url = "https://github.com/org/repo.git"
        assertEquals(url, SessionCopyOriginSync.resolveOriginUrlForSessionCopy(url, null))
    }

    @Test
    fun replacesLocalGoldenPathWithGoldenNetworkOrigin() {
        val golden = "https://github.com/org/repo.git"
        val local = "/data/user/0/${BuildConfig.APPLICATION_ID}/files/golden-checkout"
        assertEquals(
            golden,
            SessionCopyOriginSync.resolveOriginUrlForSessionCopy(local, golden),
        )
    }

    @Test
    fun replacesLocalGoldenPathWithGoldenBareRemote() {
        val golden = "/tmp/termfold/remote.git"
        val local = "/tmp/termfold/golden"
        assertEquals(
            golden,
            SessionCopyOriginSync.resolveOriginUrlForSessionCopy(local, golden),
        )
    }

    @Test
    fun returnsNullWhenGoldenOriginMissing() {
        assertNull(
            SessionCopyOriginSync.resolveOriginUrlForSessionCopy(
                "/local/golden",
                null,
            ),
        )
    }
}
