package com.termfold.app.memory

import android.content.Context
import com.termfold.app.automation.AutomationInjectCaps
import com.termfold.app.automation.memory.AutomationMemoryRepository

object AgentMemoryPromptAssembler {

    const val ANCHOR_TAG = "termfold-agent-memory-context"

    fun prefix(
        context: Context,
        memorySessionKey: String,
        userQuery: String,
        currentHost: String?,
        workflow: AgentMemoryWorkflow,
    ): String {
        if (workflow == AgentMemoryWorkflow.PM) {
            return AgentMemoryGuidance.promptBlock(workflow)
        }
        val repo = AutomationMemoryRepository(context)
        val memoryQuery = userQuery.trim().takeIf { it.isNotBlank() }
            ?: currentHost?.takeIf { it.isNotBlank() }
            ?: "session"
        val memory = repo.searchSessionMemory(memorySessionKey, memoryQuery, AutomationInjectCaps.MAX_SESSION_CHUNKS)
            .joinToString("\n") {
                "- ${it.title}: ${it.body.take(AutomationInjectCaps.MAX_CHUNK_CHARS)}"
            }
        val navQuery = userQuery.trim().takeIf { it.isNotBlank() } ?: "navigation"
        val global = currentHost?.let { host ->
            repo.searchGlobalNav(host, navQuery, AutomationInjectCaps.MAX_GLOBAL_CHUNKS)
                .joinToString("\n") {
                    "- ${it.title}: ${it.body.take(AutomationInjectCaps.MAX_CHUNK_CHARS)}"
                }
        }.orEmpty()
        return buildString {
            appendLine("<$ANCHOR_TAG>")
            appendLine(AgentMemoryGuidance.promptBlock(workflow))
            if (memory.isNotBlank()) {
                appendLine("## Session memory (preview)")
                appendLine(memory)
            }
            if (global.isNotBlank()) {
                appendLine("## Site navigation memory (preview)")
                appendLine(global)
            }
            appendLine("</$ANCHOR_TAG>")
        }.let(AutomationInjectCaps::trimBlock)
    }
}
