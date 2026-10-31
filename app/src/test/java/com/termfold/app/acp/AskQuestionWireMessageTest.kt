package com.termfold.app.acp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AskQuestionWireMessageTest {

    @Test
    fun questionPromptLabelNumbersWhenMultiple() {
        assertEquals(
            "2. Pick one?",
            AskQuestionWireMessage.questionPromptLabel(1, 3, "Pick one?"),
        )
        assertEquals(
            "Only?",
            AskQuestionWireMessage.questionPromptLabel(0, 1, "Only?"),
        )
    }

    @Test
    fun formatAnswerDisplaySummaryUsesQuestionThenAnswerLines() {
        val text = AskQuestionWireMessage.formatAnswerDisplaySummary(
            title = "Five coding questions",
            questions = listOf(
                AskQuestionSpec("q1", "Language?", listOf(AskQuestionOption("js", "TypeScript")), false),
            ),
            answers = listOf(AskQuestionAnswer("q1", listOf("js"), null)),
        )
        assertTrue(text.contains("Language?\nTypeScript"))
        assertFalse(text.contains("→"))
    }

    @Test
    fun answeredMessageUsesTagAndArrowLines() {
        val text = AskQuestionWireMessage.formatAnsweredUserMessage(
            title = "Prefs",
            questions = listOf(
                AskQuestionSpec(
                    id = "lang",
                    prompt = "Language?",
                    options = listOf(AskQuestionOption("py", "Python")),
                    allowMultiple = false,
                ),
            ),
            answers = listOf(AskQuestionAnswer("lang", listOf("py"), null)),
        )
        assertTrue(text.contains("<${AskQuestionWireMessage.ANSWER_TAG}>"))
        assertTrue(text.contains("Language?\nPython"))
    }

    @Test
    fun deferredMcpResultNamesTermfoldDeferred() {
        val outcome = AskQuestionWireMessage.deferredMcpToolResult()
            .getJSONObject("outcome")
            .getString("outcome")
        assertTrue(outcome == "termfold_deferred")
    }
}
