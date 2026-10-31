package com.termfold.app.acp

/**
 * Builds the text block prepended to every user message on the ACP wire.
 *
 * **Single injection point for mandatory session instructions** (including
 * [InteractiveAskPromptRules]). Do not duplicate those rules in workflow-specific
 * prompt assemblers (PM, PR, PDF, automation, project rules). New session types
 * must send user text only through [AcpClient] → [buildPromptContent] → here.
 *
 * When [interactiveAskInjection] is [InteractiveAskWireInjection.Full] or [Compact],
 * the matching [InteractiveAskPromptRules] block is prepended — callers cannot forget it.
 */
enum class InteractiveAskWireInjection {
    None,
    Full,
    Compact,
}

internal fun composeWirePromptText(
    pendingWireContext: String?,
    interactiveAskInjection: InteractiveAskWireInjection,
    automationPrefix: String?,
    agentMemoryPrefix: String?,
    extraPrefix: String?,
    projectRulesPrefix: String?,
    userText: String,
): String = buildString {
    pendingWireContext?.takeIf { it.isNotBlank() }?.let { ctx ->
        append(ctx)
        append("\n\n---\n\n")
    }
    val interactiveAskPrefix = when (interactiveAskInjection) {
        InteractiveAskWireInjection.Full -> InteractiveAskPromptRules.promptBlock
        InteractiveAskWireInjection.Compact -> InteractiveAskPromptRules.compactPromptBlock
        InteractiveAskWireInjection.None -> null
    }
    listOfNotNull(
        interactiveAskPrefix?.takeIf { it.isNotBlank() },
        automationPrefix?.takeIf { it.isNotBlank() },
        agentMemoryPrefix?.takeIf { it.isNotBlank() },
        extraPrefix?.takeIf { it.isNotBlank() },
        projectRulesPrefix?.takeIf { it.isNotBlank() },
    ).forEach { block ->
        append(block)
        append("\n\n---\n\n")
    }
    append(userText)
}
