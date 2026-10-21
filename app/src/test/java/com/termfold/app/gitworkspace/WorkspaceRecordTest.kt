package com.termfold.app.gitworkspace

import org.junit.Assert.assertEquals
import org.junit.Test
import org.json.JSONObject

class WorkspaceRecordTest {
    @Test
    fun jsonRoundTrip() {
        val record = WorkspaceRecord(
            id = "ws-1",
            goldenProjectId = "folder-1",
            guestPath = "/root/projects/app/.termfold/sessions/ws-1",
            createdAt = 1_700_000_000_000L,
            sourceBranch = "main",
            lastKnownBranch = "feature/x",
            complete = true,
            label = "copy-a",
        )
        val back = WorkspaceRecord.fromJson(record.toJson())
        assertEquals(record, back)
    }

    @Test
    fun fromJsonIgnoresUnknownFields() {
        val obj = JSONObject()
            .put("id", "ws-2")
            .put("goldenProjectId", "f")
            .put("guestPath", "/p")
            .put("createdAt", 0L)
            .put("sourceBranch", "main")
            .put("lastKnownBranch", "main")
            .put("complete", false)
            .put("extra", "ignored")
        val record = WorkspaceRecord.fromJson(obj)
        assertEquals("ws-2", record.id)
        assertEquals(null, record.label)
    }
}
