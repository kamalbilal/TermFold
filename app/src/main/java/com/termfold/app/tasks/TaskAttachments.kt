package com.termfold.app.tasks

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.util.UUID

object TaskAttachments {

    fun taskDir(context: Context, taskId: String): File =
        File(context.filesDir, "tasks/$taskId").apply { mkdirs() }

    fun copyFromUris(context: Context, taskId: String, uris: List<Uri>): List<TaskAttachment> {
        if (uris.isEmpty()) return emptyList()
        val dir = taskDir(context, taskId)
        return uris.mapNotNull { uri -> copyOne(context, dir, uri) }
    }

    fun deleteTaskFiles(context: Context, taskId: String) {
        taskDir(context, taskId).deleteRecursively()
    }

    private fun copyOne(context: Context, dir: File, uri: Uri): TaskAttachment? {
        val resolver = context.contentResolver
        val displayName = queryDisplayName(resolver, uri) ?: "attachment"
        val ext = displayName.substringAfterLast('.', "").takeIf { it.length in 1..8 }?.let { ".$it" }.orEmpty()
        val out = File(dir, "${UUID.randomUUID()}$ext")
        return runCatching {
            resolver.openInputStream(uri)?.use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            TaskAttachment(displayName = displayName, path = out.absolutePath)
        }.getOrNull()
    }

    private fun queryDisplayName(resolver: android.content.ContentResolver, uri: Uri): String? {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) return cursor.getString(idx)
        }
        return uri.lastPathSegment
    }
}
