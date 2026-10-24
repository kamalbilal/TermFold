package com.termfold.app.acp

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption

/**
 * Resolves where the Cursor CLI actually lives inside the guest rootfs. ACP must spawn that file
 * directly — not the `~/.local/bin/agent` shim, which is easy to lose after backup/restore.
 */
internal object CursorAgentPaths {

    fun guestPath(rootfs: File, hostFile: File): String =
        "/" + hostFile.relativeTo(rootfs).invariantSeparatorsPath

    fun locateSource(rootfs: File): File? {
        fun launcherIn(dir: File): File? =
            File(dir, "cursor-agent").takeIf { isLauncher(it) }
                ?: File(dir, "agent").takeIf { isLauncher(it) }

        fun isBundleDir(dir: File): Boolean {
            val launch = launcherIn(dir) ?: return false
            if (File(dir, "node").isFile) return true
            // SEA / single-file builds have no sibling node.
            return launch.length() > 64 * 1024
        }

        val versions = File(rootfs, "root/.local/share/cursor-agent/versions")
        if (versions.isDirectory) {
            versions.walkTopDown().maxDepth(8)
                .filter { it.isDirectory && isBundleDir(it) }
                .minByOrNull { it.relativeTo(rootfs).invariantSeparatorsPath.length }
                ?.let { dir -> launcherIn(dir)?.let { return it } }
        }

        val acpDir = File(rootfs, "opt/acp/cursor")
        for (cmd in listOf("dist-package/cursor-agent", "dist-package/agent")) {
            AcpArchives.resolveCommand(acpDir, cmd)?.takeIf { it.isFile }?.let { return it }
        }

        resolveFromAgentShim(rootfs)?.let { return it }
        return null
    }

    private fun isLauncher(file: File): Boolean {
        val path = file.toPath()
        return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) ||
            Files.isSymbolicLink(path)
    }

    private fun resolveFromAgentShim(rootfs: File): File? {
        val shim = File(rootfs, "root/.local/bin/agent")
        if (!shim.exists()) return null
        val path = shim.toPath()
        if (Files.isSymbolicLink(path)) {
            val target = Files.readSymbolicLink(path).toString()
            if (!target.startsWith("/")) return null
            return File(rootfs, target.removePrefix("/")).takeIf { it.exists() }
        }
        if (!shim.isFile || shim.length() == 0L) return null
        val text = runCatching { shim.readText() }.getOrDefault("")
        if (text.startsWith("#!/bin/sh\nexec ")) {
            val guestTarget = text.removePrefix("#!/bin/sh\nexec ").substringBefore(" \"\$@\"").trim()
            if (!guestTarget.startsWith("/")) return null
            val host = File(rootfs, guestTarget.removePrefix("/"))
            return host.takeIf { it.exists() }
        }
        return shim.takeIf { it.isFile }
    }
}
