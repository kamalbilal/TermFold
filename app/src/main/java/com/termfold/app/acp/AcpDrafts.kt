package com.termfold.app.acp

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Unsent prompt text and attachments for one TermFold session tab. */
object AcpDrafts {
    private const val PREFS = "acp"
    private const val MANIFEST = "manifest.json"

    fun load(context: Context, sessionKey: String): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(textKey(sessionKey), "")
            .orEmpty()

    fun save(context: Context, sessionKey: String, text: String) {
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (text.isBlank()) {
            editor.remove(textKey(sessionKey))
        } else {
            editor.putString(textKey(sessionKey), text)
        }
        editor.apply()
    }

    fun loadImages(context: Context, sessionKey: String): List<AcpBlock.Image> =
        loadManifest(context, sessionKey)?.optJSONArray("images")?.let { array ->
            val dir = draftDir(context, sessionKey)
            buildList {
                for (i in 0 until array.length()) {
                    val entry = array.optJSONObject(i) ?: continue
                    val name = entry.optString("file")
                    val mime = entry.optString("mime", "image/jpeg")
                    val bytes = File(dir, name).takeIf { it.isFile }?.readBytes() ?: continue
                    add(
                        AcpBlock.Image(
                            Base64.encodeToString(bytes, Base64.NO_WRAP),
                            mime,
                        ),
                    )
                }
            }
        } ?: emptyList()

    fun loadFiles(context: Context, sessionKey: String): List<AcpBlock.TextFile> =
        loadManifest(context, sessionKey)?.optJSONArray("files")?.let { array ->
            val dir = draftDir(context, sessionKey)
            buildList {
                for (i in 0 until array.length()) {
                    val entry = array.optJSONObject(i) ?: continue
                    val name = entry.optString("name")
                    val file = entry.optString("file")
                    val text = File(dir, file).takeIf { it.isFile }?.readText() ?: continue
                    add(AcpBlock.TextFile(name, text))
                }
            }
        } ?: emptyList()

    fun saveAttachments(
        context: Context,
        sessionKey: String,
        images: List<AcpBlock.Image>,
        files: List<AcpBlock.TextFile>,
    ) {
        val dir = draftDir(context, sessionKey)
        dir.listFiles()?.forEach { it.delete() }
        if (images.isEmpty() && files.isEmpty()) return

        val manifest = JSONObject()
        val imageEntries = JSONArray()
        images.forEachIndexed { index, image ->
            val ext = if (image.mimeType == "image/png") "png" else "jpg"
            val onDisk = "img-$index.$ext"
            File(dir, onDisk).writeBytes(Base64.decode(image.base64, Base64.NO_WRAP))
            imageEntries.put(
                JSONObject()
                    .put("file", onDisk)
                    .put("mime", image.mimeType),
            )
        }
        manifest.put("images", imageEntries)

        val fileEntries = JSONArray()
        files.forEachIndexed { index, file ->
            val onDisk = "file-$index.txt"
            File(dir, onDisk).writeText(file.text)
            fileEntries.put(
                JSONObject()
                    .put("name", file.name)
                    .put("file", onDisk),
            )
        }
        manifest.put("files", fileEntries)

        File(dir, MANIFEST).writeText(manifest.toString())
    }

    fun clearAttachments(context: Context, sessionKey: String) {
        draftDir(context, sessionKey).deleteRecursively()
    }

    private fun loadManifest(context: Context, sessionKey: String): JSONObject? {
        val file = File(draftDir(context, sessionKey), MANIFEST)
        if (!file.isFile) return null
        return runCatching { JSONObject(file.readText()) }.getOrNull()
    }

    private fun draftDir(context: Context, sessionKey: String): File {
        val safe = sessionKey.replace(Regex("""[^\w.-]"""), "_")
        return File(context.filesDir, "acp-drafts/$safe").also { it.mkdirs() }
    }

    private fun textKey(sessionKey: String) = "draft_$sessionKey"
}
