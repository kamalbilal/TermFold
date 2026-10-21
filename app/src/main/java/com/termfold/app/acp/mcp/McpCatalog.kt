package com.termfold.app.acp.mcp



import android.content.Context

import com.termfold.app.preview.BrowserBridge

import com.termfold.app.shell.Projects

import com.termfold.app.shell.ShellPaths

import com.termfold.app.shell.ShellSetup

import org.json.JSONArray

import java.io.File



/**

 * Discovers Cursor MCP config on disk and builds the ACP `mcpServers` array for `session/new`.

 */

object McpCatalog {



    private const val USER_CONFIG = "/root/.cursor/mcp.json"



    fun list(context: Context, workspaceGuestDir: String, cursorAgent: Boolean): List<McpServerEntry> {

        val merged = linkedMapOf<String, McpServerEntry>()

        if (cursorAgent) {

            val userFile = Projects.hostPath(context, USER_CONFIG)

            McpJson.parseFile(userFile).forEach { (name, raw) ->

                McpJson.entryFromJson(name, McpSource.USER, USER_CONFIG, raw, context, workspaceGuestDir)

                    ?.let { merged[it.name] = it }

            }

            val projectConfig = "$workspaceGuestDir/.cursor/mcp.json"

            val projectFile = Projects.hostPath(context, projectConfig)

            McpJson.parseFile(projectFile).forEach { (name, raw) ->

                McpJson.entryFromJson(name, McpSource.PROJECT, projectConfig, raw, context, workspaceGuestDir)

                    ?.let { merged[it.name] = it }

            }

        }

        builtinBrowser(context)?.let { merged[it.name] = it }

        return merged.values.toList().sortedWith(compareBy({ it.source.ordinal }, { it.name.lowercase() }))

    }



    fun acpServers(context: Context, agentId: String, workspaceGuestDir: String): JSONArray {

        val cursorAgent = agentId == "cursor"

        val guestEnv = if (cursorAgent) McpJson.loadGuestEnvFiles(context, workspaceGuestDir) else emptyMap()

        val array = JSONArray()

        list(context, workspaceGuestDir, cursorAgent).forEach { entry ->

            val configDefault = !entry.configDisabled

            if (!McpPrefs.isEnabled(context, entry.key, configDefault)) return@forEach

            val vars = McpVariables(context, workspaceGuestDir, entry.key, guestEnv)

            val secrets = McpPrefs.secretsFor(context, entry.key)

            McpWire.toAcpJson(entry, vars, secrets)?.let { array.put(it) }

        }

        return array

    }



    private fun builtinBrowser(context: Context): McpServerEntry? {

        val rootfs = ShellPaths.rootfsDir(context)

        val node = "/opt/node/bin/node"

        val tool = ShellSetup.BROWSER_TOOL

        if (!File(rootfs, node.trimStart('/')).exists() || !File(rootfs, tool.trimStart('/')).isFile) return null

        BrowserBridge.start(context)

        return McpServerEntry(

            key = McpJson.keyFor(McpSource.BUILTIN, "termfold-browser"),

            name = "termfold-browser",

            source = McpSource.BUILTIN,

            transport = McpTransport.Stdio(

                command = node,

                args = listOf(tool, "mcp"),

                env = emptyMap(),

            ),

            configPath = null,

            authHints = emptyList(),

            configDisabled = false,

        )

    }

}


