package com.termfold.app.acp

import com.termfold.app.gitworkspace.diffLineStats

/** Churn = number of added + removed diff lines (excludes context). */
fun toolPatchChurn(patchText: String?): Int {
    if (patchText.isNullOrBlank()) return Int.MAX_VALUE
    val (added, removed) = diffLineStats(patchText)
    return added + removed
}

/**
 * Picks the patch that is better for display. Prefer non-blank; then lower churn (tighter diff).
 * On equal churn, prefer [incoming] as the newer payload.
 */
fun preferToolFileDiff(existing: AcpToolFileDiff, incoming: AcpToolFileDiff): AcpToolFileDiff {
    val churnExisting = toolPatchChurn(existing.patchText)
    val churnIncoming = toolPatchChurn(incoming.patchText)
    return when {
        churnExisting == Int.MAX_VALUE && churnIncoming == Int.MAX_VALUE ->
            incoming.copy(operation = incoming.operation.takeIf { it != AcpToolFileDiff.Operation.UNKNOWN } ?: existing.operation)
        churnExisting == Int.MAX_VALUE -> incoming
        churnIncoming == Int.MAX_VALUE -> existing
        churnIncoming < churnExisting -> incoming
        churnIncoming > churnExisting -> existing
        else -> incoming.copy(operation = incoming.operation.takeIf { it != AcpToolFileDiff.Operation.UNKNOWN } ?: existing.operation)
    }
}

/** Collapses multiple records for the same path (keeps the tightest patch). */
fun dedupeToolFileDiffs(diffs: List<AcpToolFileDiff>): List<AcpToolFileDiff> {
    if (diffs.size <= 1) return diffs
    val out = mutableListOf<AcpToolFileDiff>()
    val indexByPath = mutableMapOf<String, Int>()
    for (diff in diffs) {
        val idx = indexByPath[diff.path]
        if (idx == null) {
            indexByPath[diff.path] = out.size
            out.add(diff)
        } else {
            out[idx] = preferToolFileDiff(out[idx], diff)
        }
    }
    return out
}

/** Merges streaming [incoming] into [existing] per path without replacing a tight patch with a full-file rewrite. */
fun mergeToolFileDiffUpdates(existing: List<AcpToolFileDiff>, incoming: List<AcpToolFileDiff>): List<AcpToolFileDiff> {
    if (incoming.isEmpty()) return existing
    if (existing.isEmpty()) return dedupeToolFileDiffs(incoming)
    val incomingByPath = incoming.associateBy { it.path }
    val merged = existing.map { ex ->
        incomingByPath[ex.path]?.let { preferToolFileDiff(ex, it) } ?: ex
    }.toMutableList()
    for (inc in incoming) {
        if (existing.none { it.path == inc.path }) merged.add(inc)
    }
    return merged
}
