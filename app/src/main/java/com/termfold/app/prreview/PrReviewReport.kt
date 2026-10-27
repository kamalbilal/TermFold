package com.termfold.app.prreview

import com.termfold.app.acp.AcpItem
import org.json.JSONArray
import org.json.JSONObject

enum class PrReviewRisk { LOW, MEDIUM, HIGH }

enum class PrReviewSurface { ANDROID, LINUX_GUEST, SHARED, CI_DOCS, IOS }

enum class PrReviewSeverity { BLOCKER, SHOULD_FIX, NIT, QUESTION }

data class PrReviewFinding(
    val id: String,
    val surface: PrReviewSurface,
    val severity: PrReviewSeverity,
    val title: String,
    val location: String,
    val inDiff: Boolean,
    val evidence: String,
    val suggestion: String,
)

data class PrReviewCoverage(
    val reviewed: List<String>,
    val skipped: List<String>,
)

data class PrReviewReport(
    val schemaVersion: Int,
    val summary: String,
    val risk: PrReviewRisk,
    val findings: List<PrReviewFinding>,
    val coverage: PrReviewCoverage,
    val missingTests: List<String>,
    val openQuestions: List<String>,
) {
    fun inDiffBySurface(): Map<PrReviewSurface, List<PrReviewFinding>> =
        findings.filter { it.inDiff }.groupBy { it.surface }

    fun outOfDiff(): List<PrReviewFinding> = findings.filterNot { it.inDiff }
}

sealed class PrReviewParse {
    data class Ok(val report: PrReviewReport) : PrReviewParse()
    data object Missing : PrReviewParse()
    data class Invalid(val reason: String) : PrReviewParse()
    /** Connect/idle/cancel — no completed review turn to score. */
    data object Skipped : PrReviewParse()
}

object PrReviewReportParser {
    const val FENCE = "pr-review-report"

    fun parseAgentText(text: String): PrReviewParse {
        val json = extractFence(text) ?: return PrReviewParse.Missing
        return parseJson(json)
    }

    fun parseTurn(items: List<AcpItem>): PrReviewParse {
        val lastUser = items.indexOfLast { it is AcpItem.UserMessage }
        if (lastUser < 0) return PrReviewParse.Skipped
        return parseTurnSlice(items.drop(lastUser))
    }

    /** [turnItems] must start with the user message for this turn. */
    fun parseTurnSlice(turnItems: List<AcpItem>): PrReviewParse {
        if (turnItems.isEmpty() || turnItems.first() !is AcpItem.UserMessage) return PrReviewParse.Skipped
        val agentText = turnItems
            .drop(1)
            .filterIsInstance<AcpItem.AgentText>()
            .joinToString("") { it.text }
        if (agentText.isBlank()) return PrReviewParse.Skipped
        return parseAgentText(agentText)
    }

    /** Drops fenced report JSON so chat is not a second copy of the cards. */
    fun stripFences(text: String): String {
        var remaining = text
        val open = "```$FENCE"
        while (true) {
            val start = remaining.indexOf(open)
            if (start < 0) return remaining.trim()
            val after = remaining.substring(start + open.length).trimStart()
            val closeIdx = after.indexOf("```")
            remaining = if (closeIdx < 0) {
                remaining.substring(0, start)
            } else {
                remaining.substring(0, start) + after.substring(closeIdx + 3)
            }
        }
    }

    fun extractFence(text: String): String? {
        val open = "```$FENCE"
        var search = 0
        var last: String? = null
        while (true) {
            val start = text.indexOf(open, search)
            if (start < 0) return last
            val trimmed = text.substring(start + open.length).trimStart()
            val closeIdx = trimmed.indexOf("```")
            if (closeIdx >= 0) last = trimmed.substring(0, closeIdx).trim()
            search = start + open.length
        }
    }

    fun parseJson(raw: String): PrReviewParse {
        val obj = runCatching { JSONObject(raw) }.getOrElse {
            return PrReviewParse.Invalid("Report JSON is not valid.")
        }
        val version = obj.optInt("schemaVersion", 0)
        if (version != 1) return PrReviewParse.Invalid("Unsupported schemaVersion.")
        val summary = obj.optString("summary")
        val risk = parseRisk(obj.optString("risk"))
            ?: return PrReviewParse.Invalid("Unknown risk.")
        val findingsJson = obj.optJSONArray("findings") ?: JSONArray()
        val findings = mutableListOf<PrReviewFinding>()
        for (i in 0 until findingsJson.length()) {
            val item = findingsJson.optJSONObject(i) ?: continue
            val surface = parseSurface(item.optString("surface"))
                ?: return PrReviewParse.Invalid("Unknown surface.")
            val severity = parseSeverity(item.optString("severity"))
                ?: return PrReviewParse.Invalid("Unknown severity.")
            val id = item.optString("id").trim()
            if (id.isEmpty()) return PrReviewParse.Invalid("Finding is missing id.")
            findings += PrReviewFinding(
                id = id,
                surface = surface,
                severity = severity,
                title = item.optString("title"),
                location = item.optString("location"),
                inDiff = jsonBoolean(item, "inDiff", default = true),
                evidence = item.optString("evidence"),
                suggestion = item.optString("suggestion"),
            )
        }
        val coverageObj = obj.optJSONObject("coverage")
        val coverage = PrReviewCoverage(
            reviewed = stringList(coverageObj?.optJSONArray("reviewed")),
            skipped = stringList(coverageObj?.optJSONArray("skipped")),
        )
        return PrReviewParse.Ok(
            PrReviewReport(
                schemaVersion = version,
                summary = summary,
                risk = risk,
                findings = findings,
                coverage = coverage,
                missingTests = stringList(obj.optJSONArray("missingTests")),
                openQuestions = stringList(obj.optJSONArray("openQuestions")),
            ),
        )
    }

    fun encode(report: PrReviewReport): JSONObject {
        val findings = JSONArray()
        report.findings.forEach { finding ->
            findings.put(
                JSONObject()
                    .put("id", finding.id)
                    .put("surface", wireSurface(finding.surface))
                    .put("severity", wireSeverity(finding.severity))
                    .put("title", finding.title)
                    .put("location", finding.location)
                    .put("inDiff", finding.inDiff)
                    .put("evidence", finding.evidence)
                    .put("suggestion", finding.suggestion),
            )
        }
        return JSONObject()
            .put("schemaVersion", report.schemaVersion)
            .put("summary", report.summary)
            .put("risk", wireRisk(report.risk))
            .put("findings", findings)
            .put(
                "coverage",
                JSONObject()
                    .put("reviewed", JSONArray(report.coverage.reviewed))
                    .put("skipped", JSONArray(report.coverage.skipped)),
            )
            .put("missingTests", JSONArray(report.missingTests))
            .put("openQuestions", JSONArray(report.openQuestions))
    }

    fun decodeStored(obj: JSONObject?): PrReviewReport? {
        if (obj == null) return null
        return when (val parsed = parseJson(obj.toString())) {
            is PrReviewParse.Ok -> parsed.report
            else -> null
        }
    }

    private fun jsonBoolean(obj: JSONObject, key: String, default: Boolean): Boolean {
        if (!obj.has(key) || obj.isNull(key)) return default
        return when (val value = obj.get(key)) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            is String -> value.equals("true", ignoreCase = true)
            else -> default
        }
    }

    private fun stringList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return List(array.length()) { array.optString(it) }.filter { it.isNotBlank() }
    }

    private fun parseRisk(raw: String): PrReviewRisk? = when (raw.lowercase()) {
        "low" -> PrReviewRisk.LOW
        "medium" -> PrReviewRisk.MEDIUM
        "high" -> PrReviewRisk.HIGH
        else -> null
    }

    private fun parseSurface(raw: String): PrReviewSurface? = when (raw.lowercase()) {
        "android" -> PrReviewSurface.ANDROID
        "linux-guest", "linux_guest" -> PrReviewSurface.LINUX_GUEST
        "shared" -> PrReviewSurface.SHARED
        "ci-docs", "ci_docs" -> PrReviewSurface.CI_DOCS
        "ios" -> PrReviewSurface.IOS
        else -> null
    }

    private fun parseSeverity(raw: String): PrReviewSeverity? = when (raw.lowercase()) {
        "blocker" -> PrReviewSeverity.BLOCKER
        "should-fix", "should_fix" -> PrReviewSeverity.SHOULD_FIX
        "nit" -> PrReviewSeverity.NIT
        "question" -> PrReviewSeverity.QUESTION
        else -> null
    }

    private fun wireRisk(value: PrReviewRisk): String = when (value) {
        PrReviewRisk.LOW -> "low"
        PrReviewRisk.MEDIUM -> "medium"
        PrReviewRisk.HIGH -> "high"
    }

    fun wireSurface(value: PrReviewSurface): String = when (value) {
        PrReviewSurface.ANDROID -> "android"
        PrReviewSurface.LINUX_GUEST -> "linux-guest"
        PrReviewSurface.SHARED -> "shared"
        PrReviewSurface.CI_DOCS -> "ci-docs"
        PrReviewSurface.IOS -> "ios"
    }

    private fun wireSeverity(value: PrReviewSeverity): String = when (value) {
        PrReviewSeverity.BLOCKER -> "blocker"
        PrReviewSeverity.SHOULD_FIX -> "should-fix"
        PrReviewSeverity.NIT -> "nit"
        PrReviewSeverity.QUESTION -> "question"
    }
}
