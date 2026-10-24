package com.termfold.app.acp

/** Detects `/summarize` and builds wire context after a compressing summarize turn. */
object AcpContextCompress {
    const val SUMMARIZE_SLASH_COMMAND = "summarize"

    private const val MAX_SUMMARY_CHARS = 24_000

    fun isSummarizePrompt(text: String): Boolean =
        text.trim().equals("/$SUMMARIZE_SLASH_COMMAND", ignoreCase = true)

    /** Agent text after [userMessageId] until the next user message, if any. */
    fun agentSummaryAfterUser(items: List<AcpItem>, userMessageId: Long): String? {
        val start = items.indexOfFirst { it.id == userMessageId }
        if (start < 0) return null
        val parts = mutableListOf<String>()
        for (i in start + 1 until items.size) {
            when (val item = items[i]) {
                is AcpItem.UserMessage -> break
                is AcpItem.AgentText -> {
                    val t = item.text.trim()
                    if (t.isNotBlank()) parts.add(t)
                }
                else -> Unit
            }
        }
        if (parts.isEmpty()) return null
        return parts.joinToString("\n\n")
    }

    fun formatWireContext(summaryBody: String): String {
        val trimmed = summaryBody.trim()
        val body = if (trimmed.length <= MAX_SUMMARY_CHARS) {
            trimmed
        } else {
            trimmed.take(MAX_SUMMARY_CHARS) + "\n…"
        }
        return buildString {
            append("Session context (compressed from /summarize):\n\n")
            append(body)
        }
    }
}
