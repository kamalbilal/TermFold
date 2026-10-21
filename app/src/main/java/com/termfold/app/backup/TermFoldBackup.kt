package com.termfold.app.backup



import android.content.Context

import android.os.Build

import android.util.Log

import com.termfold.app.BuildConfig

import com.termfold.app.acp.AcpSessions

import com.termfold.app.shell.DistroMigration

import com.termfold.app.shell.ShellPaths

import com.termfold.app.shell.ShellRuntime

import com.termfold.app.shell.TarExtractor

import com.termfold.app.shell.TarWriter

import com.termfold.app.shell.TerminalHost

import org.json.JSONObject

import java.io.File

import java.io.IOException

import java.io.InputStream

import java.io.OutputStream

import java.nio.file.FileVisitResult

import java.nio.file.Files

import java.nio.file.Path

import java.nio.file.SimpleFileVisitor

import java.nio.file.StandardCopyOption

import java.nio.file.attribute.BasicFileAttributes



/**

 * Full-device backup for TermFold as one gzip-compressed tar file (`.termfold`).

 *

 * The archive mirrors paths under the app's private data directory: the Debian rootfs, project

 * metadata, agent registry, drafts, and shared preferences. Scratch trees (`linux/tmp`, staging)

 * are omitted because they are recreated on launch.

 */

object TermFoldBackup {



    private const val TAG = "TermFoldBackup"

    const val FORMAT_VERSION = 1

    const val FILE_EXTENSION = "termfold"

    private const val MANIFEST = "manifest.json"

    private const val ROOTFS_PREFIX = "files/linux/rootfs"

    private const val RESTORE_MANIFEST = ".restore-manifest.json"

    private const val RESTORE_OLD_ROOTFS = "files/linux/rootfs-restore-old"



    /**
     * Paths under the Debian rootfs that must not be archived.
     *
     * `etc/hosts` and `etc/resolv.conf` are bind-mounted from the host at runtime and are often
     * not readable on disk after PRoot has run. [ShellRuntime.writeHostFiles] recreates them.
     * The other top-level names are mount stubs PRoot may leave without read permission.
     */
    private val ROOTFS_SKIP_TOP = setOf("proc", "sys", "dev", "storage", "sdcard", "mnt")

    private val ROOTFS_SKIP_FILES = setOf("etc/hosts", "etc/resolv.conf")



    /** Top-level names under `files/` that are never backed up (scratch or swap trees). */

    private val SKIP_FILES_CHILDREN = setOf(

        ".restore-staging",

    )



    data class Progress(val step: String, val fraction: Float)



    class BackupException(message: String) : IOException(message)



    fun suggestedFileName(): String {

        val stamp = java.text.SimpleDateFormat("yyyy-MM-dd-HHmm", java.util.Locale.US)

            .format(java.util.Date())

        return "TermFold-backup-$stamp.$FILE_EXTENSION"

    }



    /** Rough size for the pre-flight dialog. Counts the same trees [export] archives. */

    fun measureBytes(context: Context): Long {

        var total = 0L

        val dataDir = context.applicationInfo.dataDir

        for (relative in treesToBackup(context)) {

            val root = File(dataDir, relative)

            if (!root.exists()) continue

            total += measureTree(root)

        }

        return total

    }



    fun export(context: Context, output: OutputStream, onProgress: (Progress) -> Unit = {}) {

        synchronized(ShellRuntime) {

            stopGuestProcesses()

            if (!ShellRuntime.isReady(context)) {

                throw BackupException("Linux is not set up yet. Finish setup before backing up.")

            }

            val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()

            val marker = runCatching { ShellPaths.rootfsMarker(context).readText().trim() }.getOrDefault("")

            val manifest = JSONObject()

                .put("formatVersion", FORMAT_VERSION)

                .put("createdAt", System.currentTimeMillis())

                .put("appVersion", BuildConfig.VERSION_NAME)

                .put("abi", abi)

                .put("rootfsMarker", marker)



            var entries = 0

            val trees = treesToBackup(context)

            TarWriter.gzip(output) {

                addBytes(MANIFEST, manifest.toString().toByteArray())

                entries++

                onProgress(Progress("Preparing archive", 0.02f))

                val totalTrees = trees.size.coerceAtLeast(1)

                trees.forEachIndexed { index, relative ->

                    val root = File(context.applicationInfo.dataDir, relative)

                    val base = index.toFloat() / totalTrees

                    val span = 1f / totalTrees

                    onProgress(Progress("Archiving $relative", base))

                    val skipRootfs = relative == "files/linux/rootfs"

                    walkTree(

                        prefix = relative,

                        root = root,

                        onEntry = {

                            entries++

                            if (entries % 200 == 0) {

                                onProgress(Progress("Archiving $relative", base + span * 0.9f))

                            }

                        },

                        skipRelative = if (skipRootfs) ::shouldSkipRootfsEntry else { _ -> false },

                    )

                }

                onProgress(Progress("Finishing", 0.98f))

            }

            onProgress(Progress("Done", 1f))

            Log.i(TAG, "Backup wrote $entries tar entries")

        }

    }



    fun restore(context: Context, input: InputStream, onProgress: (Progress) -> Unit = {}) {

        synchronized(ShellRuntime) {

            val dataDir = File(context.applicationInfo.dataDir)

            val liveRootfs = ShellPaths.rootfsDir(context)

            val rootfsStaging = ShellPaths.stagingDir(context)

            val oldRootfs = File(dataDir, RESTORE_OLD_ROOTFS)

            val manifestScratch = File(context.cacheDir, RESTORE_MANIFEST)



            DistroMigration.deleteTree(rootfsStaging)

            DistroMigration.deleteTree(oldRootfs)

            manifestScratch.delete()



            var manifestChecked = false

            var rootfsSwapped = false



            fun ensureManifest() {

                if (manifestChecked) return

                if (!manifestScratch.isFile) {

                    throw BackupException("This file is not a TermFold backup (missing manifest).")

                }

                val manifest = JSONObject(manifestScratch.readText())

                val version = manifest.optInt("formatVersion", 0)

                if (version != FORMAT_VERSION) {

                    throw BackupException("Unsupported backup format (version $version).")

                }

                val backupAbi = manifest.optString("abi")

                val deviceAbi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()

                if (backupAbi.isNotBlank() && backupAbi != deviceAbi) {

                    throw BackupException(

                        "This backup is for $backupAbi but this device is $deviceAbi. " +

                            "Restore on the same kind of CPU (arm64 vs x86_64).",

                    )

                }

                manifestChecked = true

                manifestScratch.delete()

            }



            fun swapRootfs() {

                if (rootfsSwapped) return

                ensureManifest()

                if (!File(rootfsStaging, "usr/bin/bash").isFile) {

                    throw BackupException("Backup does not contain a usable Linux system.")

                }

                val peakBytes = measureTree(rootfsStaging) + if (liveRootfs.exists()) measureTree(liveRootfs) else 0L

                val free = DistroMigration.freeBytes(context)

                if (free < peakBytes) {

                    throw BackupException(

                        "Not enough free space to swap Linux. Need about ${peakBytes / (1024 * 1024)} MB free " +

                            "at that step, have ${free / (1024 * 1024)} MB.",

                    )

                }

                onProgress(Progress("Stopping sessions", 0.12f))

                stopGuestProcesses()

                onProgress(Progress("Restoring Linux", 0.20f))

                if (liveRootfs.exists()) {

                    check(liveRootfs.renameTo(oldRootfs)) {

                        "Could not move the current Linux tree aside."

                    }

                }

                check(rootfsStaging.renameTo(liveRootfs)) {

                    "Could not put the restored Linux tree in place."

                }

                rootfsSwapped = true

            }



            fun rollbackRootfs() {

                if (!rootfsSwapped || !oldRootfs.exists()) return

                if (liveRootfs.exists()) {

                    DistroMigration.deleteTree(liveRootfs)

                }

                val rolled = oldRootfs.renameTo(liveRootfs)

                if (rolled) {

                    rootfsSwapped = false

                    Log.w(TAG, "Rolled back the previous Linux tree after a failed restore")

                } else {

                    Log.e(TAG, "Restore failed and the previous Linux tree could not be restored")

                }

            }



            val confinedRoots = listOf(dataDir, context.cacheDir)



            try {

                onProgress(Progress("Reading backup", 0.05f))

                TarExtractor.extract(

                    archive = input,

                    destination = dataDir,

                    gunzip = true,

                    confined = true,

                    confinedRoots = confinedRoots,

                    entryTarget = { archivePath ->

                        val path = normalizeArchivePath(archivePath)

                        when {

                            path == MANIFEST -> File(manifestScratch.path)

                            path == ROOTFS_PREFIX || path.startsWith("$ROOTFS_PREFIX/") -> {

                                ensureManifest()

                                val rel = when (path) {

                                    ROOTFS_PREFIX -> ""

                                    else -> path.removePrefix("$ROOTFS_PREFIX/")

                                }

                                if (rel.isEmpty()) rootfsStaging else File(rootfsStaging, rel)

                            }

                            path.startsWith("files/") -> {

                                swapRootfs()

                                File(dataDir, path)

                            }

                            path.startsWith("shared_prefs/") -> {

                                swapRootfs()

                                File(dataDir, path)

                            }

                            else -> null

                        }

                    },

                )



                swapRootfs()



                onProgress(Progress("Refreshing Linux", 0.85f))

                ShellRuntime.reconcileRestoredRootfs(context, liveRootfs)

                com.termfold.app.shell.GuestCompat.migrateFakeLinks(liveRootfs)

                com.termfold.app.shell.GuestCompat.removeStaleMountPoints(liveRootfs)



                DistroMigration.deleteTree(oldRootfs)

                onProgress(Progress("Done", 1f))

                Log.i(TAG, "Restore complete")

            } catch (error: Throwable) {

                rollbackRootfs()

                if (!rootfsSwapped) {

                    DistroMigration.deleteTree(rootfsStaging)

                }

                throw error

            } finally {

                manifestScratch.delete()

            }

        }

    }



    private fun normalizeArchivePath(name: String): String =

        name.removePrefix("./").trimStart('/').replace('\\', '/')



    private fun shouldSkipRootfsEntry(relative: String): Boolean {

        if (relative in ROOTFS_SKIP_FILES) return true

        val top = relative.substringBefore('/')

        return top in ROOTFS_SKIP_TOP

    }



    private fun treesToBackup(context: Context): List<String> {

        val dataDir = File(context.applicationInfo.dataDir)

        val result = mutableListOf<String>()

        val filesLinux = File(dataDir, "files/linux/rootfs")

        if (filesLinux.isDirectory) result += "files/linux/rootfs"

        val filesRoot = File(dataDir, "files")

        filesRoot.listFiles()?.forEach { entry ->

            if (entry.name == "linux" || entry.name in SKIP_FILES_CHILDREN) return@forEach

            val rel = "files/${entry.name}"

            if (entry.isDirectory || entry.isFile) result += rel

        }

        val prefs = File(dataDir, "shared_prefs")

        if (prefs.isDirectory) result += "shared_prefs"

        return result

    }



    private fun measureTree(root: File): Long {

        if (!root.isDirectory) return 0L

        var total = 0L

        Files.walkFileTree(

            root.toPath(),

            object : SimpleFileVisitor<Path>() {

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {

                    if (attrs.isRegularFile) total += attrs.size()

                    return FileVisitResult.CONTINUE

                }



                override fun visitFileFailed(file: Path, exc: java.io.IOException): FileVisitResult =

                    FileVisitResult.CONTINUE

            },

        )

        return total

    }



    private fun stopGuestProcesses() {

        runCatching { AcpSessions.all().keys.forEach { AcpSessions.close(it) } }

        runCatching { TerminalHost.liveSessions().forEach { TerminalHost.closeSession(it.key) } }

        Thread.sleep(600)

    }

}


