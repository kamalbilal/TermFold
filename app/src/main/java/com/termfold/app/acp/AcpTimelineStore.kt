package com.termfold.app.acp

import android.content.Context
import java.io.File

/** Disk copy of the TermFold chat timeline for one session tab. */
object AcpTimelineStore {
    fun load(context: Context, sessionKey: String): AcpPersistedTimeline? {
        val file = file(context, sessionKey)
        if (!file.isFile) return null
        val text = runCatching { file.readText() }.getOrNull()?.takeIf { it.isNotBlank() } ?: return null
        val snapshot = runCatching { AcpTimelineCodec.decode(text) }.getOrNull() ?: return null
        return snapshot.takeIf { it.items.isNotEmpty() }
    }

    fun save(context: Context, sessionKey: String, snapshot: AcpPersistedTimeline) {
        val file = file(context, sessionKey)
        if (snapshot.items.isEmpty()) {
            file.delete()
            return
        }
        file.parentFile?.mkdirs()
        file.writeText(AcpTimelineCodec.encode(snapshot))
    }

    fun clear(context: Context, sessionKey: String) {
        file(context, sessionKey).delete()
    }

    private fun file(context: Context, sessionKey: String): File {
        val safe = sessionKey.replace(Regex("""[^\w.-]"""), "_")
        return File(context.filesDir, "acp-timelines/$safe.json")
    }
}
