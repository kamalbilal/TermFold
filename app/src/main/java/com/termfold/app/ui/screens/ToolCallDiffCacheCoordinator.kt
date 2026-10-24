package com.termfold.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshots.SnapshotStateMap
import com.termfold.app.acp.AcpItem
import com.termfold.app.acp.ToolCallFilePresentation
import com.termfold.app.acp.activeToolCallDiffCacheKeys
import com.termfold.app.acp.prepareToolCallFilePresentation
import com.termfold.app.acp.toolCallDiffCacheKey
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

private const val MAX_CONCURRENT_TOOL_DIFF_PREP = 3
private const val STREAMING_DEBOUNCE_MS = 50L

/**
 * Session-scoped coordinator: prepares diff presentations off the main thread and stores them in [cache].
 */
@Composable
fun ToolCallDiffCacheCoordinator(
    toolCalls: List<AcpItem.ToolCall>,
    cache: SnapshotStateMap<String, ToolCallFilePresentation>,
    loading: SnapshotStateMap<String, Boolean>,
) {
    val anyInProgress = toolCalls.any { it.status == "in_progress" }
    val requestSignature = toolCalls.joinToString("\n") { tc ->
        buildString {
            append(tc.toolCallId)
            append('|')
            append(tc.status)
            tc.fileDiffs.forEach { d ->
                append('|')
                append(toolCallDiffCacheKey(tc.toolCallId, d))
            }
        }
    }
    LaunchedEffect(requestSignature) {
        if (anyInProgress) delay(STREAMING_DEBOUNCE_MS)
        val activeKeys = activeToolCallDiffCacheKeys(toolCalls)
        cache.keys.toList().filter { it !in activeKeys }.forEach { cache.remove(it) }
        loading.keys.toList().filter { it !in activeKeys }.forEach { loading.remove(it) }
        val pending = buildList {
            for (tc in toolCalls) {
                for (diff in tc.fileDiffs) {
                    if (diff.patchText.isNullOrBlank()) continue
                    val key = toolCallDiffCacheKey(tc.toolCallId, diff)
                    if (!cache.containsKey(key) && loading[key] != true) add(key to diff)
                }
            }
        }
        if (pending.isEmpty()) return@LaunchedEffect
        pending.chunked(MAX_CONCURRENT_TOOL_DIFF_PREP).forEach { batch ->
            coroutineScope {
                batch.map { (key, diff) ->
                    async {
                        if (cache.containsKey(key)) return@async
                        loading[key] = true
                        try {
                            cache[key] = prepareToolCallFilePresentation(diff)
                        } finally {
                            loading[key] = false
                        }
                    }
                }.awaitAll()
            }
        }
    }
}
