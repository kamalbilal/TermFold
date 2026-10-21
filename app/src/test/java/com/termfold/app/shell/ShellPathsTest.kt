package com.termfold.app.shell

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellPathsTest {

    @Test
    fun `ensureGuestScratch creates tmp and cache dirs under guest-scratch`() {
        val temp = Files.createTempDirectory("termfold-scratch").toFile()
        try {
            ShellPaths.ensureGuestScratch(temp)
            assertTrue(File(temp, "guest-scratch/tmp").isDirectory)
            assertTrue(File(temp, "guest-scratch/npm-cache").isDirectory)
            assertTrue(File(temp, "guest-scratch/node-compile-cache").isDirectory)
        } finally {
            temp.deleteRecursively()
        }
    }
}
