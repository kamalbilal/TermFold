package com.termfold.app.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import com.termfold.app.shell.TarExtractor
import com.termfold.app.shell.TarWriter

class TermFoldBackupRootfsSkipTest {

    @Test
    fun `backup walk skips host bind mount paths`() {
        val rootfs = Files.createTempDirectory("tf-rootfs-skip").toFile()
        try {
            File(rootfs, "etc").mkdirs()
            File(rootfs, "etc/hosts").writeText("127.0.0.1 localhost\n")
            File(rootfs, "etc/resolv.conf").writeText("nameserver 8.8.8.8\n")
            File(rootfs, "usr/bin").mkdirs()
            File(rootfs, "usr/bin/bash").writeText("#!/bin/sh\n")

            val archive = ByteArrayOutputStream()
            TarWriter.gzip(archive) {
                walkTree(
                    prefix = "files/linux/rootfs",
                    root = rootfs,
                    skipRelative = { rel ->
                        rel in setOf("etc/hosts", "etc/resolv.conf") ||
                            rel.substringBefore('/') in setOf("proc", "sys", "dev")
                    },
                )
            }

            val listing = mutableListOf<String>()
            TarExtractor.extract(
                ByteArrayInputStream(archive.toByteArray()),
                rootfs,
                gunzip = true,
                confined = true,
                entryTarget = { path ->
                    listing += path
                    File(rootfs, "extracted-$path")
                },
            )
            assertTrue(listing.any { it.endsWith("usr/bin/bash") })
            assertFalse(listing.any { it.endsWith("etc/hosts") })
            assertFalse(listing.any { it.endsWith("etc/resolv.conf") })
        } finally {
            rootfs.deleteRecursively()
        }
    }
}
