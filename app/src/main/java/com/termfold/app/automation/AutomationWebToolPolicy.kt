package com.termfold.app.automation

import com.termfold.app.acp.AcpItem
import org.json.JSONObject

/** Blocks Cursor built-in web search / URL fetch tools; automation must use `termfold-browser`. */
object AutomationWebToolPolicy {

    private val blockedKinds = setOf("search", "fetch")

    fun blocksBuiltInWebTool(kind: String, title: String, toolCallId: String): Boolean {
        val k = kind.lowercase()
        if (k in blockedKinds) return true
        val id = toolCallId.lowercase()
        if (id.startsWith("web_") || id.contains("web_search")) return true
        val t = title.lowercase()
        return t.startsWith("web search") || t.startsWith("webfetch") || t.contains("web fetch")
    }

    fun shouldBlockPermission(params: JSONObject?): Boolean {
        val tool = params?.optJSONObject("toolCall") ?: return false
        return blocksBuiltInWebTool(
            kind = tool.optString("kind"),
            title = tool.optString("title"),
            toolCallId = tool.optString("toolCallId"),
        )
    }

    fun shouldBlockToolCall(item: AcpItem.ToolCall): Boolean =
        blocksBuiltInWebTool(item.kind, item.title, item.toolCallId)
}
