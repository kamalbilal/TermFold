package com.termfold.app.acp

/** Tool rows that represent TermFold / Cursor interactive ask (`ask_user`, `ask_question`). */
fun isInteractiveAskToolCall(tool: AcpItem.ToolCall): Boolean {
    val t = tool.title.lowercase()
    val k = tool.kind.lowercase()
    if (t.contains("ask_user") || t.contains("ask-user")) return true
    if (t.contains("termfold-ask")) return true
    if (t.contains("ask_question") || t.contains("ask question")) return true
    if (k == "ask_question" || k == "ask_user") return true
    return false
}

/**
 * Maps each interactive ask tool in chronological order to its [AskQuestionTurnSnapshot].
 * Wire [AcpItem.ToolCall.toolCallId] is the map key. Snapshots from MCP often use generated
 * `ask_*` ids — those are paired by order when ids differ.
 */
fun pairAskSnapshotsWithAskTools(
    turnAgentItems: List<AcpItem>,
    snapshots: List<AskQuestionTurnSnapshot>,
): Map<String, AskQuestionTurnSnapshot> {
    if (snapshots.isEmpty()) return emptyMap()
    val askTools = turnAgentItems.filterIsInstance<AcpItem.ToolCall>().filter(::isInteractiveAskToolCall)
    if (askTools.isEmpty()) return emptyMap()
    val available = snapshots.toMutableList()
    val out = linkedMapOf<String, AskQuestionTurnSnapshot>()
    for (tool in askTools) {
        val exactIdx = available.indexOfFirst { it.toolCallId == tool.toolCallId }
        val snap: AskQuestionTurnSnapshot? = when {
            exactIdx >= 0 -> available.removeAt(exactIdx)
            available.isNotEmpty() -> available.removeAt(0)
            else -> null
        }
        if (snap != null) out[tool.toolCallId] = snap
    }
    return out
}

/** Snapshot ids rendered inline under matching ask tool rows (exclude from turn footer). */
fun inlineAskSnapshotIdsForTurn(
    turnItems: List<AcpItem>,
    snapshots: List<AskQuestionTurnSnapshot>,
): Set<String> {
    val agentItems = turnItems.dropWhile { it is AcpItem.UserMessage }
        .takeWhile { it !is AcpItem.UserMessage }
    return pairAskSnapshotsWithAskTools(agentItems, snapshots).values
        .map { it.toolCallId }
        .toSet()
}
