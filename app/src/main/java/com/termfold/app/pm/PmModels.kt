package com.termfold.app.pm

import org.json.JSONArray
import org.json.JSONObject

enum class PmMode {
    DISCOVERY,
    RELEASE,
}

data class PmCompetitor(
    val name: String,
    val homepageUrl: String = "",
    val extraUrls: List<String> = emptyList(),
)

data class PmResearchLogEntry(
    val atMs: Long,
    val summary: String,
    val outcome: String,
)

data class PmSession(
    val id: String,
    val name: String,
    val folderId: String,
    val mode: PmMode,
    val viewBranch: String,
    val branch: String = "",
    val workspaceId: String? = null,
    val releaseLabel: String = "",
    val productOneLiner: String = "",
    val includeCompetitiveResearch: Boolean = false,
    val competitors: List<PmCompetitor> = emptyList(),
    val researchPackCache: String? = null,
    val researchPackBranch: String? = null,
    val researchLog: List<PmResearchLogEntry> = emptyList(),
    val lastDecisionJson: String? = null,
    val acpAgentId: String = "cursor",
    val tint: Int = 0,
    val createdAtMs: Long = 0L,
    val updatedAtMs: Long = 0L,
) {
    val goldenLinked: Boolean get() = mode == PmMode.DISCOVERY
}

data class PmData(
    val sessions: List<PmSession> = emptyList(),
) {
    fun session(id: String?): PmSession? = sessions.firstOrNull { it.id == id }

    fun upsert(session: PmSession): PmData {
        val index = sessions.indexOfFirst { it.id == session.id }
        if (index < 0) return copy(sessions = sessions + session)
        val next = sessions.toMutableList()
        next[index] = session
        return copy(sessions = next)
    }

    fun without(sessionId: String): PmData =
        copy(sessions = sessions.filterNot { it.id == sessionId })
}

object PmPersistence {
    fun decode(raw: String?): PmData {
        if (raw.isNullOrBlank()) return PmData()
        return runCatching {
            val root = JSONObject(raw)
            val sessions = root.optJSONArray("sessions") ?: JSONArray()
            PmData(
                sessions = List(sessions.length()) { index ->
                    sessions.getJSONObject(index).toSession()
                },
            )
        }.getOrElse { PmData() }
    }

    fun encode(data: PmData): String {
        val sessions = JSONArray()
        data.sessions.forEach { sessions.put(it.toJson()) }
        return JSONObject().put("sessions", sessions).toString()
    }

    private fun JSONObject.toSession(): PmSession {
        val competitors = optJSONArray("competitors") ?: JSONArray()
        val log = optJSONArray("researchLog") ?: JSONArray()
        return PmSession(
            id = getString("id"),
            name = getString("name"),
            folderId = getString("folderId"),
            mode = PmMode.valueOf(optString("mode", PmMode.DISCOVERY.name)),
            viewBranch = optString("viewBranch", ""),
            branch = optString("branch", ""),
            workspaceId = optString("workspaceId", "").ifBlank { null },
            releaseLabel = optString("releaseLabel", ""),
            productOneLiner = optString("productOneLiner", ""),
            includeCompetitiveResearch = optBoolean("includeCompetitiveResearch"),
            competitors = List(competitors.length()) { i -> competitors.getJSONObject(i).toCompetitor() },
            researchPackCache = optString("researchPackCache", "").ifBlank { null },
            researchPackBranch = optString("researchPackBranch", "").ifBlank { null },
            researchLog = List(log.length()) { i -> log.getJSONObject(i).toLogEntry() },
            lastDecisionJson = optString("lastDecisionJson", "").ifBlank { null },
            acpAgentId = optString("acpAgentId", "cursor").ifBlank { "cursor" },
            tint = optInt("tint"),
            createdAtMs = optLong("createdAtMs"),
            updatedAtMs = optLong("updatedAtMs"),
        )
    }

    private fun JSONObject.toCompetitor(): PmCompetitor {
        val extras = optJSONArray("extraUrls") ?: JSONArray()
        return PmCompetitor(
            name = optString("name", ""),
            homepageUrl = optString("homepageUrl", ""),
            extraUrls = List(extras.length()) { extras.getString(it) },
        )
    }

    private fun JSONObject.toLogEntry(): PmResearchLogEntry =
        PmResearchLogEntry(
            atMs = optLong("atMs"),
            summary = optString("summary", ""),
            outcome = optString("outcome", ""),
        )

    private fun PmCompetitor.toJson(): JSONObject =
        JSONObject()
            .put("name", name)
            .put("homepageUrl", homepageUrl)
            .put("extraUrls", JSONArray(extraUrls))

    private fun PmResearchLogEntry.toJson(): JSONObject =
        JSONObject()
            .put("atMs", atMs)
            .put("summary", summary)
            .put("outcome", outcome)

    private fun PmSession.toJson(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("name", name)
            .put("folderId", folderId)
            .put("mode", mode.name)
            .put("viewBranch", viewBranch)
            .put("branch", branch)
            .put("workspaceId", workspaceId ?: "")
            .put("releaseLabel", releaseLabel)
            .put("productOneLiner", productOneLiner)
            .put("includeCompetitiveResearch", includeCompetitiveResearch)
            .put("competitors", JSONArray(competitors.map { it.toJson() }))
            .put("researchPackCache", researchPackCache ?: "")
            .put("researchPackBranch", researchPackBranch ?: "")
            .put("researchLog", JSONArray(researchLog.map { it.toJson() }))
            .put("lastDecisionJson", lastDecisionJson ?: "")
            .put("acpAgentId", acpAgentId)
            .put("tint", tint)
            .put("createdAtMs", createdAtMs)
            .put("updatedAtMs", updatedAtMs)
}
