package com.termfold.app.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import org.json.JSONObject
import com.termfold.app.shell.TarExtractor
import com.termfold.app.shell.TarWriter

/** Ensures a backup-shaped archive carries a readable manifest at the tar root. */
class TermFoldBackupManifestTest {

    @Test
    fun `manifest round trips at archive root`() {
        val manifest = JSONObject()
            .put("formatVersion", TermFoldBackup.FORMAT_VERSION)
            .put("abi", "arm64-v8a")
            .put("appVersion", "2.3")
        val archive = ByteArrayOutputStream()
        TarWriter.gzip(archive) {
            addBytes("manifest.json", manifest.toString().toByteArray())
            addBytes("files/datastore/ping.txt", "ok".toByteArray())
        }
        val dir = Files.createTempDirectory("tf-manifest").toFile()
        try {
            TarExtractor.extract(
                ByteArrayInputStream(archive.toByteArray()),
                dir,
                gunzip = true,
                confined = true,
            )
            val read = JSONObject(File(dir, "manifest.json").readText())
            assertEquals(TermFoldBackup.FORMAT_VERSION, read.getInt("formatVersion"))
            assertEquals("arm64-v8a", read.getString("abi"))
            assertTrue(File(dir, "files/datastore/ping.txt").readText() == "ok")
        } finally {
            dir.deleteRecursively()
        }
    }
}
