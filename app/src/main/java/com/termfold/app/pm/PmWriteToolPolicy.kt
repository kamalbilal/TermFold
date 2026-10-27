package com.termfold.app.pm

import org.json.JSONObject

object PmWriteToolPolicy {
    private val writeKinds = setOf("edit", "write", "delete", "move", "create")
    private val writeHints = listOf("edit", "write", "delete", "unlink", "rm ", "move", "apply_patch", "strreplace")

    fun isWriteLike(params: JSONObject?): Boolean {
        if (params == null) return false
        val tool = params.optJSONObject("toolCall") ?: return false
        val kind = tool.optString("kind").lowercase()
        if (kind in writeKinds) return true
        val title = tool.optString("title").lowercase()
        val name = tool.optString("name").lowercase()
        return writeHints.any { title.contains(it) || name.contains(it) }
    }
}
