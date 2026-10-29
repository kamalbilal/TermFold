package com.termfold.app.pdftools

import org.json.JSONArray
import org.json.JSONObject

data class PdfToolsPlanParse(
    val ok: Boolean,
    val planId: String? = null,
    val error: String? = null,
    val overlay: Boolean = false,
)

object PdfToolsPlan {
    private val overlayKinds = setOf(
        "overlay",
        "cover",
        "whiteout",
        "redact_overlay",
        "BACKGROUND_OVERLAY",
        "strategy_d",
    )
    private val forbiddenFormKinds = setOf(
        "fill_form",
        "set_fields",
        "apply_fields",
    )

    fun parse(raw: String): PdfToolsPlanParse {
        val obj = runCatching { JSONObject(raw) }.getOrNull()
            ?: return PdfToolsPlanParse(ok = false, error = "Plan is not JSON")
        val ops = obj.optJSONArray("ops") ?: JSONArray()
        for (i in 0 until ops.length()) {
            val op = ops.optJSONObject(i) ?: continue
            val kind = op.optString("kind").ifBlank { op.optString("strategy") }
            if (kind.lowercase() in overlayKinds || op.optBoolean("overlay")) {
                return PdfToolsPlanParse(ok = false, overlay = true, error = "Overlay cover-up of existing text is forbidden")
            }
            if (kind.lowercase() in forbiddenFormKinds) {
                return PdfToolsPlanParse(
                    ok = false,
                    error = "Use replace_text with exact on-page words; form fill is not allowed for printed names",
                )
            }
        }
        val id = obj.optString("id").ifBlank { obj.optString("planId") }.ifBlank { "plan" }
        return PdfToolsPlanParse(ok = true, planId = id)
    }
}
