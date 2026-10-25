package com.termfold.app.automation

object AutomationReadOnlyPolicy {

    fun normalizeGuestPath(path: String): String {
        var p = path.trim().replace('\\', '/')
        if (p.isEmpty()) return p
        if (!p.startsWith("/")) p = "/$p"
        return p.trimEnd('/')
    }

    fun isUnderReadOnlyRoot(readOnlyGuestRoot: String?, guestPath: String): Boolean {
        val root = readOnlyGuestRoot?.trim()?.takeIf { it.isNotBlank() }?.let(::normalizeGuestPath)
            ?: return false
        val path = normalizeGuestPath(guestPath)
        return path == root || path.startsWith("$root/")
    }

    fun firstBlockedPath(readOnlyGuestRoot: String?, paths: Collection<String>): String? =
        paths.firstOrNull { isUnderReadOnlyRoot(readOnlyGuestRoot, it) }

    fun pathsFromPermissionParams(params: org.json.JSONObject?): List<String> {
        if (params == null) return emptyList()
        val out = linkedSetOf<String>()
        params.optJSONObject("toolCall")?.let { tool ->
            tool.optJSONObject("rawInput")?.let { input ->
                listOf("path", "file_path", "filePath", "target", "file").forEach { key ->
                    input.optString(key).takeIf { it.isNotBlank() }?.let(out::add)
                }
                input.optJSONArray("paths")?.let { arr ->
                    for (i in 0 until arr.length()) arr.optString(i).takeIf { it.isNotBlank() }?.let(out::add)
                }
            }
            tool.optString("title").takeIf { it.isNotBlank() }?.let(out::add)
        }
        params.optString("message").takeIf { it.isNotBlank() }?.let(out::add)
        return out.toList()
    }
}
