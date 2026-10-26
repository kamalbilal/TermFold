package com.termfold.app.preview.network

enum class NetworkRuleKind {
    BLOCK,
    OVERRIDE,
}

data class NetworkRule(
    val id: String,
    val kind: NetworkRuleKind,
    val pattern: String,
    val body: String = "",
    val status: Int = 200,
    val headers: Map<String, String> = emptyMap(),
) {
    fun bodyPreview(max: Int = 120): String {
        val trimmed = body.replace("\n", " ").trim()
        return if (trimmed.length <= max) trimmed else trimmed.take(max) + "…"
    }
}
