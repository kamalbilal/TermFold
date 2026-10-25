package com.termfold.app.automation.memory

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/** Rolls browser events into searchable episode chunks (async). */
object AutomationEpisodeTracker {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val buffers = ConcurrentHashMap<String, SessionBuffer>()

    private class SessionBuffer {
        @Volatile var lastUrl: String? = null
        val text = StringBuilder()
    }

    private fun bufferFor(sessionKey: String): SessionBuffer =
        buffers.getOrPut(sessionKey) { SessionBuffer() }

    fun onBrowserEvent(context: Context, sessionKey: String, url: String?, summary: String) {
        val host = url?.substringAfter("://")?.substringBefore('/')?.takeIf { it.isNotBlank() } ?: "unknown"
        val buf = bufferFor(sessionKey)
        val line = summary.trim()
        if (url != null && url != buf.lastUrl && buf.text.isNotEmpty()) {
            flush(context, sessionKey, buf, buf.lastUrl, host)
        }
        buf.lastUrl = url
        if (line.isNotEmpty()) {
            if (buf.text.isNotEmpty()) buf.text.append(" → ")
            buf.text.append(line)
        }
        if (buf.text.length > 400) {
            flush(context, sessionKey, buf, url, host)
        }
    }

    fun onHighlight(context: Context, sessionKey: String, siteKey: String, title: String, body: String) {
        scope.launch {
            AutomationMemoryRepository(context).upsertChunk(
                automationSessionId = sessionKey,
                siteKey = siteKey,
                kind = "highlight",
                title = title,
                searchText = "$title $body",
                body = body,
            )
        }
    }

    private fun flush(
        context: Context,
        sessionKey: String,
        buf: SessionBuffer,
        url: String?,
        host: String,
    ) {
        val text = buf.text.toString().trim()
        buf.text.clear()
        if (text.isEmpty()) return
        scope.launch {
            AutomationMemoryRepository(context).upsertChunk(
                automationSessionId = sessionKey,
                siteKey = host,
                kind = "episode",
                title = url ?: "browser flow",
                searchText = text,
                body = text,
            )
        }
    }
}
