package com.termfold.app.prreview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class PrReviewFactsPackTest {
    @Test
    fun writesPackAndKeepsGitStatusClean() {
        assumeTrue("git is required", commandOk("git", "--version"))
        val root = Files.createTempDirectory("pr-facts").toFile()
        try {
            val repo = initRepoWithFeature(root)
            val info = GitHubPrInfo(
                title = "Add feat",
                headRef = "feature",
                htmlUrl = "https://github.com/o/r/pull/1",
                body = "Does a thing.",
                baseRef = "main",
            )
            PrReviewFactsPack.write(repo, info, "No GitHub comments.\n", hostGit(repo))
            val dir = File(repo, PrReviewFactsPack.DIR_NAME)
            assertTrue(File(dir, "pr.diff").readText().contains("feat.txt"))
            assertTrue(File(dir, "files.txt").readText().contains("feat.txt"))
            assertTrue(File(dir, "pr.md").readText().contains("Add feat"))
            assertTrue(File(dir, "pr.md").readText().contains("Does a thing."))
            assertTrue(File(dir, "comments.md").readText().contains("No GitHub comments"))
            val status = gitOut(repo, "status", "--porcelain")
            assertEquals("", status.trim())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun secondWriteReplacesPackForNewPr() {
        assumeTrue("git is required", commandOk("git", "--version"))
        val root = Files.createTempDirectory("pr-facts-refresh").toFile()
        try {
            val repo = initRepoWithFeature(root)
            val first = GitHubPrInfo("One", "feature", "https://github.com/o/r/pull/1", "first", "main")
            val second = GitHubPrInfo("Two", "feature", "https://github.com/o/r/pull/2", "second", "main")
            val git = hostGit(repo)
            PrReviewFactsPack.write(repo, first, "old comments\n", git)
            PrReviewFactsPack.write(repo, second, "new comments\n", git)
            val prMd = File(repo, "${PrReviewFactsPack.DIR_NAME}/pr.md").readText()
            assertTrue(prMd.contains("Two"))
            assertFalse(prMd.contains("first"))
            assertEquals("new comments\n", File(repo, "${PrReviewFactsPack.DIR_NAME}/comments.md").readText())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun emptyDiffWritesExplicitNote() {
        assumeTrue("git is required", commandOk("git", "--version"))
        val root = Files.createTempDirectory("pr-facts-empty").toFile()
        try {
            val repo = File(root, "repo")
            git(root, "init", "--initial-branch=main", repo.absolutePath)
            write(File(repo, "README"), "hello\n")
            git(repo, "add", "README")
            git(repo, "-c", "user.email=t@t.com", "-c", "user.name=t", "commit", "-m", "init")
            val info = GitHubPrInfo("Empty", "main", "https://github.com/o/r/pull/3", "", "main")
            PrReviewFactsPack.write(repo, info, "", hostGit(repo))
            val diff = File(repo, "${PrReviewFactsPack.DIR_NAME}/pr.diff").readText()
            assertTrue(diff.contains("empty"))
            assertTrue(File(repo, "${PrReviewFactsPack.DIR_NAME}/files.txt").readText().contains("no changed files"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun mergeBaseFailureStillWritesNote() {
        val repo = Files.createTempDirectory("pr-facts-nomerge").toFile()
        try {
            File(repo, ".git/info").mkdirs()
            val git = PrGitRunner { _ -> 1 to "fatal: not a git repository" }
            val info = GitHubPrInfo("X", "feat", "https://github.com/o/r/pull/4", "", "main")
            PrReviewFactsPack.write(repo, info, "c\n", git)
            val diff = File(repo, "${PrReviewFactsPack.DIR_NAME}/pr.diff").readText()
            assertTrue(diff.contains("Could not resolve merge-base"))
            assertTrue(diff.contains("main"))
            assertTrue(File(repo, "${PrReviewFactsPack.DIR_NAME}/files.txt").readText().contains("Could not resolve merge-base"))
            assertTrue(File(repo, "${PrReviewFactsPack.DIR_NAME}/pr.md").isFile)
        } finally {
            repo.deleteRecursively()
        }
    }

    private fun initRepoWithFeature(root: File): File {
        val repo = File(root, "repo")
        git(root, "init", "--initial-branch=main", repo.absolutePath)
        write(File(repo, "README"), "hello\n")
        git(repo, "add", "README")
        git(repo, "-c", "user.email=t@t.com", "-c", "user.name=t", "commit", "-m", "init")
        git(repo, "checkout", "-b", "feature")
        write(File(repo, "feat.txt"), "feat\n")
        git(repo, "add", "feat.txt")
        git(repo, "-c", "user.email=t@t.com", "-c", "user.name=t", "commit", "-m", "feat")
        return repo
    }

    private fun hostGit(dir: File) = PrGitRunner { args -> git(dir, *args.toTypedArray()) }

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
