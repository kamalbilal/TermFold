package com.termfold.app.gitworkspace

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionKeysTest {
    @Test
    fun composeAndParse() {
        val key = SessionKeys.compose("folder1", "ws-1", "sess-1")
        assertEquals("folder1/ws-1/sess-1", key)
        val parts = SessionKeys.parse(key)
        assertEquals("folder1", parts.folderId)
        assertEquals("ws-1", parts.workspaceId)
        assertEquals("sess-1", parts.sessionId)
    }

    @Test
    fun legacyTwoPartKey() {
        val parts = SessionKeys.parse("folder1/sess-1")
        assertEquals(WorkspaceIds.GOLDEN, parts.workspaceId)
        assertEquals("sess-1", parts.sessionId)
    }
}
