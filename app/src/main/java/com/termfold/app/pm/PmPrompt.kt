package com.termfold.app.pm

import android.content.Context
import com.termfold.app.pm.memory.PmMemoryRepository

object PmPrompt {
    const val ANCHOR = "termfold-pm-context"
    const val MAX_TOTAL_CHARS = 14_000
    private const val MAX_MEMORY_CHUNKS = 6
    private const val MAX_MEMORY_CHARS = 280

    fun prefix(
        context: Context,
        session: PmSession,
        userQuery: String = "",
    ): String = buildString {
        appendLine("<$ANCHOR>")
        appendLine(instructions(session))
        appendLine()
        appendLine(sessionFacts(session))
        val brief = PmBriefFiles.readBriefBundle(context, session.id, session.mode)
        if (brief.isNotBlank()) {
            appendLine()
            appendLine("## Session brief (trusted user docs)")
            appendLine(brief.take(3_000))
        }
        val packSummary = PmResearchPack.summarizeForPrompt(session.researchPackCache, maxChars = 8_000)
        if (packSummary.isNotBlank()) {
            appendLine()
            appendLine("## Research pack (already fetched; treat as primary evidence)")
            appendLine(packSummary)
        } else {
            appendLine()
            appendLine("## Research pack")
            appendLine("[gap] No research pack yet. Ask the user to tap Refresh research, or answer only from the session brief and mark other claims as gaps.")
        }
        val memory = memoryLines(context, session.id, userQuery)
        if (memory.isNotBlank()) {
            appendLine()
            appendLine("## Session memory")
            appendLine(memory)
        }
        appendLine("</$ANCHOR>")
    }.let { text ->
        if (text.length <= MAX_TOTAL_CHARS) text.trim()
        else text.take(MAX_TOTAL_CHARS).trim() + "\n…"
    }

    internal fun instructions(session: PmSession): String = buildString {
        appendLine("You are a senior enterprise Product Manager. TermFold is only the host app.")
        appendLine("The product under review is the linked coding project — not TermFold, unless that project is TermFold.")
        appendLine()
        appendLine("Evidence rules (mandatory):")
        appendLine("- Tag every product, market, or competitor claim as [repo: path@ref], [web: url], [brief], or [gap].")
        appendLine("- Never invent market size, competitor features, pricing, or URLs. Unsourced claims are [gap].")
        appendLine("- Prefer the research pack below. It was read at a git ref; working-tree files may be a different branch.")
        when (session.mode) {
            PmMode.DISCOVERY -> {
                appendLine("- Discovery is read-only. Do not edit, write, delete, or `git checkout` on the golden tree.")
                appendLine("- Extra repo facts: `git show ${session.viewBranch}:path` or `git log ${session.viewBranch}`. Do not trust Read of the working tree as ${session.viewBranch}.")
            }
            PmMode.RELEASE -> {
                appendLine("- Release planning on a temp copy of branch `${session.branch.ifBlank { session.viewBranch }}`.")
                appendLine("- Plan only: do not implement code, commit, or push unless the user explicitly asks.")
            }
        }
        appendLine("- Persist material findings with MCP termfold-pm-memory: pm_memory_write, pm_memory_search, pm_memory_get.")
        appendLine("  kinds: finding, decision, competitor, assumption, open_question, research_pack, note.")
        appendLine("- Do not use generic web-search tools for competitors. Competitive pages are in the research pack when enabled.")
        appendLine()
        appendLine("When the user asks a product question, reply with a readable Decision memo AND a fenced JSON block:")
        appendLine("```json")
        appendLine("{")
        appendLine("  \"recommendation\": \"...\",")
        appendLine("  \"landscape\": \"...\",")
        appendLine("  \"options\": \"...\",")
        appendLine("  \"scope_in\": \"...\",")
        appendLine("  \"scope_out\": \"...\",")
        appendLine("  \"risks\": \"...\",")
        appendLine("  \"gaps\": \"...\"")
        appendLine("}")
        appendLine("```")
        appendLine("Each memo section must use evidence tags. If competitive research is missing, say so in gaps — do not fill from training data.")
    }.trim()

    internal fun sessionFacts(session: PmSession): String = buildString {
        appendLine("## Session")
        appendLine("Mode: ${session.mode.name}")
        when (session.mode) {
            PmMode.DISCOVERY -> appendLine("View branch (git ref for all repo cites): ${session.viewBranch.ifBlank { "HEAD" }}")
            PmMode.RELEASE -> {
                appendLine("Release branch: ${session.branch.ifBlank { session.viewBranch }}")
                if (session.releaseLabel.isNotBlank()) appendLine("Release label: ${session.releaseLabel}")
                if (session.workspaceId == null) {
                    appendLine("Temp copy is gone (Done). Chat and memory remain; do not assume a writable workspace.")
                }
            }
        }
        if (session.productOneLiner.isNotBlank()) {
            appendLine("One-liner: ${session.productOneLiner}")
        }
        appendLine("Competitive research: ${if (session.includeCompetitiveResearch) "ON" else "OFF"}")
        if (session.competitors.isEmpty()) {
            appendLine("Competitors: none listed" + if (session.includeCompetitiveResearch) " — skip web claims; suggest the user add name+URL." else "")
        } else {
            appendLine("Competitors:")
            session.competitors.forEach { c ->
                val url = c.homepageUrl.ifBlank { "no URL [gap]" }
                appendLine("- ${c.name}: $url")
            }
        }
    }.trim()

    private fun memoryLines(context: Context, sessionId: String, userQuery: String): String {
        val repo = PmMemoryRepository(context, sessionId)
        val ranked = if (userQuery.isNotBlank()) {
            repo.search(userQuery, MAX_MEMORY_CHUNKS)
        } else {
            emptyList()
        }
        val recent = repo.topChunks(MAX_MEMORY_CHUNKS)
        val merged = (ranked + recent)
            .distinctBy { it.id.takeIf { id -> id != 0L } ?: (it.kind to it.title) }
            .take(MAX_MEMORY_CHUNKS)
        return merged.joinToString("\n") { chunk ->
            "- [${chunk.kind}] ${chunk.title}: ${chunk.body.take(MAX_MEMORY_CHARS)}"
        }
    }
}
