package com.termfold.app.pdftools

import org.json.JSONObject

data class PdfToolsInspectSummary(
    val ok: Boolean,
    val pages: Int = 0,
    val encrypted: Boolean = false,
    val signed: Boolean = false,
    val acroform: Boolean = false,
    val code: String? = null,
    val error: String? = null,
    val signatureNote: String? = null,
    val kinds: List<String> = emptyList(),
)

object PdfToolsInspect {
    fun parse(raw: String): PdfToolsInspectSummary {
        val obj = runCatching { JSONObject(raw) }.getOrNull()
            ?: return PdfToolsInspectSummary(ok = false, error = "invalid inspect json")
        val kinds = mutableListOf<String>()
        val arr = obj.optJSONArray("page_kinds")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                kinds += arr.optJSONObject(i)?.optString("kind").orEmpty()
            }
        }
        return PdfToolsInspectSummary(
            ok = obj.optBoolean("ok", obj.optString("code").isBlank()),
            pages = obj.optInt("pages"),
            encrypted = obj.optBoolean("encrypted") || obj.optString("code") == "ENCRYPTED",
            signed = obj.optBoolean("signed"),
            acroform = obj.optBoolean("acroform"),
            code = obj.optString("code").ifBlank { null },
            error = obj.optString("error").ifBlank { null },
            signatureNote = obj.optString("signature_note").ifBlank { null },
            kinds = kinds.filter { it.isNotBlank() },
        )
    }
}
