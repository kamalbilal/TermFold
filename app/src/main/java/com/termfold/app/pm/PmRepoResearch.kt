package com.termfold.app.pm

import android.content.Context
import com.termfold.app.gitworkspace.GitWorkspace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PmRepoResearch {
    suspend fun verifyRef(context: Context, goldenGuestPath: String, ref: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val err = PmGitQuotes.validateRef(ref)
            if (err != null) return@withContext Result.failure(IllegalArgumentException(err))
            if (GitWorkspace.verifyGitRef(context, goldenGuestPath, ref)) {
                Result.success(Unit)
            } else {
                Result.failure(IllegalArgumentException("Unknown git ref: $ref"))
            }
        }

    suspend fun showFileAtRef(
        context: Context,
        goldenGuestPath: String,
        ref: String,
        path: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        PmGitQuotes.validateRef(ref)?.let { return@withContext Result.failure(IllegalArgumentException(it)) }
        PmGitQuotes.validateRepoPath(path)?.let { return@withContext Result.failure(IllegalArgumentException(it)) }
        GitWorkspace.showFileAtRef(context, goldenGuestPath, ref, path)
    }

    suspend fun logAtRef(
        context: Context,
        goldenGuestPath: String,
        ref: String,
        maxCount: Int = 30,
    ): Result<String> = withContext(Dispatchers.IO) {
        PmGitQuotes.validateRef(ref)?.let { return@withContext Result.failure(IllegalArgumentException(it)) }
        GitWorkspace.logAtRef(context, goldenGuestPath, ref, maxCount.coerceIn(1, 200))
    }

    suspend fun listFilesAtRef(
        context: Context,
        goldenGuestPath: String,
        ref: String,
        treePath: String,
        maxCount: Int = 40,
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        PmGitQuotes.validateRef(ref)?.let { return@withContext Result.failure(IllegalArgumentException(it)) }
        if (treePath.isNotBlank()) {
            PmGitQuotes.validateRepoPath(treePath)?.let { return@withContext Result.failure(IllegalArgumentException(it)) }
        }
        GitWorkspace.listFilesAtRef(context, goldenGuestPath, ref, treePath, maxCount)
    }

    suspend fun diffRefs(
        context: Context,
        goldenGuestPath: String,
        base: String,
        head: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        PmGitQuotes.validateRef(base)?.let { return@withContext Result.failure(IllegalArgumentException(it)) }
        PmGitQuotes.validateRef(head)?.let { return@withContext Result.failure(IllegalArgumentException(it)) }
        GitWorkspace.diffRefs(context, goldenGuestPath, base, head)
    }

    suspend fun defaultViewBranch(context: Context, goldenGuestPath: String): String =
        withContext(Dispatchers.IO) {
            GitWorkspace.currentBranch(context, goldenGuestPath)?.takeIf { it.isNotBlank() }
                ?: "HEAD"
        }
}
