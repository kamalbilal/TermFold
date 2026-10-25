package com.termfold.app.shell

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.termfold.app.data.termfoldDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class DeviceStorageMirror(
    val id: String,
    val label: String,
    val treeUri: String,
    val guestName: String,
    val lastSyncedAtMs: Long = 0L,
) {
    fun hostDir(context: Context): File =
        File(DeviceStorageMounts.mirrorRoot(context), id)

    fun guestPath(): String = "${DeviceStorageMounts.GUEST_LINKED}/${guestName}"
}

object DeviceStorageMirrorStore {

    private val key = stringPreferencesKey("device_storage_mirrors_json")

    fun mirrorsFlow(context: Context): Flow<List<DeviceStorageMirror>> =
        context.termfoldDataStore.data.map { prefs -> decode(prefs[key]) }

    suspend fun mirrors(context: Context): List<DeviceStorageMirror> =
        mirrorsFlow(context).first()

    fun mirrorsBlocking(context: Context): List<DeviceStorageMirror> =
        runBlocking { mirrors(context) }

    suspend fun add(context: Context, label: String, treeUri: Uri): DeviceStorageMirror {
        val existing = mirrors(context)
        val id = "m${System.currentTimeMillis()}"
        val guestName = DeviceStorageMounts.uniqueGuestName(
            existing,
            sanitizeGuestName(label),
        )
        val mirror = DeviceStorageMirror(id, label.trim(), treeUri.toString(), guestName)
        update(context) { list -> list + mirror }
        return mirror
    }

    suspend fun remove(context: Context, id: String) {
        update(context) { list -> list.filter { it.id != id } }
        File(DeviceStorageMounts.mirrorRoot(context), id).deleteRecursively()
    }

    suspend fun markSynced(context: Context, id: String, atMs: Long = System.currentTimeMillis()) {
        update(context) { list ->
            list.map { if (it.id == id) it.copy(lastSyncedAtMs = atMs) else it }
        }
    }

    private suspend fun update(context: Context, transform: (List<DeviceStorageMirror>) -> List<DeviceStorageMirror>) {
        context.termfoldDataStore.edit { prefs ->
            prefs[key] = encode(transform(decode(prefs[key])))
        }
    }

    fun sanitizeGuestName(label: String): String {
        val base = label.trim()
            .replace(Regex("\\s+"), "-")
            .replace(Regex("[^A-Za-z0-9._-]"), "")
            .trim('-', '.')
            .ifEmpty { "folder" }
            .take(40)
        return base
    }

    private fun decode(raw: String?): List<DeviceStorageMirror> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { i ->
                val o = array.getJSONObject(i)
                DeviceStorageMirror(
                    id = o.getString("id"),
                    label = o.getString("label"),
                    treeUri = o.getString("treeUri"),
                    guestName = o.getString("guestName"),
                    lastSyncedAtMs = o.optLong("lastSyncedAtMs", 0L),
                )
            }
        }.getOrElse { emptyList() }
    }

    private fun encode(list: List<DeviceStorageMirror>): String {
        val array = JSONArray()
        list.forEach { m ->
            array.put(
                JSONObject()
                    .put("id", m.id)
                    .put("label", m.label)
                    .put("treeUri", m.treeUri)
                    .put("guestName", m.guestName)
                    .put("lastSyncedAtMs", m.lastSyncedAtMs),
            )
        }
        return array.toString()
    }
}
