package com.termfold.app.automation

import android.content.Context
import com.termfold.app.automation.memory.AutomationMemoryRepository
import com.termfold.app.core.Folder

object AutomationPromptAssembler {

    const val ANCHOR_TAG = "termfold-automation-context"

    fun assemble(
        context: Context,
        projectId: String,
        sessionId: String,
        linkedFolder: Folder?,
        currentHost: String?,
    ): String {
        val sessionKey = AutomationSessionContext.key(projectId, sessionId)
        val docs = AutomationStorage.readMandatoryDocs(context, projectId, sessionId)
        val constraintsFile = java.io.File(
            AutomationPaths.hostSessionDir(context, projectId, sessionId),
            "docs/constraints.md",
        )
        val guardPolicy = AutomationGuardrails.policyFromConstraintsFile(constraintsFile)
        val guardSummary = AutomationGuardrails.guardrailSummaryForPrompt(guardPolicy)
        val repo = AutomationMemoryRepository(context)
        val recent = repo.recentEvents(sessionKey, AutomationInjectCaps.MAX_RECENT_EVENTS).joinToString("\n") { event ->
            "- ${event.type} ${event.url.take(80)} ${event.outcome}: ${event.summary.take(120)}"
        }
        val memory = repo.searchSessionMemory(sessionKey, currentHost ?: "session", AutomationInjectCaps.MAX_SESSION_CHUNKS)
            .joinToString("\n") {
                "- ${it.title}: ${it.body.take(AutomationInjectCaps.MAX_CHUNK_CHARS)}"
            }
        val global = currentHost?.let { host ->
            repo.searchGlobalNav(host, "navigation", AutomationInjectCaps.MAX_GLOBAL_CHUNKS)
                .joinToString("\n") {
                    "- ${it.title}: ${it.body.take(AutomationInjectCaps.MAX_CHUNK_CHARS)}"
                }
        }.orEmpty()
        val link = linkedFolder?.let { folder ->
            "Linked coding project (read-only): ${folder.name} at ${folder.path}"
        }
        return buildString {
            appendLine("<$ANCHOR_TAG>")
            appendLine(
                "Web: do not use built-in web search or WebFetch tools. " +
                    "Use MCP termfold-browser (browser_open, browser_snapshot, browser_click, …) for all web research and pages.",
            )
            if (docs.isNotBlank()) {
                appendLine("## Session documents")
                appendLine(docs)
            }
            if (!guardSummary.isNullOrBlank()) {
                appendLine("## Enforced guardrails")
                appendLine(guardSummary)
            }
            link?.let { appendLine(it) }
            if (recent.isNotBlank()) {
                appendLine("## Recent automation actions")
                appendLine(recent)
            }
            if (memory.isNotBlank()) {
                appendLine("## Session memory")
                appendLine(memory)
            }
            if (global.isNotBlank()) {
                appendLine("## Site navigation memory")
                appendLine(global)
            }
            appendLine("</$ANCHOR_TAG>")
        }.let(AutomationInjectCaps::trimBlock)
    }
}
