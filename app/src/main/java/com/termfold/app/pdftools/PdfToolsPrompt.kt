package com.termfold.app.pdftools

object PdfToolsPrompt {
    const val MCP_INSPECT = "pdf_inspect"
    const val MCP_MODEL = "pdf_model"
    const val MCP_PLAN = "pdf_plan"
    const val MCP_APPLY = "pdf_apply"
    const val MCP_VERIFY = "pdf_verify"
    const val MCP_QA = "pdf_qa"
    const val MCP_RENDER = "pdf_render"
    const val MCP_VISUAL_CONFIRM = "pdf_visual_confirm"
    const val MCP_REFUSE = "pdf_refuse"
    const val MCP_FIELDS = "pdf_fields"

    fun prefix(session: PdfToolsSession): String = buildString {
        appendLine(PdfToolsAgentSpec.AGENT_BEHAVIOR)
        appendLine()
        appendLine(PdfToolsAgentSpec.PIPELINE_INTERNAL)
        appendLine()
        appendLine(
            "Session: ${session.id}. Class: ${session.authzClass.name}. Dir: ${PdfToolsPaths.sessionGuest(session.id)}",
        )
        appendLine(
            "First: guest libraries — pdfsys probe; if missing, install full stack for Path A or B, re-probe, then inspect/model/apply/qa.",
        )
        appendLine(
            "Path A only when every designed page has a real text layer: $MCP_FIELDS → $MCP_PLAN replace_text (exact find) → user Approve → $MCP_APPLY → $MCP_VERIFY.",
        )
        appendLine(
            "Before user-ready: $MCP_RENDER → review each work/renders/deliverable/*.png → $MCP_VISUAL_CONFIRM (pages_reviewed) → $MCP_QA if Path B → $MCP_VERIFY.",
        )
        if (session.authzClass == PdfToolsAuthzClass.THIRD_PARTY_ISSUED) {
            appendLine("THIRD_PARTY_ISSUED: refuse edits; use $MCP_REFUSE.")
        }
    }
}
