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
    private const val FENCE_OPEN = "```json"

    fun parseFromAgentText(text: String): PmDecisionMemo? {
        val fenced = extractFencedJson(text)
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

    fun toJson(memo: PmDecisionMemo): String =
        JSONObject()
            .put("recommendation", memo.recommendation)
            .put("landscape", memo.landscape)
            .put("options", memo.options)
            .put("scope_in", memo.scopeIn)
            .put("scope_out", memo.scopeOut)
            .put("risks", memo.risks)
            .put("gaps", memo.gaps)
            .toString()

    fun stripFences(text: String): String {
        var remaining = text
        while (true) {
            val start = remaining.indexOf(FENCE_OPEN, ignoreCase = true)
            if (start < 0) return remaining.trim()
            val after = remaining.substring(start + FENCE_OPEN.length).trimStart()
            val closeIdx = after.indexOf("```")
            remaining = if (closeIdx < 0) {
                remaining.substring(0, start)
            } else {
                remaining.substring(0, start) + after.substring(closeIdx + 3)
            }
        }
    }

    private fun extractFencedJson(text: String): String? {
        var search = 0
        var last: String? = null
        while (true) {
            val start = text.indexOf(FENCE_OPEN, search, ignoreCase = true)
            if (start < 0) return last
            val trimmed = text.substring(start + FENCE_OPEN.length).trimStart()
            val closeIdx = trimmed.indexOf("```")
            if (closeIdx >= 0) {
                val payload = trimmed.substring(0, closeIdx).trim()
                last = PmResearchPack.firstJsonObject(payload) ?: payload.takeIf { it.startsWith("{") }
            }
            search = start + FENCE_OPEN.length
        }
    }
}
