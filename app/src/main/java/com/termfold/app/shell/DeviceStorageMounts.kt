package com.termfold.app.shell

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Exposes device folders inside the Debian guest at [GUEST_ROOT] so terminals can `ls` and `cp`
 * into `~/projects`. Live binds are used when storage permission allows; SAF-linked folders are
 * mirrored under app storage (read-only copies for the guest).
 */
object DeviceStorageMounts {

    /** Live bind of the phone shared storage root (when permitted). */
    const val GUEST_ROOT = "/mnt/android"

    /** SAF-linked folder copies (not live; refresh in Settings). */
    const val GUEST_LINKED = "/mnt/android/linked"

    fun mirrorRoot(context: Context): File =
        File(ShellPaths.rootDir(context), "host-storage-mirrors").apply { mkdirs() }

    fun hasStoragePermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** Primary shared storage (`/storage/emulated/0` class), when READ permission allows. */
    fun directStorageRoot(context: Context): File? {
        if (!hasStoragePermission(context)) return null
        val root = Environment.getExternalStorageDirectory()
        return root.takeIf { it.isDirectory && it.canRead() }
    }

    /** Top-level folder names under shared storage (for Settings summary). */
    fun directStorageSummary(context: Context): String? {
        val root = directStorageRoot(context) ?: return null
        val kids = root.list()?.filter { name ->
            val f = File(root, name)
            f.isDirectory && f.canRead()
        }?.sorted()?.take(12) ?: return GUEST_ROOT
        return if (kids.isEmpty()) GUEST_ROOT else "$GUEST_ROOT (${kids.joinToString(", ")})"
    }

    /**
     * Pairs of `hostAbsolute:guestAbsolute` for [ProotCommand].
     * Live phone storage is the whole tree at [GUEST_ROOT]; linked SAF trees sit under [GUEST_LINKED].
     */
    fun prootBindSpecs(context: Context, mirrors: List<DeviceStorageMirror>): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        directStorageRoot(context)?.let { root ->
            out += root.absolutePath to GUEST_ROOT
        }
        for (mirror in mirrors) {
            val host = mirror.hostDir(context)
            if (host.isDirectory) {
                out += host.absolutePath to mirror.guestPath()
            }
        }
        return out
    }

    fun takePersistableTreePermission(context: Context, treeUri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching {
            context.contentResolver.takePersistableUriPermission(treeUri, flags)
        }
    }

    /** URI hint for the system folder picker (primary Downloads). */
    fun labelFromTreeUri(uri: Uri): String {
        val id = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return "folder"
        val tail = id.substringAfter(':', id).substringAfterLast('/')
        return tail.ifBlank { "folder" }
    }

    fun downloadsTreeUri(): Uri? = runCatching {
        DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents",
            "primary:Download",
        )
    }.getOrNull()

    suspend fun syncMirror(context: Context, mirror: DeviceStorageMirror): Int = withContext(Dispatchers.IO) {
        val dest = mirror.hostDir(context)
        dest.deleteRecursively()
        dest.mkdirs()
        val uri = Uri.parse(mirror.treeUri)
        val count = DocumentTreeExport.copyTreeTo(context, uri, dest)
        DeviceStorageMirrorStore.markSynced(context, mirror.id)
        count
    }

    suspend fun syncAllMirrors(context: Context) {
        val list = DeviceStorageMirrorStore.mirrors(context)
        for (mirror in list) {
            runCatching { syncMirror(context, mirror) }
        }
    }

    fun uniqueGuestName(existing: List<DeviceStorageMirror>, base: String): String {
        val taken = existing.map { it.guestName }.toSet()
        if (base !in taken) return base
        var n = 2
        while ("$base-$n" in taken) n++
        return "$base-$n"
    }
}
