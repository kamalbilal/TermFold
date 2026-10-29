package com.termfold.app.pdftools

import org.json.JSONArray
import org.json.JSONObject

object PdfToolsPersistence {
    fun decode(raw: String?): PdfToolsData {
        if (raw.isNullOrBlank()) return PdfToolsData()
        return runCatching {
            val root = JSONObject(raw)
            val sessions = root.optJSONArray("sessions") ?: JSONArray()
            PdfToolsData(
                sessions = List(sessions.length()) { index ->
                    sessions.getJSONObject(index).toSession()
                },
            )
        }.getOrElse { PdfToolsData() }
    }

    fun encode(data: PdfToolsData): String {
        val sessions = JSONArray()
        data.sessions.forEach { sessions.put(it.toJson()) }
        return JSONObject().put("sessions", sessions).toString()
    }

    private fun JSONObject.toSession(): PdfToolsSession =
        PdfToolsSession(
            id = getString("id"),
            name = getString("name"),
            jobType = runCatching { PdfToolsJobType.valueOf(getString("jobType")) }
                .getOrDefault(PdfToolsJobType.INSPECT),
            authzClass = runCatching { PdfToolsAuthzClass.valueOf(optString("authzClass", "UNKNOWN")) }
                .getOrDefault(PdfToolsAuthzClass.UNKNOWN),
            inputSha256 = optString("inputSha256", "").ifBlank { null },
            outputSha256 = optString("outputSha256", "").ifBlank { null },
            approvedPlanId = optString("approvedPlanId", "").ifBlank { null },
            lastReport = optString("lastReport", "").ifBlank { null },
            inspectJson = optString("inspectJson", "").ifBlank { null },
            encrypted = optBoolean("encrypted"),
            tint = optInt("tint"),
            acpAgentId = optString("acpAgentId", "cursor").ifBlank { "cursor" },
            createdAtMs = optLong("createdAtMs"),
            updatedAtMs = optLong("updatedAtMs"),
        )

    private fun PdfToolsSession.toJson(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("name", name)
            .put("jobType", jobType.name)
            .put("authzClass", authzClass.name)
            .put("inputSha256", inputSha256 ?: "")
            .put("outputSha256", outputSha256 ?: "")
            .put("approvedPlanId", approvedPlanId ?: "")
            .put("lastReport", lastReport ?: "")
            .put("inspectJson", inspectJson ?: "")
            .put("encrypted", encrypted)
            .put("tint", tint)
            .put("acpAgentId", acpAgentId)
            .put("createdAtMs", createdAtMs)
            .put("updatedAtMs", updatedAtMs)
}
