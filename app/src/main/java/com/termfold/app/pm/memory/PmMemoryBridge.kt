package com.termfold.app.pm.memory

import android.content.Context
import com.termfold.app.pm.PmSessionContext
import org.json.JSONArray
import org.json.JSONObject

object PmMemoryBridge {
    fun handle(context: Context, action: String, args: JSONObject): JSONObject {
        val sessionId = PmSessionContext.activeSessionId
            ?: return err("No active PM session")
        val givenId = args.optString("session_id")
        if (givenId.isNotBlank() && givenId != sessionId) {
            return err("PM session mismatch")
        }
        val repo = PmMemoryRepository(context, sessionId)
        return when (action) {
            "pm_memory_search" -> {
                val query = args.optString("query").ifBlank { return err("query required") }
                val limit = args.optInt("limit", 5).coerceIn(1, 15)
                okChunks(repo.search(query, limit))
            }
            "pm_memory_write" -> {
                val targetId = args.optString("session_id", sessionId)
                if (targetId != sessionId) return err("Cannot write memory for another session")
                val kind = args.optString("kind").ifBlank { return err("kind required") }
                val title = args.optString("title").ifBlank { return err("title required") }
                val body = args.optString("body")
                val searchText = args.optString("search_text", "$title $body")
                repo.upsertChunk(kind = kind, title = title, searchText = searchText, body = body)
                JSONObject().put("ok", true)
            }
            "pm_memory_get" -> {
                val id = args.optLong("id")
                if (id <= 0L) return err("id required")
                val chunk = repo.getMemoryChunk(id) ?: return err("Unknown chunk")
                if (chunk.automationSessionId != sessionId) return err("Chunk not in this session")
                JSONObject()
                    .put("ok", true)
                    .put(
                        "chunk",
                        JSONObject()
                            .put("id", chunk.id)
                            .put("kind", chunk.kind)
                            .put("title", chunk.title)
                            .put("body", chunk.body),
                    )
            }
            else -> err("Unknown PM memory action")
        }
    }

    private fun okChunks(rows: List<com.termfold.app.automation.memory.MemoryChunkEntity>): JSONObject {
        val array = JSONArray()
        rows.forEach { chunk ->
            array.put(
                JSONObject()
                    .put("id", chunk.id)
                    .put("kind", chunk.kind)
                    .put("title", chunk.title)
                    .put("body", chunk.body),
            )
        }
        return JSONObject().put("ok", true).put("chunks", array)
    }

    private fun err(message: String) = JSONObject().put("error", message)
}
