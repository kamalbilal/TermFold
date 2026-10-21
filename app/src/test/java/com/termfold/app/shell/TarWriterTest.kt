package com.termfold.app.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files

class TarWriterTest {

    @Test
    fun `round trips files directories symlinks and long names`() {
        val source = Files.createTempDirectory("tf-tar-out").toFile()
        val restored = Files.createTempDirectory("tf-tar-in").toFile()
        try {
            File(source, "dir").mkdirs()
            File(source, "dir/hello.txt").writeText("hello")
            File(source, "dir/run.sh").apply {
                writeText("#!/bin/sh\necho ok\n")
                setExecutable(true)
            }
            val link = File(source, "dir/link")
            Files.createSymbolicLink(link.toPath(), File(source, "dir/hello.txt").toPath())

            val longDir = File(source, "dir/" + "a".repeat(90))
            longDir.mkdirs()
            File(longDir, "pkg.list").writeText("libc6:arm64")

            val archive = ByteArrayOutputStream()
            TarWriter.gzip(archive) {
                walkTree("tree", source)
            }

            TarExtractor.extract(
                ByteArrayInputStream(archive.toByteArray()),
                restored,
                gunzip = true,
                confined = true,
            )

            assertEquals("hello", File(restored, "tree/dir/hello.txt").readText())
            assertTrue(File(restored, "tree/dir/run.sh").canExecute())
            assertTrue(Files.isSymbolicLink(File(restored, "tree/dir/link").toPath()))
            assertEquals(
                "libc6:arm64",
                File(restored, "tree/dir/${"a".repeat(90)}/pkg.list").readText(),
            )
        } finally {
            source.deleteRecursively()
            restored.deleteRecursively()
        }
    }
}
