package com.termfold.app.shell

import android.content.Context
import com.termfold.app.acp.AcpInstaller
import com.termfold.app.data.FolderStore
import java.io.File

enum class GuestStorageCategory {
    HOST,
    PROJECT,
    CACHE,
    TOOLCHAIN,
    SYSTEM,
}

enum class GuestStorageDeletePolicy {
    NONE,
    CACHE,
    PROJECT,
    MIGRATION,
}

/** One row in the storage tree (parent or child). */
data class GuestStorageNode(
    val id: String,
    val label: String,
    val subtitle: String,
    val bytes: Long,
    val category: GuestStorageCategory,
    val deletePolicy: GuestStorageDeletePolicy,
    val hostPath: File? = null,
    val guestPath: String? = null,
    /** When true, tap loads immediate subfolders via [GuestStorageAnalyzer.loadChildren]. */
    val expandable: Boolean = false,
)

data class GuestStorageEntry(
    val id: String,
    val label: String,
    val subtitle: String,
    val bytes: Long,
    val category: GuestStorageCategory,
    val deletePolicy: GuestStorageDeletePolicy,
    val hostPath: File? = null,
    val guestPath: String? = null,
)

fun GuestStorageNode.toEntry(): GuestStorageEntry = GuestStorageEntry(
    id = id,
    label = label,
    subtitle = subtitle,
    bytes = bytes,
    category = category,
    deletePolicy = deletePolicy,
    hostPath = hostPath,
    guestPath = guestPath,
)

data class GuestStorageReport(
    val totalBytes: Long,
    val entries: List<GuestStorageEntry>,
    val scannedAtMillis: Long,
)

object GuestStorageAnalyzer {

    private val GUEST_TOP_LEVEL = listOf("/root", "/var", "/opt", "/usr", "/tmp")

    /** Fast host-side folders only (never walks the whole rootfs on the JVM). */
    fun scanHostLayer(context: Context): List<GuestStorageNode> {
        val root = ShellPaths.rootDir(context)
        val temp = ShellPaths.tempDir(context)
        val out = mutableListOf<GuestStorageNode>()
        if (temp.isDirectory) {
            out += hostNode(
                id = "host_scratch",
                label = "Runtime scratch",
                subtitle = temp.name,
                dir = temp,
                category = GuestStorageCategory.CACHE,
                deletePolicy = GuestStorageDeletePolicy.CACHE,
            )
        }
        listOf(
            ShellConfig.ROOTFS_STAGING_DIR to "Distro staging",
            ShellConfig.ROOTFS_OLD_DIR to "Old Ubuntu migration",
        ).forEach { (name, title) ->
            val dir = File(root, name)
            if (dir.isDirectory) {
                out += hostNode(
                    id = "host_$name",
                    label = title,
                    subtitle = dir.name,
                    dir = dir,
                    category = GuestStorageCategory.HOST,
                    deletePolicy = GuestStorageDeletePolicy.MIGRATION,
                )
            }
        }
        val mirrors = File(root, "host-storage-mirrors")
        if (mirrors.isDirectory) {
            out += hostNode(
                id = "host_mirrors",
                label = "Linked folder mirrors",
                subtitle = mirrors.name,
                dir = mirrors,
                category = GuestStorageCategory.HOST,
                deletePolicy = GuestStorageDeletePolicy.CACHE,
            )
        }
        return out.sortedByDescending { it.bytes }
    }

    /**
     * Measures one guest directory with `du` (single subtree pass in native code).
     * [onProgressGuestPath] is the path shown in the status line while this runs.
     */
    fun measureGuestPath(context: Context, guestPath: String): Long? {
        if (!ShellRuntime.isReady(context)) return null
        val script = "export PATH=/usr/bin:/bin; du -sx --block-size=1 ${shellQuote(guestPath)} 2>/dev/null | tail -1"
        val (_, out) = AcpInstaller.runCapture(context, script, guestCwd = "/root", timeoutSeconds = 600)
        return parseDuOutput(out).lastOrNull()?.second
    }

    /**
     * Immediate subfolders of [parentGuestPath], sorted largest first.
     * Uses `du --max-depth=1` (does not rescan the whole 25GB tree from Java).
     */
    fun loadChildren(context: Context, parentGuestPath: String): List<GuestStorageNode> {
        if (!ShellRuntime.isReady(context)) return emptyList()
        val script = """
            export PATH=/usr/bin:/bin
            du -sx --block-size=1 --max-depth=1 ${shellQuote(parentGuestPath)} 2>/dev/null
        """.trimIndent()
        val (_, out) = AcpInstaller.runCapture(context, script, guestCwd = "/root", timeoutSeconds = 600)
        return parseDuChildren(parentGuestPath, parseDuOutput(out))
            .map { (path, bytes) -> nodeFromGuestPath(context, path, bytes) }
            .sortedByDescending { it.bytes }
    }

    fun nodeFromGuestPath(context: Context, guestPath: String, bytes: Long): GuestStorageNode {
        val meta = guestPathMeta(guestPath)
        val name = guestPath.substringAfterLast('/').ifBlank { guestPath }
        val isProject = guestPath.startsWith("${Projects.GUEST_DIR}/") &&
            guestPath.removePrefix("${Projects.GUEST_DIR}/").isNotBlank()
        val projectLeaf = isProject && !guestPath.removePrefix("${Projects.GUEST_DIR}/").contains('/')
        val expandable = when {
            projectLeaf -> false
            guestPath == Projects.GUEST_DIR -> true
            guestPath.startsWith("${Projects.GUEST_DIR}/") -> true
            meta.third == GuestStorageDeletePolicy.CACHE && guestPath.startsWith("/opt/") -> true
            guestPath in listOf("/root", "/var", "/opt", "/usr", "/var/cache") -> true
            else -> bytes > 0 && meta.third == GuestStorageDeletePolicy.NONE
        }
        return GuestStorageNode(
            id = "guest_${guestPath.hashCode()}",
            label = if (projectLeaf) name else meta.first,
            subtitle = guestPath,
            bytes = bytes,
            category = if (projectLeaf) GuestStorageCategory.PROJECT else meta.second,
            deletePolicy = if (projectLeaf) GuestStorageDeletePolicy.PROJECT else meta.third,
            guestPath = guestPath,
            hostPath = Projects.hostPath(context, guestPath),
            expandable = expandable,
        )
    }

    /** @deprecated Use progressive scan from UI; kept for tests. */
    fun scan(context: Context): GuestStorageReport {
        val host = scanHostLayer(context)
        val guest = if (ShellRuntime.isReady(context)) {
            GUEST_TOP_LEVEL.mapNotNull { path ->
                measureGuestPath(context, path)?.let { nodeFromGuestPath(context, path, it) }
            }
        } else {
            emptyList()
        }
        val entries = (host + guest).map { it.toEntry() }
        val total = entries.sumOf { it.bytes }
        return GuestStorageReport(total, entries.sortedByDescending { it.bytes }, System.currentTimeMillis())
    }

    suspend fun delete(context: Context, entry: GuestStorageEntry): String? {
        if (entry.deletePolicy == GuestStorageDeletePolicy.NONE) {
            return "This item cannot be deleted here."
        }
        val error = when {
            entry.guestPath != null && entry.deletePolicy != GuestStorageDeletePolicy.MIGRATION ->
                deleteGuestEntry(context, entry)
            entry.hostPath != null -> deleteHostEntry(context, entry)
            else -> "Nothing to delete."
        }
        if (error == null && entry.deletePolicy == GuestStorageDeletePolicy.PROJECT && entry.hostPath != null) {
            pruneFolderRegistry(context, entry.hostPath)
        }
        return error
    }

    internal fun parseDuChildren(
        parentPath: String,
        lines: List<Pair<String, Long>>,
    ): List<Pair<String, Long>> {
        val parent = parentPath.trimEnd('/').ifEmpty { "/" }
        val prefix = if (parent == "/") "/" else "$parent/"
        return lines.filter { (path, _) ->
            val normalized = path.trimEnd('/').ifEmpty { "/" }
            if (normalized == parent) return@filter false
            if (parent == "/") {
                normalized.startsWith("/") && normalized.count { it == '/' } == 1
            } else {
                normalized.startsWith(prefix) &&
                    !normalized.removePrefix(prefix).contains('/')
            }
        }
    }

    internal fun parseDuOutput(text: String): List<Pair<String, Long>> {
        val out = mutableListOf<Pair<String, Long>>()
        for (line in text.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("__TF_")) continue
            val tab = trimmed.indexOf('\t')
            if (tab <= 0) continue
            val bytes = trimmed.substring(0, tab).trim().toLongOrNull() ?: continue
            val path = trimmed.substring(tab + 1).trim()
            if (path.isNotEmpty()) out += path to bytes
        }
        return out
    }

    /** Shallow size for small host trees only (scratch, staging, mirrors). */
    internal fun dirSizeShallow(dir: File, maxDepth: Int = 6): Long {
        if (!dir.isDirectory) return if (dir.isFile) dir.length() else 0L
        var total = 0L
        fun walk(file: File, depth: Int) {
            if (depth > maxDepth) return
            if (file.isFile) {
                total += file.length()
                return
            }
            file.listFiles()?.forEach { child -> walk(child, depth + 1) }
        }
        walk(dir, 0)
        return total
    }

    private fun hostNode(
        id: String,
        label: String,
        subtitle: String,
        dir: File,
        category: GuestStorageCategory,
        deletePolicy: GuestStorageDeletePolicy,
    ): GuestStorageNode = GuestStorageNode(
        id = id,
        label = label,
        subtitle = subtitle,
        bytes = dirSizeShallow(dir),
        category = category,
        deletePolicy = deletePolicy,
        hostPath = dir,
        expandable = false,
    )

    private fun guestPathMeta(path: String): Triple<String, GuestStorageCategory, GuestStorageDeletePolicy> =
        when {
            path == "/var/cache/apt" || path.endsWith("/apt") ->
                Triple("APT package cache", GuestStorageCategory.CACHE, GuestStorageDeletePolicy.CACHE)
            path == "/var/cache/termfold" ->
                Triple("TermFold tool caches", GuestStorageCategory.CACHE, GuestStorageDeletePolicy.CACHE)
            path == "/root/.cache" || path.endsWith("/.cache") ->
                Triple("User cache", GuestStorageCategory.CACHE, GuestStorageDeletePolicy.CACHE)
            path == "/opt/bun" -> Triple("Bun runtime", GuestStorageCategory.TOOLCHAIN, GuestStorageDeletePolicy.CACHE)
            path == "/opt/node" -> Triple("Node.js runtime", GuestStorageCategory.TOOLCHAIN, GuestStorageDeletePolicy.CACHE)
            path == "/opt/uv" -> Triple("uv Python tool", GuestStorageCategory.TOOLCHAIN, GuestStorageDeletePolicy.CACHE)
            path == "/opt/acp" -> Triple("Agent bundles (ACP)", GuestStorageCategory.TOOLCHAIN, GuestStorageDeletePolicy.CACHE)
            path == "/root" -> Triple("Home (/root)", GuestStorageCategory.SYSTEM, GuestStorageDeletePolicy.NONE)
            path == "/var" -> Triple("Variable data (/var)", GuestStorageCategory.SYSTEM, GuestStorageDeletePolicy.NONE)
            path == "/opt" -> Triple("Optional software (/opt)", GuestStorageCategory.TOOLCHAIN, GuestStorageDeletePolicy.NONE)
            path == "/usr" -> Triple("System software (/usr)", GuestStorageCategory.SYSTEM, GuestStorageDeletePolicy.NONE)
            path == "/tmp" -> Triple("Temporary files (/tmp)", GuestStorageCategory.CACHE, GuestStorageDeletePolicy.CACHE)
            path == Projects.GUEST_DIR -> Triple("All projects", GuestStorageCategory.PROJECT, GuestStorageDeletePolicy.NONE)
            else -> Triple(path.substringAfterLast('/'), GuestStorageCategory.SYSTEM, GuestStorageDeletePolicy.NONE)
        }

    private suspend fun deleteHostEntry(context: Context, entry: GuestStorageEntry): String? {
        val file = entry.hostPath ?: return "Missing path."
        val root = ShellPaths.rootDir(context).canonicalFile
        val target = file.canonicalFile
        if (!target.path.startsWith(root.path)) return "Invalid path."
        return when (entry.id) {
            "host_scratch" -> {
                clearHostScratch(context)
                null
            }
            "host_mirrors" -> {
                DeviceStorageMirrorStore.clearAll(context)
                null
            }
            else -> {
                if (!target.deleteRecursively()) "Could not delete ${target.name}."
                else null
            }
        }
    }

    private fun clearHostScratch(context: Context) {
        ShellPaths.ensureGuestScratch(context)
        listOf(
            ShellPaths.guestScratchTmp(context),
            ShellPaths.guestScratchNpmCache(context),
            ShellPaths.guestScratchNodeCompileCache(context),
            ShellPaths.shmDir(context),
        ).forEach { dir ->
            dir.listFiles()?.forEach { child -> child.deleteRecursively() }
        }
        ShellPaths.tempDir(context).listFiles()?.filter { it.name != "guest-scratch" && it.name != "shm" }
            ?.forEach { it.deleteRecursively() }
    }

    private fun deleteGuestEntry(context: Context, entry: GuestStorageEntry): String? {
        val guest = entry.guestPath ?: return "Missing path."
        if (!isDeletableGuestPath(guest, entry.deletePolicy)) return "This path cannot be deleted."
        val host = entry.hostPath ?: Projects.hostPath(context, guest)
        if (Projects.isInGuest(context, host.absolutePath)) {
            when (guest) {
                "/var/cache/apt" -> {
                    File(host, "archives").listFiles()?.forEach { it.deleteRecursively() }
                    listOf("pkgcache.bin", "srcpkgcache.bin").forEach { name ->
                        File(host, name).delete()
                    }
                }
                else -> {
                    if (host.exists() && !host.deleteRecursively()) {
                        return "Could not delete ${entry.label}."
                    }
                }
            }
            return null
        }
        val quoted = shellQuote(guest)
        val script = "rm -rf -- $quoted"
        val (code, out) = AcpInstaller.runCapture(context, script, guestCwd = "/root", timeoutSeconds = 120)
        return if (code == 0) null else out.take(400).ifBlank { "Delete failed (exit $code)." }
    }

    private fun isDeletableGuestPath(path: String, policy: GuestStorageDeletePolicy): Boolean = when (policy) {
        GuestStorageDeletePolicy.PROJECT ->
            path.startsWith("${Projects.GUEST_DIR}/") && path.removePrefix("${Projects.GUEST_DIR}/").isNotBlank() &&
                !path.removePrefix("${Projects.GUEST_DIR}/").contains('/')
        GuestStorageDeletePolicy.CACHE -> path in GUEST_CACHE_PATHS || path == "/tmp"
        else -> false
    }

    private val GUEST_CACHE_PATHS = setOf(
        "/var/cache/apt",
        "/var/cache/termfold",
        "/root/.cache",
        "/opt/bun",
        "/opt/node",
        "/opt/uv",
        "/opt/acp",
    )

    private suspend fun pruneFolderRegistry(context: Context, deletedHostDir: File) {
        val deletedPath = deletedHostDir.absolutePath.trimEnd('/')
        FolderStore(context).update { data ->
            data.copy(
                folders = data.folders.filter { folder ->
                    val p = folder.path.trimEnd('/')
                    p != deletedPath && !p.startsWith("$deletedPath/")
                },
            )
        }
    }

    private fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"
}
