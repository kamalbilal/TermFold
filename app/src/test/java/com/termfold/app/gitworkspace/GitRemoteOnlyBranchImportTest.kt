package com.termfold.app.gitworkspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class GitRemoteOnlyBranchImportTest {
    @Test
    fun cloneOfGoldenThenImportFetchMakesRemoteOnlyBranchSwitchable() {
        assumeTrue("git is required for this repro", commandOk("git", "--version"))
        val root = Files.createTempDirectory("termfold-remote-only").toFile()
        try {
            val branch = "Fixing-Ahmad--From-Trajector-ahmad-changing-new-changes"
            val upstream = File(root, "upstream")
            val golden = File(root, "golden")
            val dest = File(root, "dest")
            git(root, "init", "--initial-branch=main", "upstream")
            write(File(upstream, "README"), "hello\n")
            git(upstream, "add", "README")
            git(upstream, "-c", "user.email=t@t.com", "-c", "user.name=t", "commit", "-m", "init")
            git(upstream, "checkout", "-b", branch)
            write(File(upstream, "ahmad.txt"), "ahmad\n")
            git(upstream, "add", "ahmad.txt")
            git(upstream, "-c", "user.email=t@t.com", "-c", "user.name=t", "commit", "-m", "ahmad")
            git(upstream, "checkout", "main")
            git(root, "clone", upstream.absolutePath, golden.absolutePath)
            git(root, "clone", "--no-hardlinks", golden.absolutePath, dest.absolutePath)

            assertFalse(refExists(dest, "refs/heads/$branch"))
            assertFalse(refExists(dest, "refs/remotes/origin/$branch"))
            assertTrue(BranchNames.switchArgs(branch, hasLocalHead = false, hasOriginRemote = false).isEmpty())

            val fetch = BranchNames.importBranchFetchCommands(branch).first()
            val fetchParts = fetch.split(" ")
            assertEquals("fetch", fetchParts[0])
            git(dest, *gitArgsFromCommand(fetch).toTypedArray())

            assertTrue(refExists(dest, "refs/remotes/origin/$branch"))
            val attempts = BranchNames.switchArgs(branch, hasLocalHead = false, hasOriginRemote = true)
            assertTrue(attempts.isNotEmpty())
            git(dest, *gitArgsFromCommand(attempts.first()).toTypedArray())
            assertEquals(branch, gitOut(dest, "rev-parse", "--abbrev-ref", "HEAD").trim())
            assertTrue(File(dest, "ahmad.txt").isFile)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun gitArgsFromCommand(command: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var inQuote = false
        for (ch in command) {
            when {
                ch == '\'' -> inQuote = !inQuote
                ch == ' ' && !inQuote -> {
                    if (current.isNotEmpty()) {
                        tokens.add(current.toString())
                        current.clear()
                    }
                }
                else -> current.append(ch)
            }
        }
        if (current.isNotEmpty()) tokens.add(current.toString())
        return tokens
    }

    private fun refExists(repo: File, ref: String): Boolean =
        git(repo, "rev-parse", "--verify", ref).first == 0

    private fun write(file: File, text: String) {
        file.writeText(text)
    }

    private fun commandOk(vararg args: String): Boolean =
        run(File("."), args.toList(), timeoutSec = 15).first == 0

    private fun git(cwd: File, vararg args: String): Pair<Int, String> =
        run(cwd, listOf("git") + args, timeoutSec = 60)

    private fun gitOut(cwd: File, vararg args: String): String = git(cwd, *args).second

    private fun run(cwd: File, args: List<String>, timeoutSec: Long): Pair<Int, String> {
        val proc = ProcessBuilder(args)
            .directory(cwd)
            .redirectErrorStream(true)
            .start()
        val out = proc.inputStream.bufferedReader().readText()
        val finished = proc.waitFor(timeoutSec, TimeUnit.SECONDS)
        if (!finished) {
            proc.destroyForcibly()
            return 1 to "timeout: ${args.joinToString(" ")}"
        }
        return proc.exitValue() to out
    }
}
