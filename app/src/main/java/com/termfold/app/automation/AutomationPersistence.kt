package com.termfold.app.automation

import org.json.JSONArray
import org.json.JSONObject

object AutomationPersistence {
    fun decode(raw: String?): AutomationData {
        if (raw.isNullOrBlank()) return AutomationData()
        return runCatching {
            val root = JSONObject(raw)
            val projects = root.optJSONArray("projects") ?: JSONArray()
            AutomationData(
                projects = List(projects.length()) { index ->
                    projects.getJSONObject(index).toProject()
                },
            )
        }.getOrElse { AutomationData() }
    }

    fun encode(data: AutomationData): String {
        val projects = JSONArray()
        data.projects.forEach { projects.put(it.toJson()) }
        return JSONObject().put("projects", projects).toString()
    }

    private fun JSONObject.toProject(): AutomationProject {
        val sessions = optJSONArray("sessions") ?: JSONArray()
        val linked = optString("linkedFolderId", "").ifBlank { null }
        return AutomationProject(
            id = getString("id"),
            name = getString("name"),
            tint = optInt("tint"),
            linkedFolderId = linked,
            sessions = List(sessions.length()) { index ->
                sessions.getJSONObject(index).toSession()
            },
        )
    }

    private fun JSONObject.toSession(): AutomationSession =
        AutomationSession(
            id = getString("id"),
            name = getString("name"),
            tint = optInt("tint"),
            acpAgentId = optString("acpAgentId", "cursor").ifBlank { "cursor" },
        )

    private fun AutomationProject.toJson(): JSONObject {
        val sessionArray = JSONArray()
        sessions.forEach { session ->
            sessionArray.put(
                JSONObject()
                    .put("id", session.id)
                    .put("name", session.name)
                    .put("tint", session.tint)
                    .put("acpAgentId", session.acpAgentId),
            )
        }
        return JSONObject()
            .put("id", id)
            .put("name", name)
            .put("tint", tint)
            .put("linkedFolderId", linkedFolderId ?: "")
            .put("sessions", sessionArray)
    }
}
