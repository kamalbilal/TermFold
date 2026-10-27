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

    /** Workflow UI (PM controls, decision memo, PR report) anchored to one user turn. */
    data class TurnFooter(val userMessageId: Long) : AgentChatLazyEntry("turn-footer-$userMessageId")
}

fun buildAgentChatTimelineEntries(
    items: List<AcpItem>,
    collapsedTools: Map<Long, Boolean>,
    expandedToolFiles: Map<String, Boolean>,
    contextFolds: Map<String, Set<String>>,
    workspaceDir: String,
    showLiveToolDiffs: Boolean,
    presentationCache: Map<String, ToolCallFilePresentation>,
    turnFooters: Boolean = false,
): List<AgentChatLazyEntry> {
    val out = mutableListOf<AgentChatLazyEntry>()
    if (!turnFooters) {
        for (item in items) {
            out.appendTimelineEntriesForItem(
                item = item,
                collapsedTools = collapsedTools,
                expandedToolFiles = expandedToolFiles,
                contextFolds = contextFolds,
                workspaceDir = workspaceDir,
                showLiveToolDiffs = showLiveToolDiffs,
                presentationCache = presentationCache,
            )
        }
        return out
    }
    var index = 0
    while (index < items.size) {
        val item = items[index]
        if (item is AcpItem.UserMessage) {
            val userMessageId = item.id
            out.appendTimelineEntriesForItem(
                item = item,
                collapsedTools = collapsedTools,
                expandedToolFiles = expandedToolFiles,
                contextFolds = contextFolds,
                workspaceDir = workspaceDir,
                showLiveToolDiffs = showLiveToolDiffs,
                presentationCache = presentationCache,
            )
            index++
            while (index < items.size && items[index] !is AcpItem.UserMessage) {
                out.appendTimelineEntriesForItem(
                    item = items[index],
                    collapsedTools = collapsedTools,
                    expandedToolFiles = expandedToolFiles,
                    contextFolds = contextFolds,
                    workspaceDir = workspaceDir,
                    showLiveToolDiffs = showLiveToolDiffs,
                    presentationCache = presentationCache,
                )
                index++
            }
            out += AgentChatLazyEntry.TurnFooter(userMessageId)
        } else {
            out.appendTimelineEntriesForItem(
                item = item,
                collapsedTools = collapsedTools,
                expandedToolFiles = expandedToolFiles,
                contextFolds = contextFolds,
                workspaceDir = workspaceDir,
                showLiveToolDiffs = showLiveToolDiffs,
                presentationCache = presentationCache,
            )
            index++
        }
    }
    return out
}

/** User message plus every timeline item until the next user message (exclusive). */
fun agentChatItemsForUserTurn(all: List<AcpItem>, userMessageId: Long): List<AcpItem> {
    val start = all.indexOfFirst { it is AcpItem.UserMessage && it.id == userMessageId }
    if (start < 0) return emptyList()
    var end = start + 1
    while (end < all.size && all[end] !is AcpItem.UserMessage) end++
    return all.subList(start, end)
}

fun agentChatTurnHasAgentReply(turnItems: List<AcpItem>): Boolean =
    turnItems.drop(1).any { it is AcpItem.AgentText && it.text.isNotBlank() }

private fun MutableList<AgentChatLazyEntry>.appendTimelineEntriesForItem(
    item: AcpItem,
    collapsedTools: Map<Long, Boolean>,
    expandedToolFiles: Map<String, Boolean>,
    contextFolds: Map<String, Set<String>>,
    workspaceDir: String,
    showLiveToolDiffs: Boolean,
    presentationCache: Map<String, ToolCallFilePresentation>,
) {
    val wsPrefix = workspaceDir.trimEnd('/') + "/"
    when (item) {
        is AcpItem.ToolCall -> {
            add(AgentChatLazyEntry.ToolHeader(item))
            if (collapsedTools[item.id] == true) return
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
                add(
                    AgentChatLazyEntry.ToolFileSection(
                        item = item,
                        fileIndex = fileIndex,
                        diff = diff,
                        relPath = rel,
                        added = added,
                        removed = removed,
                        fileExpanded = fileExpanded,
                        body = body,
                    ),
                )
            }
            if (item.detail.isNotBlank()) {
                add(AgentChatLazyEntry.ToolDetail(item))
            }
        }
        else -> add(AgentChatLazyEntry.Standard(item))
    }
}

private fun lastUserMessageIndex(items: List<AcpItem>): Int =
    items.indexOfLast { it is AcpItem.UserMessage }

private fun toolInLatestTurn(tool: AcpItem.ToolCall, items: List<AcpItem>): Boolean {
    val userIdx = lastUserMessageIndex(items)
    if (userIdx < 0) return false
    val toolIdx = items.indexOfFirst { it is AcpItem.ToolCall && it.id == tool.id }
    if (toolIdx < 0) return false
    return toolIdx > userIdx
}

/** Default collapsed state for a tool row; in-progress tools stay expanded. */
fun defaultToolCallCollapsed(
    tool: AcpItem.ToolCall,
    items: List<AcpItem>,
    showLiveToolDiffs: Boolean,
    agentBusy: Boolean,
): Boolean {
    if (tool.status == "in_progress") return false
    if (!showLiveToolDiffs) return true
    if (agentBusy) return true
    if (tool.fileDiffs.isEmpty()) return true
    return !toolInLatestTurn(tool, items)
}

fun syncCompletedToolCollapse(
    items: List<AcpItem>,
    collapsedTools: MutableMap<Long, Boolean>,
    showLiveToolDiffs: Boolean,
    agentBusy: Boolean,
    userToggledToolIds: Set<Long> = emptySet(),
) {
    for (item in items) {
        if (item !is AcpItem.ToolCall) continue
        if (item.id in userToggledToolIds) continue
        val defaultCollapsed = defaultToolCallCollapsed(item, items, showLiveToolDiffs, agentBusy)
        if (!collapsedTools.containsKey(item.id)) {
            collapsedTools[item.id] = defaultCollapsed
        } else if (collapsedTools[item.id] != defaultCollapsed) {
            collapsedTools[item.id] = defaultCollapsed
        }
    }
}

/** Collapse map for the chat timeline; recomputed when items or live-diff policy change. */
fun buildToolCallCollapsedMap(
    items: List<AcpItem>,
    showLiveToolDiffs: Boolean,
    agentBusy: Boolean,
    userToggledToolIds: Set<Long>,
    userCollapsedOverrides: Map<Long, Boolean> = emptyMap(),
): Map<Long, Boolean> = buildMap {
    for (item in items) {
        if (item !is AcpItem.ToolCall) continue
        val collapsed = if (item.id in userToggledToolIds) {
            userCollapsedOverrides[item.id] ?: defaultToolCallCollapsed(item, items, showLiveToolDiffs, agentBusy)
        } else {
            defaultToolCallCollapsed(item, items, showLiveToolDiffs, agentBusy)
        }
        put(item.id, collapsed)
    }
}
