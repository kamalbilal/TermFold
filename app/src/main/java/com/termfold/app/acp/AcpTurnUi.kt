package com.termfold.app.acp

/** UI hints for whether a turn still looks active to the user (vs wire still finishing). */
object AcpTurnUi {

    /** Footer "Working" and header subtitle while [agentBusy] but the stream looks idle. */
    fun showWorkingIndicator(agentBusy: Boolean, items: List<AcpItem>): Boolean {
        if (!agentBusy) return false
        val lastUserIdx = items.indexOfLast { it is AcpItem.UserMessage }
        if (lastUserIdx < 0) return true
        val tail = items.subList(lastUserIdx + 1, items.size)
        if (tail.isEmpty()) return true
        if (tail.any { it is AcpItem.ToolCall && it.status == "in_progress" }) return true
        if (tail.lastOrNull() is AcpItem.Thought) return true
        return false
    }
}
