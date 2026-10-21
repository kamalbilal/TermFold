package com.termfold.app.shell

import android.content.Context
import java.io.File

/** Locates the pieces of the bundled Linux environment. */
object ShellPaths {

    /** Everything the environment owns lives under this app-private directory. */
    fun rootDir(context: Context): File = File(context.filesDir, ShellConfig.ROOT_DIR)

    /** The extracted Debian root filesystem. */
    fun rootfsDir(context: Context): File = File(rootDir(context), ShellConfig.ROOTFS_DIR)

    /** PRoot's scratch space. Must exist before PRoot starts or forked children can die. */
    fun tempDir(context: Context): File =
        File(context.filesDir, ShellConfig.TEMP_DIR).apply { mkdirs() }

    /** Host tree for guest `/tmp` and tool caches (bind-mounted into Debian). */
    fun guestScratchDir(context: Context): File = guestScratchDir(tempDir(context))

    fun guestScratchDir(tempRoot: File): File = File(tempRoot, "guest-scratch")

    /** Guest `/tmp` after bind mount. */
    fun guestScratchTmp(context: Context): File =
        guestScratchTmp(tempDir(context)).apply { mkdirs() }

    fun guestScratchTmp(tempRoot: File): File = File(guestScratchDir(tempRoot), "tmp")

    /** Host side of guest `npm_config_cache`. */
    fun guestScratchNpmCache(context: Context): File =
        guestScratchNpmCache(tempDir(context)).apply { mkdirs() }

    fun guestScratchNpmCache(tempRoot: File): File = File(guestScratchDir(tempRoot), "npm-cache")

    /** Host side of guest `NODE_COMPILE_CACHE`. */
    fun guestScratchNodeCompileCache(context: Context): File =
        guestScratchNodeCompileCache(tempDir(context)).apply { mkdirs() }

    fun guestScratchNodeCompileCache(tempRoot: File): File =
        File(guestScratchDir(tempRoot), "node-compile-cache")

    /** Guest paths that [ProotCommand] bind-mounts over. */
    const val GUEST_TMP = "/tmp"
    const val GUEST_NPM_CACHE = "/var/cache/termfold/npm"
    const val GUEST_NODE_COMPILE_CACHE = "/var/cache/termfold/node-compile-cache"

    /** Creates host scratch dirs used for guest `/tmp` and Node/npm caches. */
    fun ensureGuestScratch(context: Context) {
        ensureGuestScratch(tempDir(context))
    }

    fun ensureGuestScratch(tempRoot: File) {
        guestScratchTmp(tempRoot).mkdirs()
        guestScratchNpmCache(tempRoot).mkdirs()
        guestScratchNodeCompileCache(tempRoot).mkdirs()
    }

    /**
     * PRoot needs a writable `/dev/shm`, and Android's own is owned by the system. Debian's apt
     * refuses to run without one, so this directory is bind-mounted over `/dev/shm` in the guest.
     */
    fun shmDir(context: Context): File = File(tempDir(context), "shm").apply { mkdirs() }

    /** The resolver configuration bind-mounted over the guest's `/etc/resolv.conf`. */
    fun resolvConf(context: Context): File = File(tempDir(context), "resolv.conf")

    /** The hosts table bind-mounted over the guest's `/etc/hosts`. */
    fun hostsFile(context: Context): File = File(tempDir(context), "hosts")

    /** The scratch tree a new rootfs is unpacked into before it takes over. */
    fun stagingDir(context: Context): File = File(rootDir(context), ShellConfig.ROOTFS_STAGING_DIR)

    /** The Ubuntu rootfs that is being migrated away from (see [DistroMigration]). */
    fun oldRootfsDir(context: Context): File = File(rootDir(context), ShellConfig.ROOTFS_OLD_DIR)

    /** Marks a completed extraction, so provisioning runs at most once. */
    fun rootfsMarker(context: Context): File = File(rootfsDir(context), ".termfold-rootfs")

    /** True once the rootfs is unpacked; the tools themselves always live in nativeLibraryDir. */
    fun isReady(context: Context): Boolean =
        rootfsMarker(context).isFile && File(rootfsDir(context), "usr/bin/bash").isFile
}
