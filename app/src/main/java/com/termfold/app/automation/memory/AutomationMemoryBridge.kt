package com.termfold.app.automation.memory

import android.content.Context
import com.termfold.app.automation.AutomationSessionContext
import com.termfold.app.memory.AgentMemorySessionContext
import org.json.JSONArray
import org.json.JSONObject

object AutomationMemoryBridge {

    fun handle(context: Context, action: String, args: JSONObject): JSONObject {
        val sessionKey = AutomationSessionContext.activeKey
            ?: AgentMemorySessionContext.activeKey
            ?: return err("No active agent session for memory")
        val givenKey = args.optString("session_key")
        if (givenKey.isNotBlank() && givenKey != sessionKey) {
            return err("Automation session mismatch")
        }
        val repo = AutomationMemoryRepository(context)
        return when (action) {
            "memory_session_search" -> {
                val query = args.optString("query").ifBlank { return err("query required") }
                val limit = args.optInt("limit", 5).coerceIn(1, 10)
                val rows = repo.searchSessionMemory(sessionKey, query, limit)
                okChunks(rows)
            }
            "memory_session_write" -> {
                val kind = args.optString("kind").ifBlank { return err("kind required") }
                val title = args.optString("title").ifBlank { return err("title required") }
                val body = args.optString("body")
                val searchText = args.optString("search_text", "$title $body")
                val siteKey = args.optString("site_key", "")
                repo.writeSessionMemory(sessionKey, kind, title, body, searchText, siteKey).fold(
                    onSuccess = { JSONObject().put("ok", true) },
                    onFailure = { JSONObject().put("error", it.message ?: "rejected") },
                )
            }
            "memory_session_get" -> {
                val id = args.optLong("id")
                if (id <= 0L) return err("id required")
                val chunk = repo.getMemoryChunk(id) ?: return err("Unknown chunk")
                if (chunk.automationSessionId != sessionKey) return err("Chunk not in this session")
                JSONObject()
                    .put("ok", true)
                    .put(
                        "chunk",
                        JSONObject()
                            .put("id", chunk.id)
                            .put("kind", chunk.kind)
                            .put("title", chunk.title)
                            .put("body", chunk.body)
                            .put("site_key", chunk.siteKey),
                    )
            }
            "memory_session_recent" -> {
                val limit = args.optInt("limit", 8).coerceIn(1, 20)
                val rows = repo.recentEvents(sessionKey, limit)
                val array = JSONArray()
                rows.forEach { event ->
                    array.put(
                        JSONObject()
                            .put("type", event.type)
                            .put("url", event.url)
                            .put("summary", event.summary)
                            .put("outcome", event.outcome)
                            .put("at", event.createdAtMs),
                    )
                }
                JSONObject().put("ok", true).put("events", array)
            }
            "memory_nav_search" -> {
                val siteKey = args.optString("site_key").ifBlank { return err("site_key required") }
                val query = args.optString("query").ifBlank { return err("query required") }
                val limit = args.optInt("limit", 5).coerceIn(1, 10)
                okChunks(repo.searchGlobalNav(siteKey, query, limit))
            }
            "memory_nav_write" -> {
                val siteKey = args.optString("site_key").ifBlank { return err("site_key required") }
                val kind = args.optString("kind").ifBlank { return err("kind required") }
                val title = args.optString("title").ifBlank { return err("title required") }
                val body = args.optString("body")
                val searchText = args.optString("search_text", "$title $body")
                repo.writeGlobalNav(siteKey, kind, title, body, searchText).fold(
                    onSuccess = { JSONObject().put("ok", true) },
                    onFailure = { JSONObject().put("error", it.message ?: "rejected") },
                )
            }
            "memory_secret_put" -> {
                val name = args.optString("name").ifBlank { return err("name required") }
                val payload = args.optString("payload")
                val redactedSummary = "stored secret: $name"
                repo.appendEvent(sessionKey, "secret", null, redactedSummary, "ok")
                SessionSecretStore.put(context, sessionKey, name, args.optString("description"), payload)
                JSONObject().put("ok", true)
            }
            "memory_secret_get" -> {
                val name = args.optString("name").ifBlank { return err("name required") }
                val value = SessionSecretStore.get(context, sessionKey, name)
                    ?: return err("Unknown secret")
                JSONObject().put("ok", true).put("payload", value)
            }
            else -> err("Unknown memory action")
        }
    }

    private fun okChunks(rows: List<MemoryChunkEntity>): JSONObject {
        val array = JSONArray()
        rows.forEach { chunk ->
            array.put(
                JSONObject()
                    .put("id", chunk.id)
                    .put("kind", chunk.kind)
                    .put("title", chunk.title)
                    .put("body", chunk.body)
                    .put("site_key", chunk.siteKey),
            )
        }
        return JSONObject().put("ok", true).put("chunks", array)
    }

    private fun err(message: String) = JSONObject().put("error", message)
}
