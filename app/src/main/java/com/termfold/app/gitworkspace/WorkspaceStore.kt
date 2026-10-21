package com.termfold.app.gitworkspace

import android.content.Context
import com.termfold.app.core.Folder
import com.termfold.app.shell.Projects
import com.termfold.app.shell.ShellConfig
import org.json.JSONArray
import org.json.JSONObject

object WorkspaceStore {
    private const val PREFS = "git_workspaces"

    fun goldenGuestPath(context: Context, folder: Folder): String =
        Projects.guestPath(context, folder.path) ?: ShellConfig.GUEST_HOME

    fun guestPath(context: Context, folder: Folder, workspaceId: String): String? {
        if (workspaceId == WorkspaceIds.GOLDEN) return goldenGuestPath(context, folder)
        val record = get(context, folder.id, workspaceId) ?: return null
        if (!record.complete) return null
        return record.guestPath
    }

    fun listComplete(context: Context, goldenProjectId: String): List<WorkspaceRecord> =
        loadAll(context, goldenProjectId).filter { it.complete }.sortedByDescending { it.createdAt }

    fun get(context: Context, goldenProjectId: String, workspaceId: String): WorkspaceRecord? =
        loadAll(context, goldenProjectId).firstOrNull { it.id == workspaceId }

    fun insert(context: Context, record: WorkspaceRecord) {
        val all = loadAll(context, record.goldenProjectId).filter { it.id != record.id } + record
        saveAll(context, record.goldenProjectId, all)
    }

    fun remove(context: Context, goldenProjectId: String, workspaceId: String) {
        val all = loadAll(context, goldenProjectId).filter { it.id != workspaceId }
        saveAll(context, goldenProjectId, all)
    }

    fun updateBranch(context: Context, goldenProjectId: String, workspaceId: String, branch: String) {
        val all = loadAll(context, goldenProjectId).map { record ->
            if (record.id == workspaceId) record.copy(lastKnownBranch = branch) else record
        }
        saveAll(context, goldenProjectId, all)
    }

    private fun loadAll(context: Context, goldenProjectId: String): List<WorkspaceRecord> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(storageKey(goldenProjectId), null)
            ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                add(WorkspaceRecord.fromJson(obj))
            }
        }
    }

    private fun saveAll(context: Context, goldenProjectId: String, records: List<WorkspaceRecord>) {
        val array = JSONArray()
        records.forEach { array.put(it.toJson()) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(storageKey(goldenProjectId), array.toString())
            .apply()
    }

    private fun storageKey(goldenProjectId: String) = "workspaces_$goldenProjectId"
}
