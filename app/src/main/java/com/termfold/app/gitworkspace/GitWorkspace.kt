package com.termfold.app.gitworkspace

import android.content.Context

import com.termfold.app.acp.AcpInstaller

import com.termfold.app.shell.Projects

import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.ensureActive

import kotlinx.coroutines.withContext

import java.io.File

import kotlin.coroutines.coroutineContext

enum class CheckoutMode {

    FAIL_IF_DIRTY,

    STASH_THEN_SWITCH,

    FORCE,

}

object GitWorkspace {

    data class ProvisionProgress(val copiedBytes: Long, val totalBytes: Long) {

        val percent: Int

            get() = if (totalBytes <= 0L) 0 else ((copiedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)

    }

    suspend fun currentBranch(context: Context, guestPath: String): String? = withContext(Dispatchers.IO) {

        val (code, out) = runGit(context, guestPath, "rev-parse --abbrev-ref HEAD")

        if (code != 0) return@withContext null

        out.trim().takeIf { it.isNotBlank() && it != "HEAD" }

    }

    /** Reads the current branch from the host `.git/HEAD` file (no PRoot). */
    fun readBranchFromHost(context: Context, guestPath: String): String? {
        val headFile = Projects.hostPath(context, guestPath).resolve(".git/HEAD")
        if (!headFile.isFile) return null
        return runCatching { parseGitHeadContent(headFile.readText()) }.getOrNull()
    }

    internal fun parseGitHeadContent(content: String): String? {
        val line = content.lineSequence().firstOrNull()?.trim() ?: return null
        if (line.startsWith("ref: refs/heads/")) {
            return line.removePrefix("ref: refs/heads/").trim().takeIf { it.isNotBlank() }
        }
        return line.take(7).takeIf { it.length >= 7 }
    }

    internal fun shouldFetchOriginBeforeSwitch(hasLocal: Boolean): Boolean = !hasLocal

    suspend fun isDirty(context: Context, guestPath: String): Boolean = withContext(Dispatchers.IO) {

        val (code, out) = runGit(context, guestPath, "status --porcelain")

        code == 0 && out.isNotBlank()

    }

    suspend fun isGitRepository(context: Context, guestPath: String): Boolean = withContext(Dispatchers.IO) {
        val (code, out) = runGit(context, guestPath, "rev-parse --is-inside-work-tree")
        code == 0 && out.trim().equals("true", ignoreCase = true)
    }

    suspend fun loadChangeSummary(context: Context, guestPath: String): GitChangeSummary? = withContext(Dispatchers.IO) {
        val (repoCode, repoOut) = runGit(context, guestPath, "rev-parse --is-inside-work-tree")
        if (repoCode != 0 || !repoOut.trim().equals("true", ignoreCase = true)) return@withContext null
        val (branchCode, branchOut) = runGit(context, guestPath, "rev-parse --abbrev-ref HEAD")
        if (branchCode != 0) return@withContext null
        val branch = branchOut.trim()
        val (upCode, upOut) = runGit(context, guestPath, "rev-parse --abbrev-ref @{upstream}")
        val upstream = if (upCode == 0) upOut.trim().takeIf { it.isNotBlank() } else null
        val outgoing = upstream?.let {
            val (c, o) = runGit(context, guestPath, "rev-list --count @{upstream}..HEAD")
            if (c == 0) o.trim().toIntOrNull() else null
        }
        val incoming = upstream?.let {
            val (c, o) = runGit(context, guestPath, "rev-list --count HEAD..@{upstream}")
            if (c == 0) o.trim().toIntOrNull() else null
        }
        val (_, porcelain) = runGit(context, guestPath, "status --porcelain")
        val (_, stagedNum) = runGit(context, guestPath, "diff --cached --numstat")
        val (_, unstagedNum) = runGit(context, guestPath, "diff --numstat")
        val (stagedEntries, unstagedEntries) = parsePorcelain(porcelain.lines())
        val stagedFiles = buildChangeFiles(stagedEntries, staged = true, parseNumstat(stagedNum.lines()))
        val unstagedFiles = buildChangeFiles(unstagedEntries, staged = false, parseNumstat(unstagedNum.lines()))
        GitChangeSummary(
            branch = branch,
            upstream = upstream,
            outgoingCommits = outgoing,
            incomingCommits = incoming,
            staged = stagedFiles,
            unstaged = unstagedFiles,
        )
    }

    suspend fun loadUnifiedDiff(
        context: Context,
        guestPath: String,
        path: String,
        staged: Boolean,
        untracked: Boolean,
    ): String = withContext(Dispatchers.IO) {
        val quoted = shellQuote(path)
        val args = when {
            staged -> "diff -U3 --cached -- $quoted"
            untracked -> "diff --no-index -U3 /dev/null $quoted"
            else -> "diff -U3 -- $quoted"
        }
        val (code, out) = runGit(context, guestPath, args)
        if (code != 0 && out.isBlank()) return@withContext ""
        val capped = if (out.length > MAX_DIFF_CHARS) {
            out.take(MAX_DIFF_CHARS) + "\n\n… diff truncated …"
        } else {
            out
        }
        capped
    }

    private const val MAX_DIFF_CHARS = 512 * 1024

    suspend fun listBranches(context: Context, guestPath: String): List<String> = withContext(Dispatchers.IO) {

        val (code, out) = runGit(

            context,

            guestPath,

            "for-each-ref --format='%(refname:short)' refs/heads refs/remotes",

        )

        if (code == 0 && out.isNotBlank()) {
            return@withContext parseBranchLines(out.lines())
        }

        val fallback = runGit(context, guestPath, "branch -a --format='%(refname:short)'")
        if (fallback.first == 0 && fallback.second.isNotBlank()) {
            return@withContext parseBranchLines(fallback.second.lines())
        }

        val legacy = runGit(context, guestPath, "branch -a")
        if (legacy.first != 0) return@withContext emptyList()
        return@withContext BranchNames.normalizeForPicker(
            legacy.second.lines()
                .map { it.trim().removePrefix("* ").trim() }
                .filter { it.isNotBlank() && !it.contains("->") }
                .map { it.removePrefix("remotes/") },
        )

    }

    private fun parseBranchLines(lines: List<String>): List<String> =
        BranchNames.normalizeForPicker(lines)

    /**
     * Branch names for the picker on a session copy: golden catalog minus branches that do not exist
     * on the golden repo (so checkout import cannot reach them).
     */
    suspend fun listBranchesForPicker(
        context: Context,
        checkoutGuestPath: String,
        catalogGuestPath: String?,
    ): List<String> = withContext(Dispatchers.IO) {
        val catalog = catalogGuestPath?.takeIf { it.isNotBlank() && it != checkoutGuestPath }
        if (catalog == null) return@withContext listBranches(context, checkoutGuestPath)
        val golden = listBranches(context, catalog)
        val onCopy = listBranches(context, checkoutGuestPath).toSet()
        golden.filter { name -> name in onCopy || branchRefExists(context, catalog, name) }
    }

    private fun branchRefExists(context: Context, guestPath: String, localName: String): Boolean {
        val n = BranchNames.canonicalLocalName(localName)
        if (n.isBlank()) return false
        return refExists(context, guestPath, "refs/heads/$n") ||
            refExists(context, guestPath, "refs/remotes/origin/$n")
    }

    suspend fun checkoutBranch(

        context: Context,

        guestPath: String,

        branch: String,

        mode: CheckoutMode,

    ): Result<Unit> = withContext(Dispatchers.IO) {

        val dirty = isDirty(context, guestPath)

        if (dirty && mode == CheckoutMode.FAIL_IF_DIRTY) {

            return@withContext Result.failure(IllegalStateException("dirty"))

        }

        if (dirty && mode == CheckoutMode.STASH_THEN_SWITCH) {

            val (stashCode, stashOut) = runGit(context, guestPath, "stash push -u -m termfold-checkout")

            if (stashCode != 0) {

                return@withContext Result.failure(IllegalStateException(stashOut.takeLast(400)))

            }

        }

        if (dirty && mode == CheckoutMode.FORCE) {

            runGit(context, guestPath, "reset --hard")

            runGit(context, guestPath, "clean -fd")

        }

        return@withContext switchToBranch(context, guestPath, branch)

    }

    private suspend fun switchToBranch(context: Context, guestPath: String, branch: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val localName = BranchNames.canonicalLocalName(branch)
            if (localName.isBlank()) {
                return@withContext Result.failure(IllegalStateException("empty branch name"))
            }
            var hasLocal = refExists(context, guestPath, "refs/heads/$localName")
            var hasRemote = refExists(context, guestPath, "refs/remotes/origin/$localName")
            if (shouldFetchOriginBeforeSwitch(hasLocal)) {
                runGit(context, guestPath, "fetch origin --prune")
                hasLocal = refExists(context, guestPath, "refs/heads/$localName")
                hasRemote = refExists(context, guestPath, "refs/remotes/origin/$localName")
            }
            if (!hasLocal && !hasRemote) {
                for (fetchCmd in BranchNames.importBranchFetchCommands(localName)) {
                    runGit(context, guestPath, fetchCmd)
                    hasLocal = refExists(context, guestPath, "refs/heads/$localName")
                    hasRemote = refExists(context, guestPath, "refs/remotes/origin/$localName")
                    if (hasLocal || hasRemote) break
                }
            }
            val attempts = BranchNames.switchArgs(localName, hasLocal, hasRemote)
            if (attempts.isEmpty()) {
                return@withContext Result.failure(
                    IllegalStateException("branch '$localName' not found in this repository"),
                )
            }
            var lastOut = ""
            for (args in attempts) {
                val (code, out) = runGit(context, guestPath, args)
                lastOut = out
                if (code == 0) return@withContext Result.success(Unit)
            }
            Result.failure(IllegalStateException(lastOut.takeLast(400)))
        }

    private fun refExists(context: Context, guestPath: String, ref: String): Boolean =
        runGit(context, guestPath, "rev-parse --verify ${shellQuote(ref)}").first == 0

    suspend fun provisionWorkspace(

        context: Context,

        goldenGuestPath: String,

        branch: String,

        sessionWorkspaceId: String,

        onProgress: (ProvisionProgress) -> Unit,

    ) = withContext(Dispatchers.IO) {

        val destGuest = ProjectWorkspaces.sessionGuestPath(goldenGuestPath, sessionWorkspaceId)

        if (!ProjectWorkspaces.isManagedSessionPath(goldenGuestPath, destGuest)) {

            error("Invalid session destination")

        }

        val destHost = Projects.hostPath(context, destGuest)

        val goldenHost = Projects.hostPath(context, goldenGuestPath)

        check(goldenHost.isDirectory) { "Golden project is not a directory" }

        val nodeModulesHost = File(goldenHost, "node_modules")

        val nodeModulesBytes = if (nodeModulesHost.isDirectory) dirSize(nodeModulesHost) else 0L

        val tmpGuest = guestTmpPath(sessionWorkspaceId)

        val tmpHost = Projects.hostPath(context, tmpGuest)

        deleteHostTree(tmpHost)

        coroutineContext.ensureActive()

        destHost.parentFile?.mkdirs()

        if (destHost.exists()) {

            ProjectWorkspaces.deleteTree(destHost)

        }

        val branchLocal = BranchNames.canonicalLocalName(branch)

        var (cloneCode, cloneOut) = runShell(

            context,

            sessionCloneScript(goldenGuestPath, tmpGuest, branchLocal),

        )

        coroutineContext.ensureActive()

        if (cloneCode != 0) {

            val fallback = runShell(context, sessionCloneScript(goldenGuestPath, tmpGuest, null))

            cloneCode = fallback.first

            cloneOut = fallback.second

        }

        if (cloneCode != 0) {

            deleteHostTree(tmpHost)

            error("git clone failed: ${cloneOut.takeLast(500)}")

        }

        val (moveCode, moveOut) = runShell(

            context,

            "mv ${shellQuote(tmpGuest)} ${shellQuote(destGuest)}",

        )

        coroutineContext.ensureActive()

        if (moveCode != 0) {

            deleteHostTree(tmpHost)

            deleteHostTree(destHost)

            error("move workspace failed: ${moveOut.takeLast(500)}")

        }

        runGit(context, destGuest, "stash clear")

        coroutineContext.ensureActive()

        switchToBranch(context, destGuest, branchLocal).getOrElse { err ->
            error("git switch failed: ${err.message?.takeLast(500) ?: err.toString()}")
        }

        runGit(context, destGuest, "reset --hard")

        runGit(context, destGuest, "clean -fd")

        runGit(context, destGuest, "stash clear")

        coroutineContext.ensureActive()

        val repoBytes = dirSize(destHost, skipDirNames = setOf("node_modules"))

        val totalBytes = repoBytes + nodeModulesBytes

        onProgress(ProvisionProgress(repoBytes.coerceAtMost(totalBytes), totalBytes))

        if (nodeModulesHost.isDirectory) {

            var nodeCopied = 0L

            copyHostTree(

                source = nodeModulesHost,

                dest = File(destHost, "node_modules"),

                onBytes = { n ->

                    coroutineContext.ensureActive()

                    nodeCopied += n

                    onProgress(

                        ProvisionProgress(

                            (repoBytes + nodeCopied).coerceAtMost(totalBytes),

                            totalBytes,

                        ),

                    )

                },

            )

        }

        onProgress(ProvisionProgress(totalBytes, totalBytes))

        TermfoldGitignore.ensureTermfoldIgnored(goldenHost)
        TermfoldGitignore.ensureTermfoldIgnored(destHost)

    }

    suspend fun deletePartialWorkspace(context: Context, goldenGuestPath: String, guestPath: String) =

        withContext(Dispatchers.IO) {

            if (!ProjectWorkspaces.isManagedSessionPath(goldenGuestPath, guestPath)) return@withContext

            ProjectWorkspaces.deleteTree(Projects.hostPath(context, guestPath))

            val id = guestPath.substringAfterLast('/')

            deleteHostTree(Projects.hostPath(context, guestTmpPath(id)))

        }

    private fun guestTmpPath(sessionWorkspaceId: String): String = "/tmp/termfold-ws-$sessionWorkspaceId"

    /**
     * Clone golden into /tmp before moving into app storage. Uses plain copies, not hardlinks.
     * `--local` breaks on TermFold when golden and /tmp are on different mounts (pack hardlink errors).
     */
    internal fun sessionCloneScript(goldenGuestPath: String, tmpGuest: String, branch: String? = null): String {
        val branchFlag = branch?.takeIf { it.isNotBlank() }?.let { "-b ${shellQuote(it)} " } ?: ""
        return "cd /tmp && rm -rf ${shellQuote(tmpGuest)} && git clone --no-hardlinks $branchFlag${shellQuote(goldenGuestPath)} ${shellQuote(tmpGuest)}"
    }

    private fun deleteHostTree(dir: File) {

        if (!dir.exists()) return

        ProjectWorkspaces.deleteTree(dir)

    }

    private fun runGit(context: Context, guestCwd: String, args: String): Pair<Int, String> {

        val cmd = "cd ${shellQuote(guestCwd)} && git $args"

        return AcpInstaller.runCapture(context, cmd, timeoutSeconds = 600)

    }

    private fun runShell(context: Context, script: String): Pair<Int, String> =

        AcpInstaller.runCapture(context, script, timeoutSeconds = 600)

    private fun shellQuote(value: String): String =

        "'" + value.replace("'", "'\\''") + "'"

    private fun dirSize(root: File, skipDirNames: Set<String> = emptySet()): Long {

        if (!root.isDirectory) return if (root.isFile) root.length() else 0L

        var sum = 0L

        root.walkTopDown()

            .onEnter { dir ->

                dir == root || dir.name !in skipDirNames

            }

            .forEach { file ->

                if (file.isFile) sum += file.length()

            }

        return sum

    }

    private fun copyHostTree(source: File, dest: File, onBytes: (Long) -> Unit) {

        if (!source.exists()) return

        source.walkTopDown().forEach { file ->

            val relative = file.relativeTo(source).path

            val target = File(dest, relative)

            if (file.isDirectory) {

                target.mkdirs()

            } else {

                target.parentFile?.mkdirs()

                file.inputStream().use { input ->

                    target.outputStream().use { output ->

                        val buffer = ByteArray(64 * 1024)

                        while (true) {

                            val read = input.read(buffer)

                            if (read <= 0) break

                            output.write(buffer, 0, read)

                            onBytes(read.toLong())

                        }

                    }

                }

            }

        }

    }

}

