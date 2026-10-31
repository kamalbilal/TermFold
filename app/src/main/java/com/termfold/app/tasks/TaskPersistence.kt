package com.termfold.app.tasks

import org.json.JSONArray
import org.json.JSONObject

object TaskPersistence {

    fun decode(raw: String?): TasksData {
        if (raw.isNullOrBlank()) return TasksData()
        return runCatching {
            val root = JSONObject(raw)
            val array = root.optJSONArray("tasks") ?: JSONArray()
            TasksData(
                tasks = List(array.length()) { index ->
                    array.getJSONObject(index).toTask()
                },
            )
        }.getOrElse { TasksData() }
    }

    fun encode(data: TasksData): String {
        val array = JSONArray()
        data.tasks.forEach { array.put(it.toJson()) }
        return JSONObject().put("tasks", array).toString()
    }

    private fun JSONObject.toTask(): TaskItem {
        val attachments = optJSONArray("attachments") ?: JSONArray()
        return TaskItem(
            id = getString("id"),
            folderId = getString("folderId"),
            dayKey = getString("dayKey"),
            title = getString("title"),
            description = optString("description"),
            status = TaskStatus.fromWire(optString("status")),
            attachments = List(attachments.length()) { i ->
                attachments.getJSONObject(i).toAttachment()
            },
            updatedAtEpochMs = optLong("updatedAtEpochMs", 0L),
        )
    }

    private fun JSONObject.toAttachment() = TaskAttachment(
        displayName = getString("displayName"),
        path = getString("path"),
    )

    private fun TaskItem.toJson() = JSONObject()
        .put("id", id)
        .put("folderId", folderId)
        .put("dayKey", dayKey)
        .put("title", title)
        .put("description", description)
        .put("status", status.wire)
        .put(
            "attachments",
            JSONArray().also { arr ->
                attachments.forEach { att ->
                    arr.put(
                        JSONObject()
                            .put("displayName", att.displayName)
                            .put("path", att.path),
                    )
                }
            },
        )
        .put("updatedAtEpochMs", updatedAtEpochMs)
}
