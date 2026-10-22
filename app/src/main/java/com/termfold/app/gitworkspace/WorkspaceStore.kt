package com.termfold.app.gitworkspace

import android.content.Context
import com.termfold.app.core.Folder
import com.termfold.app.shell.Projects
import com.termfold.app.shell.ShellConfig
import com.termfold.app.data.FolderStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

object WorkspaceStore {
    private const val PREFS = "git_workspaces"

    fun goldenGuestPath(context: Context, folder: Folder): String =
        Projects.guestPath(context, folder.path) ?: ShellConfig.GUEST_HOME

    /** Golden checkout path for a managed session copy (for git origin sync). */
    fun findGoldenGuestPathForSessionCopy(context: Context, sessionGuestPath: String): String? {
        val normalized = sessionGuestPath.trimEnd('/')
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        for ((key, value) in prefs.all) {
            if (key !is String || !key.startsWith("workspaces_") || value !is String) continue
            val goldenProjectId = key.removePrefix("workspaces_")
            val array = runCatching { JSONArray(value) }.getOrNull() ?: continue
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                if (!obj.optBoolean("complete", true)) continue
                val path = obj.optString("guestPath").trimEnd('/')
                if (path != normalized) continue
                val folder = runBlocking(Dispatchers.IO) {
                    FolderStore(context).data.first().folders.firstOrNull { it.id == goldenProjectId }
                } ?: return null
                return goldenGuestPath(context, folder)
            }
        }
        return null
    }

    fun guestPath(context: Context, folder: Folder, workspaceId: String): String? {
        if (workspaceId == WorkspaceIds.GOLDEN) return goldenGuestPath(context, folder)
        val record = resolveSessionRecord(context, folder, workspaceId) ?: return null
        if (!record.complete) return null
        return record.guestPath
    }

    fun resolveSessionRecord(context: Context, folder: Folder, workspaceId: String): WorkspaceRecord? {
        val record = get(context, folder.id, workspaceId) ?: return null
        val golden = goldenGuestPath(context, folder)
        if (!ProjectWorkspaces.isLegacyManagedSessionPath(golden, record.guestPath)) {
            return record
        }
        val migrated = runBlocking(Dispatchers.IO) {
            SessionWorkspaceMigration.migrateIfNeeded(context, golden, record)
        }
        if (migrated != record) insert(context, migrated)
        return migrated
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
