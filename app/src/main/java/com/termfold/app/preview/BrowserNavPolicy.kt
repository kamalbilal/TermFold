package com.termfold.app.preview

/** Agent browser navigation; no host allowlist — any URL the WebView can load is permitted. */
object BrowserNavPolicy {

    fun normalizeHost(raw: String): String =
        raw.trim().lowercase().removePrefix("http://").removePrefix("https://")
            .substringBefore('/').substringBefore(':')

    /** @return null if allowed, else a short reason for the agent */
    fun checkAgentOpen(url: String): String? = if (url.isBlank()) "Invalid URL." else null
}
