package com.termfold.app.automation

object AutomationInjectCaps {
    const val MAX_TOTAL_CHARS = 4_000
    const val MAX_RECENT_EVENTS = 6
    const val MAX_SESSION_CHUNKS = 3
    const val MAX_GLOBAL_CHUNKS = 2
    const val MAX_CHUNK_CHARS = 220

    fun trimBlock(text: String): String =
        if (text.length <= MAX_TOTAL_CHARS) text else text.take(MAX_TOTAL_CHARS) + "\n…"
}
