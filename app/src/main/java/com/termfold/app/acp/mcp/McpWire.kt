package com.termfold.app.acp.mcp

import org.json.JSONArray
import org.json.JSONObject

/** Maps discovered [McpServerEntry] values to ACP `session/new` wire objects. */
object McpWire {

    fun toAcpJson(
        entry: McpServerEntry,
        vars: McpVariables,
        secrets: Map<String, String>,
    ): JSONObject? = toAcpJson(entry, vars::resolve, secrets)

    fun toAcpJson(
        entry: McpServerEntry,
        resolve: (String) -> String,
        secrets: Map<String, String>,
    ): JSONObject? = when (val transport = entry.transport) {
        is McpTransport.Stdio -> {
            val env = JSONArray()
            val merged = transport.env.toMutableMap()
            secrets.forEach { (name, value) -> merged[name] = value }
            merged.forEach { (name, value) ->
                val resolved = resolve(value)
                if (resolved.isNotBlank() && !resolved.contains("\${env:")) {
                    env.put(JSONObject().put("name", name).put("value", resolved))
                }
            }
            val command = resolve(transport.command)
            val args = JSONArray(transport.args.map { resolve(it) })
            JSONObject()
                .put("name", entry.name)
                .put("command", command)
                .put("args", args)
                .put("env", env)
        }
        is McpTransport.Remote -> {
            val headers = JSONArray()
            val merged = transport.headers.toMutableMap()
            secrets.forEach { (name, value) -> merged[name] = value }
            merged.forEach { (name, value) ->
                val resolved = resolve(value)
                if (resolved.isNotBlank() && !resolved.contains("\${env:")) {
                    headers.put(JSONObject().put("name", name).put("value", resolved))
                }
            }
            val type = when (transport.kind) {
                RemoteKind.HTTP -> "http"
                RemoteKind.SSE -> "sse"
            }
            JSONObject()
                .put("type", type)
                .put("name", entry.name)
                .put("url", resolve(transport.url))
                .put("headers", headers)
        }
    }
}
