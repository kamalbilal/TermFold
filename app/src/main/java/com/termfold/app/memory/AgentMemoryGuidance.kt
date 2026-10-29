package com.termfold.app.memory

import com.termfold.app.pdftools.PdfToolsAgentSpec

enum class AgentMemoryWorkflow {
    CODING,
    AUTOMATION,
    PR_REVIEW,
    PM,
    PDF_TOOLS,
}

object AgentMemoryGuidance {

    fun promptBlock(workflow: AgentMemoryWorkflow): String = when (workflow) {
        AgentMemoryWorkflow.PM -> PM_BLOCK
        AgentMemoryWorkflow.PR_REVIEW -> PR_REVIEW_BLOCK
        AgentMemoryWorkflow.PDF_TOOLS -> PDF_TOOLS_BLOCK
        AgentMemoryWorkflow.AUTOMATION,
        AgentMemoryWorkflow.CODING,
        -> TERMFOLD_MEMORY_BLOCK
    }

    private val PR_REVIEW_BLOCK: String = """
## TermFold PR Review (MCP termfold-pr-review)
- When a review turn is ready, call **submit_pr_review_report** with the full schemaVersion 1 report (same fields as in the review prompt).
- Fix and retry if the tool returns a validation error. Do not duplicate the full report JSON in chat after a successful submit.
- Then ask which finding ids to fix in plain language.

## TermFold memory (MCP termfold-automation-memory)
- At the start of a new task, after the user changes direction, or when you are unsure: call session_memory_search with a short query from the user's goal (not only the current URL).
- When you work on a specific site, also call nav_memory_search with site_key = hostname and a query about the UI or flow you need.
- Session-private (session_memory_write, session_memory_get): goals, decisions, findings, and notes for this chat session only.
- Global navigation (nav_memory_write): reusable UI paths and click flows for a site (hostname as site_key). Never passwords.
- Secrets: session_secret_put / session_secret_get only.
- After you learn something useful for a later turn, write it; do not rely on chat history alone.
""".trim()

    private val PDF_TOOLS_BLOCK: String = buildString {
        appendLine("## TermFold PDF Tools")
        appendLine(PdfToolsAgentSpec.AGENT_BEHAVIOR)
        appendLine()
        appendLine("## TermFold memory (MCP termfold-automation-memory)")
        appendLine("- At the start of a new task, after the user changes direction, or when you are unsure: call session_memory_search with a short query from the user's goal (not only the current URL).")
        appendLine("- Session-private (session_memory_write, session_memory_get): goals, decisions, findings, and notes for this chat session only.")
        appendLine("- Secrets: session_secret_put / session_secret_get only.")
    }

    private val TERMFOLD_MEMORY_BLOCK: String = """
## TermFold memory (MCP termfold-automation-memory)
- At the start of a new task, after the user changes direction, or when you are unsure: call session_memory_search with a short query from the user's goal (not only the current URL).
- When you work on a specific site, also call nav_memory_search with site_key = hostname and a query about the UI or flow you need.
- Session-private (session_memory_write, session_memory_get): goals, decisions, findings, and notes for this chat session only.
- Global navigation (nav_memory_write): reusable UI paths and click flows for a site (hostname as site_key). Never passwords.
- Secrets: session_secret_put / session_secret_get only.
- After you learn something useful for a later turn, write it; do not rely on chat history alone.
""".trim()

    private val PM_BLOCK: String = """
## TermFold PM memory (MCP termfold-pm-memory)
- At the start of a product question or when context is thin: call pm_memory_search with a query from the user's goal.
- Persist decisions and findings with pm_memory_write (kinds: finding, decision, competitor, assumption, open_question, note).
- Use pm_memory_get when you need the full body of a chunk from search results.
- Do not store credentials in memory chunks.
""".trim()
}
