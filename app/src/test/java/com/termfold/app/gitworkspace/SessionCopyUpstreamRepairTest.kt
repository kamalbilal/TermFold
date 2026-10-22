package com.termfold.app.gitworkspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class SessionCopyUpstreamRepairTest {

    @Test
    fun actionWhenFetchFailedDoesNothing() {
        assertEquals(
            SessionCopyUpstreamRepair.Action.NONE,
            SessionCopyUpstreamRepair.action(
                originBranchExists = false,
                isCheckedOutBranch = true,
                fetchSucceeded = false,
            ),
        )
    }

    @Test
    fun actionWhenOriginMissingOnCheckedOutBranchUnsetsUpstream() {
        assertEquals(
            SessionCopyUpstreamRepair.Action.UNSET_UPSTREAM,
            SessionCopyUpstreamRepair.action(originBranchExists = false, isCheckedOutBranch = true),
        )
        assertEquals(
            SessionCopyUpstreamRepair.Action.SET_UPSTREAM_TO_ORIGIN,
            SessionCopyUpstreamRepair.action(originBranchExists = true, isCheckedOutBranch = true),
        )
        assertEquals(
            SessionCopyUpstreamRepair.Action.NONE,
            SessionCopyUpstreamRepair.action(originBranchExists = false, isCheckedOutBranch = false),
        )
    }

    @Test
    fun localGoldenOriginCanBeReplacedWithNetworkRemoteForFetch() {
        assumeTrue("git is required", commandOk("git", "--version"))
        val root = Files.createTempDirectory("termfold-origin-sync").toFile()
        try {
            val branch = "main-dev"
            val remote = File(root, "remote")
            val golden = File(root, "golden")
            val sessionCopy = File(root, "session-copy")
            git(root, "init", "--initial-branch=main", "--bare", remote.absolutePath)
            val remoteWork = File(root, "remote-work")
            git(root, "init", "--initial-branch=main", remoteWork.name)
            write(File(remoteWork, "README"), "hello\n")
            git(remoteWork, "add", "README")
            git(remoteWork, "-c", "user.email=t@t.com", "-c", "user.name=t", "commit", "-m", "init")
            git(remoteWork, "remote", "add", "origin", remote.absolutePath)
            git(remoteWork, "push", "-u", "origin", "main")
            git(root, "clone", remote.absolutePath, golden.absolutePath)
            git(golden, "checkout", "-b", branch)
            write(File(golden, "dev.txt"), "dev\n")
            git(golden, "add", "dev.txt")
            git(golden, "-c", "user.email=t@t.com", "-c", "user.name=t", "commit", "-m", "dev")
            git(golden, "push", "-u", "origin", branch)

            git(root, "clone", "--no-hardlinks", golden.absolutePath, sessionCopy.absolutePath)
            assertEquals(golden.absolutePath, gitOut(sessionCopy, "remote", "get-url", "origin").trim())

            val goldenOrigin = gitOut(golden, "remote", "get-url", "origin").trim()
            val resolved = SessionCopyOriginSync.resolveOriginUrlForSessionCopy(
                golden.absolutePath,
                goldenOrigin,
            )
            assertEquals(remote.absolutePath, resolved)
            git(sessionCopy, "remote", "set-url", "origin", resolved!!)
            git(sessionCopy, "fetch", "origin", "--prune")
            assertTrue(refExists(sessionCopy, "refs/remotes/origin/$branch"))

            val repair = SessionCopyUpstreamRepair.action(
                originBranchExists = true,
                isCheckedOutBranch = true,
            )
            git(sessionCopy, *SessionCopyUpstreamRepair.branchArgs(branch, repair)!!.split(" ").toTypedArray())
            assertTrue(hasUpstream(sessionCopy))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun cloneFromGoldenWithStaleMainDevUpstreamIsClearedAfterRepair() {
        assumeTrue("git is required", commandOk("git", "--version"))
        val root = Files.createTempDirectory("termfold-upstream-repair").toFile()
        try {
            val branch = "main-dev"
            val remote = File(root, "remote")
            val golden = File(root, "golden")
            val sessionCopy = File(root, "session-copy")
            git(root, "init", "--initial-branch=main", "--bare", remote.absolutePath)
            val remoteWork = File(root, "remote-work")
            git(root, "init", "--initial-branch=main", remoteWork.name)
            write(File(remoteWork, "README"), "hello\n")
            git(remoteWork, "add", "README")
            git(remoteWork, "-c", "user.email=t@t.com", "-c", "user.name=t", "commit", "-m", "init")
            git(remoteWork, "remote", "add", "origin", remote.absolutePath)
            git(remoteWork, "push", "-u", "origin", "main")
            git(root, "clone", remote.absolutePath, golden.absolutePath)
            git(golden, "checkout", "-b", branch)
            write(File(golden, "local.txt"), "dev\n")
            git(golden, "add", "local.txt")
            git(golden, "-c", "user.email=t@t.com", "-c", "user.name=t", "commit", "-m", "dev")
            git(golden, "branch", "--set-upstream-to=origin/$branch", branch)

            git(root, "clone", "--no-hardlinks", golden.absolutePath, sessionCopy.absolutePath)
            git(sessionCopy, "remote", "set-url", "origin", remote.absolutePath)
            assertEquals(branch, gitOut(sessionCopy, "rev-parse", "--abbrev-ref", "HEAD").trim())
            assertTrue(hasUpstream(sessionCopy))

            git(sessionCopy, "fetch", "origin", "--prune")
            assertFalse(refExists(sessionCopy, "refs/remotes/origin/$branch"))

            val repair = SessionCopyUpstreamRepair.action(
                originBranchExists = false,
                isCheckedOutBranch = true,
            )
            val args = SessionCopyUpstreamRepair.branchArgs(branch, repair)
            assertEquals("branch --unset-upstream", args)
            git(sessionCopy, "branch", "--unset-upstream")

            assertFalse(hasUpstream(sessionCopy))
            val pull = git(sessionCopy, "pull")
            assertFalse(
                pull.second.contains("no such ref was fetched", ignoreCase = true),
            )
        } finally {
            root.deleteRecursively()
        }
    }

    private fun hasUpstream(repo: File): Boolean =
        git(repo, "rev-parse", "--abbrev-ref", "@{upstream}").first == 0

    private fun refExists(repo: File, ref: String): Boolean =
        git(repo, "rev-parse", "--verify", ref).first == 0

    private fun write(file: File, text: String) {
        file.writeText(text)
    }

    private fun commandOk(vararg args: String): Boolean =
        run(File("."), args.toList(), timeoutSec = 15).first == 0

    private fun git(cwd: File, vararg args: String): Pair<Int, String> =
        run(cwd, listOf("git") + args, timeoutSec = 120)

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
