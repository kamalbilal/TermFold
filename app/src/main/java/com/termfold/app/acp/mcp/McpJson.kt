package com.termfold.app.acp.mcp

import android.content.Context
import com.termfold.app.shell.Projects
import org.json.JSONObject
import java.io.File

/**
 * Reads Cursor-style `mcp.json` (`{ "mcpServers": { "name": { … } } }`).
 */
object McpJson {

    fun parseFile(file: File): Map<String, JSONObject> {
        if (!file.isFile) return emptyMap()
        return runCatching { parseText(file.readText()) }.getOrDefault(emptyMap())
    }

    fun parseText(body: String): Map<String, JSONObject> {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return emptyMap()
        val servers = root.optJSONObject("mcpServers") ?: return emptyMap()
        return buildMap {
            servers.keys().forEach { name ->
                servers.optJSONObject(name)?.let { put(name, it) }
            }
        }
    }

    fun entryFromJson(
        name: String,
        source: McpSource,
        configPath: String?,
        raw: JSONObject,
        context: android.content.Context,
        workspaceGuestDir: String,
    ): McpServerEntry? {
        val configDisabled = raw.optBoolean("disabled", false)
        val guestEnv = loadGuestEnvFiles(context, workspaceGuestDir)
        val vars = McpVariables(context, workspaceGuestDir, keyFor(source, name), guestEnv)
        val url = raw.optString("url").ifBlank { null }
        if (url != null) {
            val headers = stringMap(raw.optJSONObject("headers"), vars)
            val kind = when {
                raw.optString("type").equals("sse", ignoreCase = true) -> RemoteKind.SSE
                url.contains("/sse", ignoreCase = true) -> RemoteKind.SSE
                else -> RemoteKind.HTTP
            }
            val authHints = authHintsFromMap(headers)
            return McpServerEntry(
                key = keyFor(source, name),
                name = name,
                source = source,
                transport = McpTransport.Remote(
                    url = vars.resolve(url),
                    headers = headers,
                    kind = kind,
                ),
                configPath = configPath,
                authHints = authHints,
                configDisabled = configDisabled,
            )
        }
        val command = raw.optString("command").ifBlank { null } ?: return null
        val args = raw.optJSONArray("args")?.let { arr ->
            List(arr.length()) { i -> vars.resolve(arr.optString(i)) }
        } ?: emptyList()
        var env = stringMap(raw.optJSONObject("env"), vars)
        raw.optString("envFile").ifBlank { null }?.let { path ->
            env = env + loadEnvFile(context, vars.resolve(path))
        }
        val authHints = authHintsFromMap(env)
        return McpServerEntry(
            key = keyFor(source, name),
            name = name,
            source = source,
            transport = McpTransport.Stdio(
                command = vars.resolve(command),
                args = args,
                env = env,
            ),
            configPath = configPath,
            authHints = authHints,
            configDisabled = configDisabled,
        )
    }

    fun loadGuestEnvFiles(context: Context, workspaceGuestDir: String): Map<String, String> {
        val paths = listOf(
            "/root/.cursor/mcp.env",
            "$workspaceGuestDir/.cursor/mcp.env",
        )
        return paths.fold(emptyMap()) { acc, guestPath ->
            acc + loadEnvFile(context, guestPath)
        }
    }

    fun keyFor(source: McpSource, name: String): String =
        when (source) {
            McpSource.USER -> "user:$name"
            McpSource.PROJECT -> "project:$name"
            McpSource.BUILTIN -> "builtin:$name"
        }

    private fun stringMap(obj: JSONObject?, vars: McpVariables): Map<String, String> {
        if (obj == null) return emptyMap()
        return buildMap {
            obj.keys().forEach { key ->
                put(key, vars.resolve(obj.optString(key)))
            }
        }
    }

    fun loadEnvFile(context: Context, guestPath: String): Map<String, String> =
        parseEnvFile(Projects.hostPath(context, guestPath))

    internal fun parseEnvFile(file: File): Map<String, String> {
        if (!file.isFile) return emptyMap()
        return buildMap {
            file.readLines().forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach
                val eq = trimmed.indexOf('=')
                if (eq <= 0) return@forEach
                put(trimmed.substring(0, eq).trim(), trimmed.substring(eq + 1).trim())
            }
        }
    }

    private fun authHintsFromMap(map: Map<String, String>): List<String> {
        val names = mutableSetOf<String>()
        map.forEach { (key, value) ->
            ENV_REF.findAll(value).forEach { match -> names.add(match.groupValues[1]) }
            if (value.isBlank() || value.contains("\${env:")) names.add(key)
        }
        return names.sorted()
    }

    private val ENV_REF = Regex("""\$\{env:([^}]+)\}""")
}
