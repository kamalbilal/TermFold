package com.termfold.app.acp

import org.json.JSONObject

/** Formats interactive ask replies as the next user prompt (MCP `ask_user` deferred flow). */
object AskQuestionWireMessage {

    const val ANSWER_TAG = "termfold-ask-answer"
    const val SKIPPED_TAG = "termfold-ask-skipped"

    /** Label for one skipped question in summaries and wire text. */
    const val SKIPPED_ANSWER_LABEL = "(Skipped)"

    private val recommendedSuffix = Regex("""\s*\(Recommended\)\s*$""", RegexOption.IGNORE_CASE)

    fun deferredMcpToolResult(): JSONObject = JSONObject()
        .put(
            "outcome",
            JSONObject()
                .put("outcome", "termfold_deferred")
                .put(
                    "message",
                    "Interactive card is open and the agent turn was stopped. " +
                        "Do not call tools or send chat. The user will submit answers as their next message.",
                ),
        )

    /** Question line on the ask card; numbered when there is more than one question. */
    fun questionPromptLabel(index: Int, totalQuestions: Int, prompt: String): String {
        val text = prompt.trim()
        if (totalQuestions <= 1) return text
        return "${index + 1}. $text"
    }

    /** Shown on the answered ask card in chat (question / answer / blank line). */
    fun formatAnswerDisplaySummary(
        title: String?,
        questions: List<AskQuestionSpec>,
        answers: List<AskQuestionAnswer>,
    ): String = buildString {
        title?.takeIf { it.isNotBlank() }?.let {
            append(it.trim()).append("\n\n")
        }
        questions.forEachIndexed { index, question ->
            val answer = answers.firstOrNull { it.questionId == question.id } ?: return@forEachIndexed
            val qLine = questionPromptLabel(index, questions.size, question.prompt)
            append(qLine).append('\n')
            if (answer.skipped) {
                append(SKIPPED_ANSWER_LABEL).append("\n\n")
            } else {
                val labels = answer.selectedOptionIds.map { id -> optionLabel(questions, answer, id) }
                append(labels.joinToString(", ")).append("\n\n")
            }
        }
    }.trim()

    fun formatAnsweredUserMessage(
        title: String?,
        questions: List<AskQuestionSpec>,
        answers: List<AskQuestionAnswer>,
    ): String = buildString {
        append("<$ANSWER_TAG>\n")
        append(formatAnswerDisplaySummary(title, questions, answers))
        append("\n</").append(ANSWER_TAG).append('>')
    }.trim()

    fun formatSkippedUserMessage(title: String?): String = buildString {
        append("<$SKIPPED_TAG>")
        title?.takeIf { it.isNotBlank() }?.let { append('\n').append(it) }
        append("\n").append(CURSOR_ASK_SKIPPED_REASON)
        append("\n</").append(SKIPPED_TAG).append('>')
    }.trim()

    private fun optionLabel(
        questions: List<AskQuestionSpec>,
        answer: AskQuestionAnswer,
        optionId: String,
    ): String = when (optionId) {
        CURSOR_ASK_FREEFORM_OPTION_ID -> answer.freeformText?.takeIf { it.isNotBlank() } ?: "Other"
        else -> questions
            .firstOrNull { it.id == answer.questionId }
            ?.options
            ?.firstOrNull { it.id == optionId }
            ?.label
            ?.let { recommendedSuffix.replace(it, "").trim() }
            ?.ifBlank { optionId }
            ?: optionId
    }
}
