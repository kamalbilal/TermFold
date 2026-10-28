package com.termfold.app.projectrules

object ProjectRulesPromptAssembler {
    const val ANCHOR_TAG = "termfold-project-rules"
    const val MAX_TOTAL_CHARS = 10_000

    private val preamble = """
        You MUST follow every enabled project rule below on this turn and on every later turn while it remains enabled.
        These rules override generic habits and casual shortcuts unless a rule explicitly says otherwise.
        If the user's latest message explicitly conflicts with a rule, follow the user message and briefly note which rule you are not applying.
    """.trimIndent()

    fun assemble(rules: List<ProjectRule>): String {
        val active = rules.filter { it.body.isNotBlank() || it.title.isNotBlank() }
        if (active.isEmpty()) return ""
        val body = buildString {
            appendLine("<$ANCHOR_TAG>")
            appendLine(preamble)
            appendLine()
            active.forEachIndexed { index, rule ->
                val title = rule.title.trim().ifBlank { "Rule ${index + 1}" }
                appendLine("## $title")
                if (rule.body.isNotBlank()) {
                    appendLine(rule.body.trim())
                }
                appendLine()
            }
            append("</$ANCHOR_TAG>")
        }
        return if (body.length <= MAX_TOTAL_CHARS) {
            body
        } else {
            body.take(MAX_TOTAL_CHARS - 40) + "\n… [project rules truncated]\n</$ANCHOR_TAG>"
        }
    }
}
