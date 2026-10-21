package com.termfold.app.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files

/** Routed extraction writes each path to its mapped destination without a single scratch root. */
class TarExtractorRoutedTest {

    @Test
    fun `routes entries to separate destinations`() {
        val rootfs = Files.createTempDirectory("tf-route-rootfs").toFile()
        val prefs = Files.createTempDirectory("tf-route-prefs").toFile()
        val manifest = Files.createTempDirectory("tf-route-manifest").toFile()
        val archive = ByteArrayOutputStream()
        TarWriter.gzip(archive) {
            addBytes("manifest.json", """{"formatVersion":1}""".toByteArray())
            addBytes("files/linux/rootfs/usr/bin/bash", "#!/bin/sh\n".toByteArray())
            addBytes("shared_prefs/acp.xml", "<map/>".toByteArray())
        }
        try {
            TarExtractor.extract(
                ByteArrayInputStream(archive.toByteArray()),
                destination = rootfs,
                gunzip = true,
                confined = true,
                confinedRoots = listOf(rootfs, prefs, manifest),
                entryTarget = { path ->
                    when {
                        path == "manifest.json" -> File(manifest, "manifest.json")
                        path.startsWith("files/linux/rootfs/") -> {
                            val rel = path.removePrefix("files/linux/rootfs/")
                            File(rootfs, rel)
                        }
                        path.startsWith("shared_prefs/") -> {
                            val rel = path.removePrefix("shared_prefs/")
                            File(prefs, rel)
                        }
                        else -> null
                    }
                },
            )
            assertTrue(File(manifest, "manifest.json").isFile)
            assertEquals("#!/bin/sh\n", File(rootfs, "usr/bin/bash").readText())
            assertEquals("<map/>", File(prefs, "acp.xml").readText())
            assertFalse(File(rootfs, "manifest.json").exists())
            assertFalse(File(rootfs, "shared_prefs").exists())
        } finally {
            rootfs.deleteRecursively()
            prefs.deleteRecursively()
            manifest.deleteRecursively()
        }
    }
}
