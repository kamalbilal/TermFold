package com.termfold.app.acp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ToolCallDiffCacheTest {

    @Test
    fun cacheKeyChangesWhenPatchChanges() {
        val diffA = AcpToolFileDiff("/f.kt", AcpToolFileDiff.Operation.MODIFY, patchText = "a")
        val diffB = diffA.copy(patchText = "b")
        val k1 = toolCallDiffCacheKey("tc1", diffA)
        val k2 = toolCallDiffCacheKey("tc1", diffB)
        assertNotEquals(k1, k2)
    }

    @Test
    fun cacheKeyStableForSamePatch() {
        val diff = AcpToolFileDiff("/f.kt", AcpToolFileDiff.Operation.MODIFY, patchText = "same")
        assertEquals(toolCallDiffCacheKey("id", diff), toolCallDiffCacheKey("id", diff))
    }

    @Test
    fun activeKeysFromToolCalls() {
        val tc = AcpItem.ToolCall(
            id = 1L,
            toolCallId = "wire-1",
            title = "Edit",
            kind = "edit",
            status = "completed",
            detail = "",
            fileDiffs = listOf(
                AcpToolFileDiff("/a.kt", AcpToolFileDiff.Operation.MODIFY, patchText = "x"),
            ),
        )
        val keys = activeToolCallDiffCacheKeys(listOf(tc))
        assertEquals(1, keys.size)
        assertEquals(toolCallDiffCacheKey("wire-1", tc.fileDiffs.single()), keys.single())
    }
}
