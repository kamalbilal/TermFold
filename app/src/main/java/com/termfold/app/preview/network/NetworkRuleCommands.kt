package com.termfold.app.preview.network

import org.json.JSONObject

object NetworkRuleCommands {
    fun execute(store: NetworkRuleStore, sessionKey: String, args: JSONObject): JSONObject {
        val op = args.optString("op").ifBlank { args.optString("action") }
        return when (op) {
            "list" -> list(store, sessionKey)
            "add_block" -> {
                val pattern = args.optString("url_pattern").ifBlank { args.optString("pattern") }
                if (pattern.isBlank()) return error("Give url_pattern to block.")
                val rule = store.addBlock(sessionKey, pattern)
                ok("Blocked ${rule.pattern} (${rule.id}).")
            }
            "add_override" -> {
                val pattern = args.optString("url_pattern").ifBlank { args.optString("pattern") }
                if (pattern.isBlank()) return error("Give url_pattern to override.")
                val body = args.optString("body")
                val status = if (args.has("status")) args.optInt("status") else 200
                val headers = mutableMapOf<String, String>()
                args.optJSONObject("headers")?.let { obj ->
                    obj.keys().forEach { k -> headers[k] = obj.optString(k) }
                }
                val rule = runCatching {
                    store.addOverride(sessionKey, pattern, body, status, headers)
                }.getOrElse { return error(it.message ?: "Could not add override.") }
                ok("Override ${rule.pattern} (${rule.id}).")
            }
            "remove" -> {
                val id = args.optString("id")
                if (id.isBlank()) return error("Give rule id.")
                if (!store.remove(sessionKey, id)) return error("No rule $id in this session.")
                ok("Removed $id.")
            }
            "clear" -> {
                store.clear(sessionKey)
                ok("Cleared network rules for this session.")
            }
            else -> error("Unknown op \"$op\". Use list, add_block, add_override, remove, or clear.")
        }
    }

    fun list(store: NetworkRuleStore, sessionKey: String): JSONObject {
        val rules = store.list(sessionKey)
        if (rules.isEmpty()) return ok("No network rules for this session.")
        val text = buildString {
            append("Network rules (").append(rules.size).append("):\n")
            rules.forEach { rule ->
                append(rule.id).append("  ")
                append(if (rule.kind == NetworkRuleKind.BLOCK) "block" else "override").append("  ")
                append(rule.pattern)
                if (rule.kind == NetworkRuleKind.OVERRIDE && rule.body.isNotBlank()) {
                    append("  ").append(rule.bodyPreview())
                }
                append('\n')
            }
        }.trimEnd()
        return ok(text)
    }

    private fun ok(text: String) = JSONObject().put("text", text)
    private fun error(text: String) = JSONObject().put("error", text)
}
