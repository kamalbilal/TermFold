package com.termfold.app.acp

/**
 * Mandatory TermFold instruction: structured user questions go through the interactive ask UI,
 * not plain chat.
 *
 * **Wire:** Full block on the first user message of each ACP session, then [compactPromptBlock]
 * via [composeWirePromptText] / [InteractiveAskWireInjection] (all Cursor sessions).
 * [com.termfold.app.automation.AutomationPromptAssembler], [com.termfold.app.pm.PmPrompt], or other
 * prefix helpers — that would duplicate or drift.
 *
 * **MCP:** Builtin server `termfold-ask-user` is registered in [com.termfold.app.acp.mcp.McpCatalog]
 * for every Cursor `session/new`.
 */
object InteractiveAskPromptRules {

    const val ANCHOR_TAG = "termfold-interactive-ask"
    const val ANSWER_TAG = AskQuestionWireMessage.ANSWER_TAG

    val promptBlock: String = """
        <$ANCHOR_TAG>
        ## Interactive questions (mandatory — very critical)

        Whenever you need a choice, preference, confirmation, clarification, or any structured input from the user, you MUST ask in **interactive mode**. Do NOT ask in plain chat (no "reply A/B/C", numbered lists, or yes/no in prose).

        **How (TermFold / Cursor):**
        - Call MCP tool **`ask_user`** on server **`termfold-ask-user`** (Agent, Plan, and Ask modes).
        - TermFold **stops the agent turn** when the card opens. Your answers arrive as the user's **next chat message** (`<$ANSWER_TAG>` / Skip), not as a silent tool callback — do not keep working after `ask_user` returns `termfold_deferred`.
        - Wait for that user message before doing anything else; never call `ask_user` again while a card is open.
        - If the user asked for several questions in one message (e.g. "ask me 5 things"), put them in **one** `ask_user` call (`questions[]` with up to 5 items) — not five separate tool calls in one turn.
        - Otherwise one topic per card; do not stack unrelated asks without an answer in between.
        - Use multiple-choice options; add **(Recommended)** on one option when you have a default.
        - Use **Other…** only when none of the listed options fit.

        If the runtime exposes native **`cursor/ask_question`**, that is the same UX — still do not fall back to chat text.

        Violation: writing "Which do you prefer: X or Y?" in chat instead of `ask_user`.
        </$ANCHOR_TAG>
    """.trimIndent()

    /** Short reminder after the full block was sent once this ACP session (saves context tokens). */
    val compactPromptBlock: String = """
        <$ANCHOR_TAG>
        Use MCP **`termfold-ask-user` / `ask_user`** for choices (not plain chat). After `termfold_deferred`, wait for the user's next message — do not auto-continue.
        </$ANCHOR_TAG>
    """.trimIndent()
}
