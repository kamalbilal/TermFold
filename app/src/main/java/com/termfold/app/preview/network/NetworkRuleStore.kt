package com.termfold.app.preview.network

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Base64
import java.util.UUID

class NetworkRuleStore(private val root: File) {
    private val lock = Any()

    init {
        root.mkdirs()
    }

    fun list(sessionKey: String): List<NetworkRule> = synchronized(lock) { listUnlocked(sessionKey) }

    private fun listUnlocked(sessionKey: String): List<NetworkRule> {
        val file = fileFor(sessionKey)
        if (!file.isFile) return emptyList()
        val text = runCatching { file.readText() }.getOrNull() ?: return emptyList()
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return emptyList()
        val arr = json.optJSONArray("rules") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i -> parseRule(arr.optJSONObject(i)) }
    }

    private fun readFile(file: File): Pair<String, List<NetworkRule>>? {
        if (!file.isFile) return null
        val text = runCatching { file.readText() }.getOrNull() ?: return null
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return null
        val key = json.optString("sessionKey")
        if (key.isBlank()) return null
        val arr = json.optJSONArray("rules") ?: JSONArray()
        val rules = (0 until arr.length()).mapNotNull { i -> parseRule(arr.optJSONObject(i)) }
        return key to rules
    }

    fun addBlock(sessionKey: String, pattern: String): NetworkRule {
        val trimmed = pattern.trim()
        require(trimmed.isNotEmpty()) { "url_pattern is blank." }
        val rule = NetworkRule(
            id = UUID.randomUUID().toString(),
            kind = NetworkRuleKind.BLOCK,
            pattern = trimmed,
        )
        synchronized(lock) { append(sessionKey, rule) }
        return rule
    }

    fun addOverride(
        sessionKey: String,
        pattern: String,
        body: String,
        status: Int = 200,
        headers: Map<String, String> = emptyMap(),
    ): NetworkRule {
        val trimmed = pattern.trim()
        require(trimmed.isNotEmpty()) { "url_pattern is blank." }
        val bytes = body.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BODY_BYTES) {
            "Override body is too large (${bytes.size} bytes; max $MAX_BODY_BYTES)."
        }
        val rule = NetworkRule(
            id = UUID.randomUUID().toString(),
            kind = NetworkRuleKind.OVERRIDE,
            pattern = trimmed,
            body = body,
            status = status,
            headers = headers,
        )
        synchronized(lock) { append(sessionKey, rule) }
        return rule
    }

    fun remove(sessionKey: String, id: String): Boolean = synchronized(lock) {
        val rules = listUnlocked(sessionKey)
        val next = rules.filterNot { it.id == id }
        if (next.size == rules.size) return false
        write(sessionKey, next)
        return true
    }

    fun clear(sessionKey: String) = synchronized(lock) {
        write(sessionKey, emptyList())
    }

    fun deleteSession(sessionKey: String) = synchronized(lock) {
        fileFor(sessionKey).delete()
    }

    fun firstMatch(sessionKey: String, url: String): NetworkRule? =
        list(sessionKey).firstOrNull { NetworkUrlGlob.matches(it.pattern, url) }

    fun allSessions(): Map<String, List<NetworkRule>> = synchronized(lock) {
        if (!root.isDirectory) return emptyMap()
        val out = linkedMapOf<String, List<NetworkRule>>()
        root.listFiles { f -> f.isFile && f.name.endsWith(".json") }.orEmpty().forEach { file ->
            val pair = readFile(file) ?: return@forEach
            out[pair.first] = pair.second
        }
        return out
    }

    fun rulesBySessionJson(): JSONObject {
        val out = JSONObject()
        allSessions().forEach { (key, rules) ->
            val arr = JSONArray()
            rules.forEach { arr.put(it.toJson()) }
            out.put(key, arr)
        }
        return out
    }

    private fun append(sessionKey: String, rule: NetworkRule) {
        write(sessionKey, listUnlocked(sessionKey) + rule)
    }

    private fun write(sessionKey: String, rules: List<NetworkRule>) {
        root.mkdirs()
        val arr = JSONArray()
        rules.forEach { arr.put(it.toJson()) }
        val json = JSONObject()
            .put("sessionKey", sessionKey)
            .put("rules", arr)
        fileFor(sessionKey).writeText(json.toString())
    }

    private fun fileFor(sessionKey: String) = File(root, encodeKey(sessionKey) + ".json")

    companion object {
        const val MAX_BODY_BYTES = 256 * 1024

        fun forApp(context: Context): NetworkRuleStore =
            NetworkRuleStore(File(context.applicationContext.filesDir, "network-rules"))

        fun encodeKey(sessionKey: String): String =
            Base64.getUrlEncoder().withoutPadding().encodeToString(sessionKey.toByteArray(Charsets.UTF_8))

        fun parseRule(obj: JSONObject?): NetworkRule? {
            if (obj == null) return null
            val id = obj.optString("id")
            val pattern = obj.optString("pattern")
            if (id.isBlank() || pattern.isBlank()) return null
            val kind = when (obj.optString("kind")) {
                "override" -> NetworkRuleKind.OVERRIDE
                else -> NetworkRuleKind.BLOCK
            }
            val headersObj = obj.optJSONObject("headers")
            val headers = mutableMapOf<String, String>()
            if (headersObj != null) {
                headersObj.keys().forEach { k -> headers[k] = headersObj.optString(k) }
            }
            return NetworkRule(
                id = id,
                kind = kind,
                pattern = pattern,
                body = obj.optString("body"),
                status = obj.optInt("status", 200),
                headers = headers,
            )
        }
    }
}

fun NetworkRule.toJson(): JSONObject {
    val headers = JSONObject()
    this.headers.forEach { (k, v) -> headers.put(k, v) }
    return JSONObject()
        .put("id", id)
        .put("kind", if (kind == NetworkRuleKind.OVERRIDE) "override" else "block")
        .put("pattern", pattern)
        .put("body", body)
        .put("status", status)
        .put("headers", headers)
}
