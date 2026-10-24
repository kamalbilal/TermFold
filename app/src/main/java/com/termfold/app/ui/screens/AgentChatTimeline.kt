package com.termfold.app.ui.screens

import com.termfold.app.acp.AcpItem
import com.termfold.app.acp.AcpToolFileDiff
import com.termfold.app.acp.ToolCallFilePresentation
import com.termfold.app.acp.toolCallDiffCacheKey
import com.termfold.app.gitworkspace.DiffDisplayRow
import com.termfold.app.gitworkspace.applyContextFolding
import com.termfold.app.gitworkspace.diffLineStats

/** One row in the agent chat [androidx.compose.foundation.lazy.LazyColumn] (tool calls fan out to many). */
sealed class AgentChatLazyEntry(val key: String) {
    data class Standard(val item: AcpItem) : AgentChatLazyEntry("item-${item.id}")

    data class ToolHeader(val item: AcpItem.ToolCall) : AgentChatLazyEntry("tool-h-${item.id}")

    /** File card + diff body in one lazy item so list spacing does not gap diff lines. */
    data class ToolFileSection(
        val item: AcpItem.ToolCall,
        val fileIndex: Int,
        val diff: AcpToolFileDiff,
        val relPath: String,
        val added: Int,
        val removed: Int,
        val fileExpanded: Boolean,
        val body: ToolFileDiffBody?,
    ) : AgentChatLazyEntry("tool-f-${item.id}-$fileIndex")

    sealed class ToolFileDiffBody {
        data object Loading : ToolFileDiffBody()
        data object Binary : ToolFileDiffBody()
        data class Lines(
            val rows: List<DiffDisplayRow>,
            val foldKeyPrefix: String,
        ) : ToolFileDiffBody()
    }

    data class ToolDetail(val item: AcpItem.ToolCall) : AgentChatLazyEntry("tool-d-${item.id}")
}

fun buildAgentChatTimelineEntries(
    items: List<AcpItem>,
    collapsedTools: Map<Long, Boolean>,
    expandedToolFiles: Map<String, Boolean>,
    contextFolds: Map<String, Set<String>>,
    workspaceDir: String,
    showLiveToolDiffs: Boolean,
    presentationCache: Map<String, ToolCallFilePresentation>,
): List<AgentChatLazyEntry> {
    val out = mutableListOf<AgentChatLazyEntry>()
    val wsPrefix = workspaceDir.trimEnd('/') + "/"
    for (item in items) {
        when (item) {
            is AcpItem.ToolCall -> {
                out += AgentChatLazyEntry.ToolHeader(item)
                if (collapsedTools[item.id] == true) continue
                val inProgress = item.status == "in_progress"
                val showHunkLines = !inProgress || showLiveToolDiffs
                item.fileDiffs.forEachIndexed { fileIndex, diff ->
                    val fileUiKey = "${item.id}:$fileIndex"
                    val fileExpanded = expandedToolFiles[fileUiKey] != false
                    val cacheKey = toolCallDiffCacheKey(item.toolCallId, diff)
                    val row = presentationCache[cacheKey]
                    val quick = diffLineStats(diff.patchText.orEmpty())
                    val (added, removed) = row?.let { it.added to it.removed } ?: quick
                    val rel = diff.path.removePrefix(wsPrefix).ifBlank { diff.path }
                    val body = when {
                        !fileExpanded || !showHunkLines -> null
                        else -> {
                            val presentation = row?.presentation
                            when {
                                presentation == null && !diff.patchText.isNullOrBlank() ->
                                    AgentChatLazyEntry.ToolFileDiffBody.Loading
                                presentation?.isBinary == true ->
                                    AgentChatLazyEntry.ToolFileDiffBody.Binary
                                presentation != null -> {
                                    val foldPrefix = "tc${item.id}:f$fileIndex:"
                                    val folds = contextFolds[foldPrefix] ?: emptySet()
                                    val displayRows = applyContextFolding(presentation.rowsUnified, foldPrefix, folds)
                                    if (displayRows.isEmpty()) null
                                    else AgentChatLazyEntry.ToolFileDiffBody.Lines(displayRows, foldPrefix)
                                }
                                else -> null
                            }
                        }
                    }
                    out += AgentChatLazyEntry.ToolFileSection(
                        item = item,
                        fileIndex = fileIndex,
                        diff = diff,
                        relPath = rel,
                        added = added,
                        removed = removed,
                        fileExpanded = fileExpanded,
                        body = body,
                    )
                }
                if (item.detail.isNotBlank()) {
                    out += AgentChatLazyEntry.ToolDetail(item)
                }
            }
            else -> out += AgentChatLazyEntry.Standard(item)
        }
    }
    return out
}

/** Default collapse for completed tool calls; in-progress tools stay expanded. */
fun syncCompletedToolCollapse(
    items: List<AcpItem>,
    collapsedTools: MutableMap<Long, Boolean>,
) {
    // Only set defaults for newly seen tools; never override user expand/collapse.
    for (item in items) {
        if (item !is AcpItem.ToolCall) continue
        if (!collapsedTools.containsKey(item.id)) {
            collapsedTools[item.id] = item.status != "in_progress"
        }
    }
}
