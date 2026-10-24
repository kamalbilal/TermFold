package com.termfold.app.acp

import android.content.Context
import java.io.File

/** Disk copy of the TermFold chat timeline for one session tab, and per listed conversation. */
object AcpTimelineStore {
    fun load(context: Context, sessionKey: String, conversationId: String? = null): AcpPersistedTimeline? {
        val file = file(context, sessionKey, conversationId)
        if (!file.isFile) return null
        val text = runCatching { file.readText() }.getOrNull()?.takeIf { it.isNotBlank() } ?: return null
        val snapshot = runCatching { AcpTimelineCodec.decode(text) }.getOrNull() ?: return null
        return snapshot.takeIf { it.items.isNotEmpty() }
    }

    fun save(
        context: Context,
        sessionKey: String,
        snapshot: AcpPersistedTimeline,
        conversationId: String? = null,
    ) {
        val file = file(context, sessionKey, conversationId)
        if (snapshot.items.isEmpty()) {
            file.delete()
            return
        }
        file.parentFile?.mkdirs()
        file.writeText(AcpTimelineCodec.encode(snapshot))
    }

    fun clear(context: Context, sessionKey: String, conversationId: String? = null) {
        file(context, sessionKey, conversationId).delete()
    }

    private fun file(context: Context, sessionKey: String, conversationId: String?): File {
        val tab = sessionKey.replace(Regex("""[^\w.-]"""), "_")
        if (conversationId.isNullOrBlank()) {
            return File(context.filesDir, "acp-timelines/$tab.json")
        }
        val id = conversationId.replace(Regex("""[^\w.-]"""), "_")
        return File(context.filesDir, "acp-timelines/$tab/$id.json")
    }
}
