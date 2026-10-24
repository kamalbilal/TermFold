package com.termfold.app.acp.checkpoints

import com.termfold.app.acp.AcpItem

/** Builds a compact context block from kept (non-ghost) timeline rows for a fresh wire session. */
object ConversationSummary {

    private const val MAX_CHARS = 12_000

    fun resolveWireSummary(
        items: List<AcpItem>,
        ghostFromItemId: Long?,
        summaryOverride: String?,
    ): String = summaryOverride ?: fromItems(items, ghostFromItemId)

    fun fromItems(items: List<AcpItem>, ghostFromItemId: Long?): String {
        val kept = if (ghostFromItemId == null) items else items.filter { it.id < ghostFromItemId }
        if (kept.isEmpty()) return "No prior conversation in this session."
        val body = kept.mapNotNull { lineFor(it) }.joinToString("\n\n")
        val trimmed = if (body.length <= MAX_CHARS) body else body.take(MAX_CHARS) + "\n…"
        return buildString {
            append("Session context (summarized from earlier turns in this chat):\n\n")
            append(trimmed)
        }
    }

    private fun lineFor(item: AcpItem): String? = when (item) {
        is AcpItem.UserMessage -> {
            val t = item.text.trim()
            if (t.isBlank()) null else "User: $t"
        }
        is AcpItem.AgentText -> {
            val t = item.text.trim()
            if (t.isBlank()) null else "Assistant: ${t.take(4000)}"
        }
        is AcpItem.ToolCall -> "Tool (${item.kind}): ${item.title}"
        is AcpItem.Notice -> null
        is AcpItem.Thought -> null
        is AcpItem.Plan -> {
            val lines = item.entries.map { "- ${it.text}" }.joinToString("\n")
            if (lines.isBlank()) null else "Plan:\n$lines"
        }
    }
}
