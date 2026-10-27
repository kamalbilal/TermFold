package com.termfold.app.gitworkspace

import android.content.Context

import com.termfold.app.acp.AcpInstaller

import com.termfold.app.shell.Projects

import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.ensureActive

import kotlinx.coroutines.sync.Mutex

import kotlinx.coroutines.sync.withLock

import kotlinx.coroutines.withContext

import java.io.File

import java.util.concurrent.ConcurrentHashMap

import kotlin.coroutines.coroutineContext

enum class CheckoutMode {

    FAIL_IF_DIRTY,

    STASH_THEN_SWITCH,

    FORCE,

}

object GitWorkspace {

    /** Bump when session-copy upstream repair logic changes so existing copies re-run once. */
    private const val UPSTREAM_REPAIR_SCHEMA = "3"

    private val upstreamRepairedGuestPaths = ConcurrentHashMap.newKeySet<String>()

    /** One checkout/stash at a time per repo — parallel `git status` + checkout corrupts the index. */
    private val checkoutLocks = ConcurrentHashMap<String, Mutex>()

    data class ProvisionProgress(val copiedBytes: Long, val totalBytes: Long) {

        val percent: Int

            get() = if (totalBytes <= 0L) 0 else ((copiedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)

    }

    suspend fun currentBranch(context: Context, guestPath: String): String? = withContext(Dispatchers.IO) {

        val (code, out) = runGit(context, guestPath, "rev-parse --abbrev-ref HEAD")

        if (code != 0) return@withContext null

        out.trim().takeIf { it.isNotBlank() && it != "HEAD" }

    }

    suspend fun originUrl(context: Context, guestPath: String): String? = withContext(Dispatchers.IO) {
        val (code, out) = runGit(context, guestPath, "remote get-url origin")
        if (code == 0) out.trim().takeIf { it.isNotBlank() } else null
    }

    suspend fun fetchOrigin(context: Context, guestPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        val (code, out) = runGit(context, guestPath, "fetch origin --prune")
        if (code == 0) Result.success(Unit) else Result.failure(IllegalStateException(out.takeLast(400)))
    }

    suspend fun hasUnpushedCommits(context: Context, guestPath: String): Boolean {
        val outgoing = loadChangeSummary(context, guestPath)?.outgoingCommits ?: 0
        return outgoing > 0
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
        ensureSessionCopyGitUpstream(context, guestPath, branch)
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
        if (isDirectoryDiffPath(path)) {
            val files = listChangedFilesInDirectory(context, guestPath, path, staged, untracked)
            if (files.isEmpty()) return@withContext ""
            val combined = files.mapNotNull { file ->
                loadUnifiedDiffSingle(context, guestPath, file, staged, untracked)
                    .takeIf { it.isNotBlank() }
            }.joinToString("\n")
            return@withContext capDiffText(combined)
        }
        loadUnifiedDiffSingle(context, guestPath, path, staged, untracked)
    }

    private fun loadUnifiedDiffSingle(
        context: Context,
        guestPath: String,
        path: String,
        staged: Boolean,
        untracked: Boolean,
    ): String {
        val quoted = shellQuote(path)
        val args = when {
            staged -> "diff -U3 --cached -- $quoted"
            // `--` keeps /dev/null from being parsed as a path under the repo (e.g. scripts/null).
            untracked -> "diff --no-index -U3 -- /dev/null $quoted"
            else -> "diff -U3 -- $quoted"
        }
        val (code, out) = runGit(context, guestPath, args)
        if (code != 0 && out.isBlank()) return ""
        return capDiffText(out)
    }

    private fun listChangedFilesInDirectory(
        context: Context,
        guestPath: String,
        path: String,
        staged: Boolean,
        untracked: Boolean,
    ): List<String> {
        val dirSpec = directoryPathspec(path)
        val args = when {
            staged -> "diff --cached --name-only -- $dirSpec"
            untracked -> "ls-files -o --exclude-standard -- $dirSpec"
            else -> "diff --name-only -- $dirSpec"
        }
        val (code, out) = runGit(context, guestPath, args)
        if (code != 0) return emptyList()
        return parseGitNameOnlyOutput(out)
    }

    private const val MAX_DIFF_CHARS = 512 * 1024

    private fun capDiffText(out: String): String =
        if (out.length > MAX_DIFF_CHARS) {
            out.take(MAX_DIFF_CHARS) + "\n\n… diff truncated …"
        } else {
            out
        }

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
     * Branch names for the picker on a session copy: golden catalog, filtered to branches that exist
     * on the copy or resolve on the golden repo (one batched ref scan — not per-branch git calls).
     */
    suspend fun listBranchesForPicker(
        context: Context,
        checkoutGuestPath: String,
        catalogGuestPath: String?,
    ): List<String> = withContext(Dispatchers.IO) {
        val catalog = catalogGuestPath?.takeIf { it.isNotBlank() && it != checkoutGuestPath }
        if (catalog == null) return@withContext listBranches(context, checkoutGuestPath)
        val golden = listBranches(context, catalog)
        if (!isGitRepository(context, checkoutGuestPath)) {
            return@withContext golden
        }
        val onCopy = listBranches(context, checkoutGuestPath).toSet()
        if (onCopy.isEmpty()) {
            return@withContext golden
        }
        val resolvableOnCatalog = resolvableBranchNamesOnCatalog(context, catalog)
        filterPickerBranches(golden, onCopy, resolvableOnCatalog)
    }

    internal fun filterPickerBranches(
        golden: List<String>,
        onCopy: Set<String>,
        catalogResolvable: Set<String>,
    ): List<String> =
        golden.filter { name ->
            name in onCopy || BranchNames.canonicalLocalName(name) in catalogResolvable
        }

    private fun resolvableBranchNamesOnCatalog(context: Context, catalogGuestPath: String): Set<String> {
        val (code, out) = runGit(
            context,
            catalogGuestPath,
            "for-each-ref --format='%(refname:short)' refs/heads refs/remotes/origin",
        )
        if (code != 0 || out.isBlank()) return emptySet()
        return parseBranchLines(out.lines())
            .map { BranchNames.canonicalLocalName(it) }
            .filter { it.isNotBlank() }
            .toSet()
    }

    suspend fun checkoutBranch(

        context: Context,

        guestPath: String,

        branch: String,

        mode: CheckoutMode,

    ): Result<Unit> = withContext(Dispatchers.IO) {
        val lock = checkoutLocks.getOrPut(guestPath) { Mutex() }
        lock.withLock {
            val dirty = isDirty(context, guestPath)

            if (dirty && mode == CheckoutMode.FAIL_IF_DIRTY) {
                return@withLock Result.failure(IllegalStateException("dirty"))
            }

            if (dirty && mode == CheckoutMode.STASH_THEN_SWITCH) {
                val (stashCode, stashOut) = stashBeforeCheckout(context, guestPath)
                if (stashCode != 0) {
                    return@withLock Result.failure(IllegalStateException(stashOut.takeLast(400)))
                }
            }

            if (dirty && mode == CheckoutMode.FORCE) {
                prepareGitWrite(context, guestPath)
                runGit(context, guestPath, "reset --hard")
                runGit(context, guestPath, "clean -fd")
            }

            switchToBranch(context, guestPath, branch)
        }
    }

    /** Clear stale locks and refresh the index before mutating commands (stash, checkout). */
    private fun prepareGitWrite(context: Context, guestPath: String) {
        runShell(
            context,
            "cd ${shellQuote(guestPath)} && rm -f .git/index.lock && git update-index -q --refresh 2>/dev/null || true",
        )
    }

    private fun stashBeforeCheckout(context: Context, guestPath: String): Pair<Int, String> {
        fun attempt(includeUntracked: Boolean): Pair<Int, String> {
            prepareGitWrite(context, guestPath)
            val cmd = if (includeUntracked) {
                "stash push -u -m termfold-checkout"
            } else {
                "stash push -m termfold-checkout"
            }
            return runGit(context, guestPath, cmd)
        }
        var (code, out) = attempt(includeUntracked = true)
        if (code != 0 && indexWriteFailure(out)) {
            prepareGitWrite(context, guestPath)
            val retry = attempt(includeUntracked = true)
            code = retry.first
            out = retry.second
        }
        if (code != 0 && indexWriteFailure(out)) {
            // Huge or awkward untracked trees can break `stash -u`; still switch with tracked work stashed.
            val trackedOnly = attempt(includeUntracked = false)
            code = trackedOnly.first
            out = trackedOnly.second
        }
        return code to out
    }

    private fun indexWriteFailure(gitOutput: String): Boolean {
        val lower = gitOutput.lowercase()
        return lower.contains("could not write index") ||
            lower.contains("index.lock") ||
            lower.contains("unable to write new index")
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
            prepareGitWrite(context, guestPath)
            var lastOut = ""
            for (args in attempts) {
                val (code, out) = runGit(context, guestPath, args)
                lastOut = out
                if (code == 0) {
                    reconcileBranchUpstream(context, guestPath, localName)
                    return@withContext Result.success(Unit)
                }
            }
            Result.failure(IllegalStateException(lastOut.takeLast(400)))
        }

    /**
     * Align session-copy `origin`, fetch, and set or clear upstream tracking.
     * Safe to call when opening a session or refreshing git status.
     */
    suspend fun ensureSessionCopyGitUpstream(
        context: Context,
        guestPath: String,
        branchName: String? = null,
    ) = withContext(Dispatchers.IO) {
        if (!ProjectWorkspaces.looksLikeSessionWorkspace(guestPath)) return@withContext
        if (!isGitRepository(context, guestPath)) return@withContext
        val branch = branchName?.let { BranchNames.canonicalLocalName(it) }
            ?: currentBranch(context, guestPath)
            ?: readBranchFromHost(context, guestPath)
            ?: return@withContext
        if (branch.isBlank()) return@withContext
        if (!shouldRunSessionUpstreamRepair(context, guestPath, branch)) return@withContext
        if (!upstreamRepairedGuestPaths.add(guestPath)) return@withContext
        reconcileBranchUpstream(context, guestPath, branch)
        if (sessionCopyUpstreamIsSettled(context, guestPath, branch)) {
            runGit(
                context,
                guestPath,
                "config termfold.upstreamRepairSchema $UPSTREAM_REPAIR_SCHEMA",
            )
        } else {
            upstreamRepairedGuestPaths.remove(guestPath)
        }
    }

    suspend fun reconcileBranchUpstream(context: Context, guestPath: String, branchName: String) =
        withContext(Dispatchers.IO) {
            if (!ProjectWorkspaces.looksLikeSessionWorkspace(guestPath)) return@withContext
            val branch = BranchNames.canonicalLocalName(branchName)
            if (branch.isBlank() || !isGitRepository(context, guestPath)) return@withContext
            alignSessionCopyOriginWithGolden(context, guestPath)
            val (fetchCode, _) = runGit(context, guestPath, "fetch origin --prune")
            val fetchSucceeded = fetchCode == 0
            val hasOrigin = fetchSucceeded &&
                refExists(context, guestPath, "refs/remotes/origin/$branch")
            val current = currentBranch(context, guestPath)
            val repair = SessionCopyUpstreamRepair.action(
                originBranchExists = hasOrigin,
                isCheckedOutBranch = current == branch,
                fetchSucceeded = fetchSucceeded,
            )
            val args = SessionCopyUpstreamRepair.branchArgs(branch, repair) ?: return@withContext
            runGit(context, guestPath, args)
        }

    private fun hasUpstream(context: Context, guestPath: String): Boolean =
        runGit(context, guestPath, "rev-parse --abbrev-ref @{upstream}").first == 0

    private suspend fun shouldRunSessionUpstreamRepair(
        context: Context,
        guestPath: String,
        branch: String,
    ): Boolean {
        val (schemaCode, schemaOut) =
            runGit(context, guestPath, "config --get termfold.upstreamRepairSchema")
        val schemaCurrent = schemaCode == 0 && schemaOut.trim() == UPSTREAM_REPAIR_SCHEMA
        if (hasUpstream(context, guestPath)) return false
        if (!schemaCurrent) return true
        alignSessionCopyOriginWithGolden(context, guestPath)
        val (fetchCode, _) = runGit(context, guestPath, "fetch origin --prune")
        if (fetchCode != 0) return true
        return refExists(context, guestPath, "refs/remotes/origin/$branch")
    }

    private suspend fun sessionCopyUpstreamIsSettled(
        context: Context,
        guestPath: String,
        branch: String,
    ): Boolean {
        if (hasUpstream(context, guestPath)) return true
        val (fetchCode, _) = runGit(context, guestPath, "fetch origin --prune")
        if (fetchCode != 0) return false
        return !refExists(context, guestPath, "refs/remotes/origin/$branch")
    }

    private fun refExists(context: Context, guestPath: String, ref: String): Boolean =
        runGit(context, guestPath, "rev-parse --verify ${shellQuote(ref)}").first == 0

    suspend fun verifyGitRef(context: Context, guestPath: String, ref: String): Boolean =
        withContext(Dispatchers.IO) {
            if (refExists(context, guestPath, ref)) return@withContext true
            if (!ref.contains('/') && refExists(context, guestPath, "refs/heads/$ref")) return@withContext true
            false
        }

    suspend fun showFileAtRef(
        context: Context,
        guestPath: String,
        ref: String,
        path: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!verifyGitRef(context, guestPath, ref)) {
            return@withContext Result.failure(IllegalArgumentException("Unknown ref: $ref"))
        }
        val (code, out) = runGit(
            context,
            guestPath,
            "show ${shellQuote("$ref:$path")}",
        )
        if (code == 0) Result.success(out) else Result.failure(IllegalStateException(out.takeLast(400)))
    }

    suspend fun logAtRef(
        context: Context,
        guestPath: String,
        ref: String,
        maxCount: Int,
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!verifyGitRef(context, guestPath, ref)) {
            return@withContext Result.failure(IllegalArgumentException("Unknown ref: $ref"))
        }
        val (code, out) = runGit(
            context,
            guestPath,
            "log ${shellQuote(ref)} -n $maxCount --oneline",
        )
        if (code == 0) Result.success(out) else Result.failure(IllegalStateException(out.takeLast(400)))
    }

    suspend fun listFilesAtRef(
        context: Context,
        guestPath: String,
        ref: String,
        treePath: String,
        maxCount: Int = 40,
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        if (!verifyGitRef(context, guestPath, ref)) {
            return@withContext Result.failure(IllegalArgumentException("Unknown ref: $ref"))
        }
        val recurse = if (treePath.isBlank()) "" else "-r "
        val pathspec = if (treePath.isBlank()) {
            ""
        } else {
            " -- ${shellQuote(treePath.trimEnd('/'))}"
        }
        val (code, out) = runGit(
            context,
            guestPath,
            "ls-tree ${recurse}--name-only ${shellQuote(ref)}$pathspec",
        )
        if (code != 0) return@withContext Result.failure(IllegalStateException(out.takeLast(400)))
        val paths = out.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .take(maxCount.coerceIn(1, 200))
            .toList()
        Result.success(paths)
    }

    suspend fun diffRefs(
        context: Context,
        guestPath: String,
        base: String,
        head: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!verifyGitRef(context, guestPath, base) || !verifyGitRef(context, guestPath, head)) {
            return@withContext Result.failure(IllegalArgumentException("Unknown ref in diff range"))
        }
        val (code, out) = runGit(
            context,
            guestPath,
            "diff ${shellQuote("$base..$head")} --stat",
        )
        if (code == 0) Result.success(out) else Result.failure(IllegalStateException(out.takeLast(400)))
    }

    private fun rememberGoldenGuestPath(context: Context, sessionGuestPath: String, goldenGuestPath: String) {
        runGit(
            context,
            sessionGuestPath,
            "config termfold.goldenGuestPath ${shellQuote(goldenGuestPath)}",
        )
    }

    private suspend fun alignSessionCopyOriginWithGolden(context: Context, sessionGuestPath: String) {
        val (copyCode, copyOrigin) = runGit(context, sessionGuestPath, "remote get-url origin")
        if (copyCode != 0) return
        val goldenGuestPath = readGoldenGuestPath(context, sessionGuestPath, copyOrigin.trim())
            ?: return
        val (_, goldenOrigin) = runGit(context, goldenGuestPath, "remote get-url origin")
        val resolved = SessionCopyOriginSync.resolveOriginUrlForSessionCopy(
            copyOrigin.trim(),
            goldenOrigin.trim(),
        ) ?: return
        if (copyOrigin.trim() == resolved) return
        runGit(context, sessionGuestPath, "remote set-url origin ${shellQuote(resolved)}")
    }

    private suspend fun readGoldenGuestPath(
        context: Context,
        sessionGuestPath: String,
        sessionOriginUrl: String,
    ): String? {
        val (code, out) = runGit(context, sessionGuestPath, "config --get termfold.goldenGuestPath")
        if (code == 0) {
            val path = out.trim()
            if (path.isNotBlank()) return path
        }
        val origin = sessionOriginUrl.trim()
        if (origin.startsWith("/") && isGitRepository(context, origin)) return origin
        return WorkspaceStore.findGoldenGuestPathForSessionCopy(context, sessionGuestPath)
    }

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

        ProjectWorkspaces.ensureSessionStorageDirs(context, goldenGuestPath)

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

        rememberGoldenGuestPath(context, destGuest, goldenGuestPath)
        switchToBranch(context, destGuest, branchLocal).getOrElse { err ->
            error("git switch failed: ${err.message?.takeLast(500) ?: err.toString()}")
        }
        reconcileBranchUpstream(context, destGuest, branchLocal)

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

