package com.termfold.app.acp

import com.termfold.app.gitworkspace.diffLineStats
import com.termfold.app.gitworkspace.prepareDiffPresentation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Stable cache key for a tool-call file patch; changes when [AcpToolFileDiff.patchText] changes. */
fun toolCallDiffCacheKey(toolCallId: String, diff: AcpToolFileDiff): String {
    val patch = diff.patchText.orEmpty()
    val fingerprint = if (patch.isEmpty()) "empty" else "${patch.length}:${patch.hashCode()}"
    return "$toolCallId|${diff.path}|$fingerprint"
}

suspend fun prepareToolCallFilePresentation(diff: AcpToolFileDiff): ToolCallFilePresentation =
    withContext(Dispatchers.Default) {
        val patch = diff.patchText
        if (patch.isNullOrBlank()) {
            ToolCallFilePresentation(diff, null, 0, 0)
        } else {
            val (added, removed) = diffLineStats(patch)
            ToolCallFilePresentation(diff, prepareDiffPresentation(patch), added, removed)
        }
    }

/** Keys currently referenced by visible tool calls (for pruning stale entries). */
fun activeToolCallDiffCacheKeys(toolCalls: List<AcpItem.ToolCall>): Set<String> =
    toolCalls.flatMap { tc -> tc.fileDiffs.map { toolCallDiffCacheKey(tc.toolCallId, it) } }.toSet()
