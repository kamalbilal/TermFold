package com.termfold.app.automation

/** `/spec` in automation sessions: turn a short user ask into session docs. */
object AutomationSpecCommand {
    const val SLASH_COMMAND = "spec"

    private val promptPattern = Regex(
        """^\s*/$SLASH_COMMAND(?:\s+(?<request>.+))?\s*$""",
        RegexOption.IGNORE_CASE,
    )

    /** User text after `/spec`, or null if this is not a spec prompt. */
    fun requestFromPrompt(text: String): String? {
        val match = promptPattern.matchEntire(text.trim()) ?: return null
        return match.groups["request"]?.value?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun isSpecPrompt(text: String): Boolean = promptPattern.matches(text.trim())

    fun isBareSpecPrompt(text: String): Boolean =
        isSpecPrompt(text) && requestFromPrompt(text) == null

    fun toWirePrompt(request: String): String = buildString {
        appendLine("Update this automation session's specification documents from the user request below.")
        appendLine()
        appendLine("User request:")
        appendLine(request.trim())
        appendLine()
        appendLine("Overwrite the full contents of these files in the session workspace:")
        appendLine("- docs/requirements.md — high-level requirements: goals, scope, success criteria, actors, out of scope")
        appendLine("- docs/prd.md — product requirements document: user stories, flows, acceptance criteria")
        appendLine("- docs/constraints.md — constraints and must-nots")
        appendLine()
        appendLine("In docs/constraints.md you MUST keep this exact enforced block (adjust allow_hosts / deny_url_contains if the user needs them; keep legal_sources_only unless the user explicitly opts out):")
        appendLine(AutomationGuardrails.defaultGuardrailBlockMarkdown.trim())
        appendLine()
        appendLine("Use clear Markdown with headings. Write only under this automation session tree.")
        appendLine("When finished, reply briefly confirming the three files were updated.")
    }
}
