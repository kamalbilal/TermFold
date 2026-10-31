package com.termfold.app.startup

import org.json.JSONArray
import org.json.JSONObject

object StartupCommandsPersistence {

    fun decode(raw: String?): StartupCommandsData {
        if (raw.isNullOrBlank()) return StartupCommandsData()
        return runCatching {
            val root = JSONObject(raw)
            val commandsArray = root.optJSONArray("commands") ?: JSONArray()
            val groupsArray = root.optJSONArray("groups") ?: JSONArray()
            StartupCommandsData(
                commands = List(commandsArray.length()) { index ->
                    commandsArray.getJSONObject(index).toCommand()
                },
                groups = List(groupsArray.length()) { index ->
                    groupsArray.getJSONObject(index).toGroup()
                },
            )
        }.getOrElse { StartupCommandsData() }
    }

    fun encode(data: StartupCommandsData): String {
        val commands = JSONArray()
        data.commands.forEach { commands.put(it.toJson()) }
        val groups = JSONArray()
        data.groups.forEach { groups.put(it.toJson()) }
        return JSONObject()
            .put("commands", commands)
            .put("groups", groups)
            .toString()
    }

    private fun JSONObject.toCommand() = StartupCommand(
        id = getString("id"),
        name = getString("name"),
        folderId = getString("folderId"),
        command = getString("command"),
    )

    private fun JSONObject.toGroup(): StartupCommandGroup {
        val idsArray = optJSONArray("commandIds") ?: JSONArray()
        val ids = buildList {
            for (i in 0 until idsArray.length()) {
                idsArray.optString(i).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
        return StartupCommandGroup(
            id = getString("id"),
            name = getString("name"),
            commandIds = ids,
        )
    }

    private fun StartupCommand.toJson() = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("folderId", folderId)
        .put("command", command)

    private fun StartupCommandGroup.toJson(): JSONObject {
        val ids = JSONArray()
        commandIds.forEach { ids.put(it) }
        return JSONObject()
            .put("id", id)
            .put("name", name)
            .put("commandIds", ids)
    }
}
