package com.termfold.app.pm

import org.json.JSONObject

data class PmDecisionMemo(
    val recommendation: String = "",
    val landscape: String = "",
    val options: String = "",
    val scopeIn: String = "",
    val scopeOut: String = "",
    val risks: String = "",
    val gaps: String = "",
)

object PmDecisionMemoParser {
    private val fencedJson = Regex("""```json\s*(\{[\s\S]*?})\s*```""", RegexOption.IGNORE_CASE)

    fun parseFromAgentText(text: String): PmDecisionMemo? {
        val fenced = fencedJson.find(text)?.groupValues?.getOrNull(1)
        val raw = fenced ?: PmResearchPack.firstJsonObject(text)
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val memo = fromJson(JSONObject(raw))
            if (memo.recommendation.isBlank() && memo.options.isBlank() && memo.gaps.isBlank()) null
            else memo
        }.getOrNull()
    }

    fun fromJson(obj: JSONObject): PmDecisionMemo =
        PmDecisionMemo(
            recommendation = obj.optString("recommendation", ""),
            landscape = obj.optString("landscape", ""),
            options = obj.optString("options", ""),
            scopeIn = obj.optString("scope_in", obj.optString("scopeIn", "")),
            scopeOut = obj.optString("scope_out", obj.optString("scopeOut", "")),
            risks = obj.optString("risks", ""),
            gaps = obj.optString("gaps", obj.optString("open_questions", "")),
        )

    fun stripFences(text: String): String = fencedJson.replace(text, "").trim()
}
