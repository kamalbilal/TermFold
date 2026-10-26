package com.termfold.app.prreview

import android.content.Context
import com.termfold.app.acp.AcpInstaller
import com.termfold.app.shell.Projects
import java.io.File

fun interface PrGitRunner {
    fun run(args: List<String>): Pair<Int, String>
}

object PrReviewFactsPack {
    const val DIR_NAME = ".termfold-pr-review"
    const val DIFF_FILE = "pr.diff"
    const val FILES_FILE = "files.txt"
    const val PR_FILE = "pr.md"
    const val COMMENTS_FILE = "comments.md"

    fun writeOnGuest(
        context: Context,
        guestPath: String,
        info: GitHubPrInfo,
        commentsMarkdown: String,
    ) {
        val host = Projects.hostPath(context, guestPath)
        write(
            repoRoot = host,
            info = info,
            commentsMarkdown = commentsMarkdown,
            git = PrGitRunner { args ->
                val cmd = buildString {
                    append("cd ")
                    append(shellQuote(guestPath))
                    append(" && git")
                    args.forEach { arg ->
                        append(' ')
                        append(shellQuote(arg))
                    }
                }
                AcpInstaller.runCapture(context, cmd, timeoutSeconds = 180)
            },
        )
    }

    fun write(
        repoRoot: File,
        info: GitHubPrInfo,
        commentsMarkdown: String,
        git: PrGitRunner,
    ) {
        val dir = File(repoRoot, DIR_NAME)
        dir.mkdirs()
        ensureExclude(repoRoot)
        git.run(listOf("fetch", "origin", "--prune"))
        if (info.baseRef.isNotBlank()) {
            git.run(listOf("fetch", "origin", info.baseRef))
        }
        val (mergeBase, mergeNote) = resolveMergeBase(git, info.baseRef)
        val diffText: String
        val filesText: String
        if (mergeBase == null) {
            val note = mergeNote ?: "Could not resolve merge-base."
            diffText = "# TermFold: $note\n"
            filesText = "# TermFold: $note\n"
        } else {
            val diff = git.run(listOf("diff", "$mergeBase...HEAD"))
            val names = git.run(listOf("diff", "--name-only", "$mergeBase...HEAD"))
            val rawDiff = if (diff.first == 0) diff.second else ""
            val rawNames = if (names.first == 0) names.second else ""
            if (rawDiff.isBlank()) {
                diffText = "# TermFold: git diff is empty (no changes vs merge-base $mergeBase).\n"
                filesText = if (rawNames.isBlank()) {
                    "# TermFold: no changed files vs merge-base $mergeBase.\n"
                } else {
                    rawNames.trimEnd() + "\n"
                }
            } else {
                diffText = rawDiff
                filesText = rawNames.trimEnd() + "\n"
            }
        }
        File(dir, DIFF_FILE).writeText(diffText)
        File(dir, FILES_FILE).writeText(filesText)
        File(dir, PR_FILE).writeText(prMarkdown(info))
        File(dir, COMMENTS_FILE).writeText(commentsMarkdown.ifBlank { "No GitHub comments.\n" })
    }

    internal fun resolveMergeBase(git: PrGitRunner, baseRef: String): Pair<String?, String?> {
        val candidates = buildList {
            if (baseRef.isNotBlank()) {
                add("origin/$baseRef")
                add(baseRef)
            }
            add("origin/HEAD")
            add("origin/main")
            add("origin/master")
        }.distinct()
        for (candidate in candidates) {
            val (code, out) = git.run(listOf("merge-base", candidate, "HEAD"))
            if (code == 0 && out.trim().isNotBlank()) return out.trim() to null
        }
        val against = if (baseRef.isNotBlank()) " against '$baseRef'" else ""
        return null to "Could not resolve merge-base$against."
    }

    private fun prMarkdown(info: GitHubPrInfo): String = buildString {
        appendLine("# ${info.title}")
        appendLine()
        appendLine("URL: ${info.htmlUrl}")
        appendLine("Head: ${info.headRef}")
        if (info.baseRef.isNotBlank()) appendLine("Base: ${info.baseRef}")
        appendLine()
        appendLine(info.body.ifBlank { "(no description)" })
        appendLine()
    }

    private fun ensureExclude(repoRoot: File) {
        val exclude = File(repoRoot, ".git/info/exclude")
        exclude.parentFile?.mkdirs()
        val line = "$DIR_NAME/"
        val existing = if (exclude.isFile) exclude.readText() else ""
        if (existing.lines().any { it.trim() == line }) return
        val prefix = if (existing.isNotEmpty() && !existing.endsWith("\n")) "\n" else ""
        exclude.appendText("$prefix$line\n")
    }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
