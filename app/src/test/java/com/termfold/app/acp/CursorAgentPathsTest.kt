package com.termfold.app.acp

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class CursorAgentPathsTest {

    @Test
    fun findsCursorAgentUnderVersions() {
        val rootfs = Files.createTempDirectory("tf-cursor").toFile()
        try {
            val bundle = File(rootfs, "root/.local/share/cursor-agent/versions/2026.10.22")
            bundle.mkdirs()
            File(bundle, "node").writeText("elf")
            File(bundle, "cursor-agent").writeText("#!/bin/sh\n")

            val source = CursorAgentPaths.locateSource(rootfs)
            assertEquals(File(bundle, "cursor-agent"), source)
            assertEquals(
                "/root/.local/share/cursor-agent/versions/2026.10.22/cursor-agent",
                CursorAgentPaths.guestPath(rootfs, source!!),
            )
        } finally {
            rootfs.deleteRecursively()
        }
    }

    @Test
    fun findsLauncherViaAgentSymlink() {
        val rootfs = Files.createTempDirectory("tf-cursor-link").toFile()
        try {
            val bundle = File(rootfs, "root/.local/share/cursor-agent/versions/2026.10.22")
            bundle.mkdirs()
            File(bundle, "node").writeText("elf")
            File(bundle, "cursor-agent").writeText("#!/bin/sh\n")
            val bin = File(rootfs, "root/.local/bin")
            bin.mkdirs()
            Files.createSymbolicLink(
                File(bin, "agent").toPath(),
                File("/root/.local/share/cursor-agent/versions/2026.10.22/cursor-agent").toPath(),
            )
            val source = CursorAgentPaths.locateSource(rootfs)
            assertEquals(File(bundle, "cursor-agent"), source)
        } finally {
            rootfs.deleteRecursively()
        }
    }

    @Test
    fun followsTermfoldShimToBundle() {
        val rootfs = Files.createTempDirectory("tf-cursor-shim").toFile()
        try {
            val bundle = File(rootfs, "opt/acp/cursor/dist-package")
            bundle.mkdirs()
            File(bundle, "node").writeText("elf")
            File(bundle, "cursor-agent").writeText("#!/bin/sh\n")

            val shim = File(rootfs, "root/.local/bin/agent")
            shim.parentFile.mkdirs()
            shim.writeText("#!/bin/sh\nexec /opt/acp/cursor/dist-package/cursor-agent \"\$@\"\n")

            val source = CursorAgentPaths.locateSource(rootfs)
            assertNotNull(source)
            assertEquals(File(bundle, "cursor-agent"), source)
        } finally {
            rootfs.deleteRecursively()
        }
    }
}
