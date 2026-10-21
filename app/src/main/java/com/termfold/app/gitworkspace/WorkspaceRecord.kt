package com.termfold.app.gitworkspace

import org.json.JSONObject

data class WorkspaceRecord(
    val id: String,
    val goldenProjectId: String,
    val guestPath: String,
    val createdAt: Long,
    val sourceBranch: String,
    val lastKnownBranch: String,
    val complete: Boolean,
    val label: String? = null,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("goldenProjectId", goldenProjectId)
        .put("guestPath", guestPath)
        .put("createdAt", createdAt)
        .put("sourceBranch", sourceBranch)
        .put("lastKnownBranch", lastKnownBranch)
        .put("complete", complete)
        .put("label", label)

    companion object {
        fun fromJson(obj: JSONObject): WorkspaceRecord = WorkspaceRecord(
            id = obj.getString("id"),
            goldenProjectId = obj.getString("goldenProjectId"),
            guestPath = obj.getString("guestPath"),
            createdAt = obj.getLong("createdAt"),
            sourceBranch = obj.getString("sourceBranch"),
            lastKnownBranch = obj.getString("lastKnownBranch"),
            complete = obj.optBoolean("complete", true),
            label = obj.optString("label").takeIf { it.isNotBlank() },
        )
    }
}
