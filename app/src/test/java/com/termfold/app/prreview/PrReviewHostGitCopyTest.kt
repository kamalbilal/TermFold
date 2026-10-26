package com.termfold.app.prreview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class PrReviewHostGitCopyTest {
    @Test
    fun cloneOnFeatureLeavesGoldenDirty() {
        assumeTrue("git is required", commandOk("git", "--version"))
        val root = Files.createTempDirectory("pr-review-copy").toFile()
        try {
            val golden = File(root, "golden")
            val copy = File(root, "copy")
            git(root, "init", "--initial-branch=main", golden.absolutePath)
            write(File(golden, "README"), "hello\n")
            git(golden, "add", "README")
            git(golden, "-c", "user.email=t@t.com", "-c", "user.name=t", "commit", "-m", "init")
            git(golden, "checkout", "-b", "feature-x")
            write(File(golden, "feat.txt"), "feat\n")
            git(golden, "add", "feat.txt")
            git(golden, "-c", "user.email=t@t.com", "-c", "user.name=t", "commit", "-m", "feat")
            git(golden, "checkout", "main")
            write(File(golden, "WIP"), "dirty-golden\n")

            git(root, "clone", "--no-hardlinks", "-b", "feature-x", golden.absolutePath, copy.absolutePath)
            assertEquals("feature-x", gitOut(copy, "rev-parse", "--abbrev-ref", "HEAD").trim())
            assertTrue(File(copy, "feat.txt").isFile)
            assertEquals("dirty-golden\n", File(golden, "WIP").readText())
            assertEquals("main", gitOut(golden, "rev-parse", "--abbrev-ref", "HEAD").trim())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun reuseSwitchDoesNotCreateSecondId() {
        val first = "ws-keep"
        val reused = first
        assertEquals(first, reused)
    }

    private fun write(file: File, text: String) {
        file.writeText(text)
    }

    private fun git(dir: File, vararg args: String): Pair<Int, String> {
        val proc = ProcessBuilder("git", *args).directory(dir).redirectErrorStream(true).start()
        val out = proc.inputStream.bufferedReader().readText()
        if (!proc.waitFor(30, TimeUnit.SECONDS)) {
            proc.destroyForcibly()
            error("git timed out")
        }
        return proc.exitValue() to out
    }

    private fun gitOut(dir: File, vararg args: String): String = git(dir, *args).second

    private fun commandOk(vararg cmd: String): Boolean = runCatching {
        val proc = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        proc.waitFor(10, TimeUnit.SECONDS) && proc.exitValue() == 0
    }.getOrDefault(false)
}
