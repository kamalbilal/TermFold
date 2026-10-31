package com.termfold.app.acp

import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Routes guest MCP `ask_user` calls to the active [AcpClient] for a coding session key.
 * Uses the same turn-footer UI as [AcpClient.handleAskQuestion] (`cursor/ask_question`).
 */
object AskUserBridge {

    private val clients = ConcurrentHashMap<String, AcpClient>()

    fun register(sessionKey: String, client: AcpClient) {
        clients[sessionKey] = client
    }

    fun unregister(sessionKey: String) {
        clients.remove(sessionKey)
    }

    /** Resolves the TermFold chat session key for MCP `ask_user` (coding or automation). */
    internal fun resolveSessionKey(args: JSONObject): String {
        val fromArgs = args.optString("session_key").trim()
        if (fromArgs.isNotEmpty()) return fromArgs
        return com.termfold.app.preview.BrowserBridge.codingSessionKey
            ?: com.termfold.app.preview.BrowserBridge.automationSessionKey
            ?: ""
    }

    suspend fun handle(args: JSONObject): JSONObject {
        val sessionKey = resolveSessionKey(args)
        val params = JSONObject()
        args.optString("title").takeIf { it.isNotBlank() }?.let { params.put("title", it) }
        args.optString("toolCallId").takeIf { it.isNotBlank() }?.let { params.put("toolCallId", it) }
        val questions = args.optJSONArray("questions")
            ?: return JSONObject().put("error", "questions[] is required")
        params.put("questions", questions)
        return ask(sessionKey, params)
    }

    suspend fun ask(sessionKey: String, params: JSONObject): JSONObject {
        if (sessionKey.isBlank()) {
            return JSONObject().put("error", "Missing agent session key")
        }
        val client = clients[sessionKey]
            ?: return JSONObject().put(
                "error",
                "No active TermFold agent chat is bound to this session. Keep the session open on screen.",
            )
        return client.awaitInteractiveAsk(params)
    }
}
