package com.termfold.app.pm

import android.content.Context
import com.termfold.app.pm.memory.PmMemoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PmResearchOrchestrator {
    suspend fun runResearch(
        context: Context,
        session: PmSession,
        goldenGuestPath: String,
        forceRefresh: Boolean = false,
        onProgress: (String) -> Unit = {},
    ): PmSession = withContext(Dispatchers.IO) {
        val branchKey = when (session.mode) {
            PmMode.DISCOVERY -> session.viewBranch
            PmMode.RELEASE -> session.branch.ifBlank { session.viewBranch }
        }
        if (
            !forceRefresh &&
            !session.researchPackCache.isNullOrBlank() &&
            session.researchPackBranch == branchKey
        ) {
            return@withContext session
        }
        onProgress("Repo research…")
        val repoPack = PmResearchPack.buildRepoPack(
            context = context,
            goldenGuestPath = goldenGuestPath,
            viewBranch = branchKey,
            onProgress = { onProgress(it.step) },
        )
        val competitive = if (session.includeCompetitiveResearch && session.competitors.isNotEmpty()) {
            onProgress("Competitive fetch…")
            PmCompetitiveFetch.fetchSnapshot(session.competitors)
        } else {
            ""
        }
        val combined = buildString {
            appendLine("## Repo pack")
            appendLine(repoPack)
            if (competitive.isNotBlank()) {
                appendLine()
                appendLine("## Competitive pack")
                appendLine(competitive)
            }
        }
        val logEntry = PmResearchLogEntry(
            atMs = System.currentTimeMillis(),
            summary = "Research pack refreshed @ $branchKey",
            outcome = if (competitive.isBlank()) "repo-only" else "repo+web",
        )
        val updated = session.copy(
            researchPackCache = combined,
            researchPackBranch = branchKey,
            researchLog = session.researchLog + logEntry,
            updatedAtMs = System.currentTimeMillis(),
        )
        val repo = PmMemoryRepository(context, session.id)
        repo.upsertChunk(
            kind = "research_pack",
            title = "Research pack @ $branchKey",
            searchText = PmResearchPack.summarizeForPrompt(repoPack),
            body = combined.take(20_000),
        )
        updated
    }

    fun invalidateOnViewBranchChange(session: PmSession, newBranch: String): PmSession =
        if (session.viewBranch == newBranch) session
        else session.copy(
            viewBranch = newBranch,
            researchPackCache = null,
            researchPackBranch = null,
            updatedAtMs = System.currentTimeMillis(),
        )
}
