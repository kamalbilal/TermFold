package com.termfold.app.automation

object AutomationSessionContext {
    /** `projectId:sessionId` for the foreground automation session, or null. */
    @Volatile
    var activeKey: String? = null

    fun key(projectId: String, sessionId: String): String = "$projectId:$sessionId"

    fun parse(key: String): Pair<String, String>? {
        val parts = key.split(':', limit = 2)
        if (parts.size != 2 || parts[0].isBlank() || parts[1].isBlank()) return null
        return parts[0] to parts[1]
    }
}
