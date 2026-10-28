package com.termfold.app.acp

/**
 * Builds the text block prepended to user messages on the wire (excluding automation command rewriting).
 */
internal fun composeWirePromptText(
    pendingWireContext: String?,
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
    listOfNotNull(
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
