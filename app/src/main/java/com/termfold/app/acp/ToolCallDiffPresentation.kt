package com.termfold.app.acp

import com.termfold.app.gitworkspace.CachedDiffPresentation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ToolCallFilePresentation(
    val diff: AcpToolFileDiff,
    val presentation: CachedDiffPresentation?,
    val added: Int,
    val removed: Int,
)

suspend fun prepareToolCallFilePresentations(
    fileDiffs: List<AcpToolFileDiff>,
): List<ToolCallFilePresentation> = withContext(Dispatchers.Default) {
    fileDiffs.map { prepareToolCallFilePresentation(it) }
}
