package com.termfold.app.prreview

import org.json.JSONArray
import org.json.JSONObject

object PrReviewPersistence {
    fun decode(raw: String?): PrReviewData {
        if (raw.isNullOrBlank()) return PrReviewData()
        return runCatching {
            val root = JSONObject(raw)
            val sessions = root.optJSONArray("sessions") ?: JSONArray()
            PrReviewData(
                sessions = List(sessions.length()) { index ->
                    sessions.getJSONObject(index).toSession()
                },
            )
        }.getOrElse { PrReviewData() }
    }

    fun encode(data: PrReviewData): String {
        val sessions = JSONArray()
        data.sessions.forEach { sessions.put(it.toJson()) }
        return JSONObject().put("sessions", sessions).toString()
    }

    private fun JSONObject.toSession(): PrReviewSession =
        PrReviewSession(
            id = getString("id"),
            name = getString("name"),
            url = getString("url"),
            owner = getString("owner"),
            repo = getString("repo"),
            number = getInt("number"),
            headBranch = getString("headBranch"),
            folderId = getString("folderId"),
            workspaceId = optString("workspaceId", "").ifBlank { null },
            tint = optInt("tint"),
            acpAgentId = optString("acpAgentId", "cursor").ifBlank { "cursor" },
            lastReportJson = optString("lastReportJson", "").ifBlank { null },
            lastParseError = optString("lastParseError", "").ifBlank { null },
            reportsByUserMessageId = optJSONObject("reportsByUserMessageId")?.toReportsMap() ?: emptyMap(),
        )

    private fun PrReviewSession.toJson(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("name", name)
            .put("url", url)
            .put("owner", owner)
            .put("repo", repo)
            .put("number", number)
            .put("headBranch", headBranch)
            .put("folderId", folderId)
            .put("workspaceId", workspaceId ?: "")
            .put("tint", tint)
            .put("acpAgentId", acpAgentId)
            .put("lastReportJson", lastReportJson ?: "")
            .put("lastParseError", lastParseError ?: "")
            .put("reportsByUserMessageId", reportsByUserMessageId.toReportsJson())

    private fun JSONObject.toReportsMap(): Map<Long, String> = buildMap {
        keys().forEach { key ->
            val value = optString(key, "").ifBlank { null } ?: return@forEach
            put(key.toLong(), value)
        }
    }

    private fun Map<Long, String>.toReportsJson(): JSONObject {
        val obj = JSONObject()
        forEach { (id, json) -> obj.put(id.toString(), json) }
        return obj
    }
}
