package com.termfold.app.acp

/**
 * One file touched by an ACP tool call, with optional unified diff text for display.
 */
data class AcpToolFileDiff(
    val path: String,
    val operation: Operation,
    val oldPath: String? = null,
    val patchText: String? = null,
) {
    enum class Operation {
        ADD,
        DELETE,
        MODIFY,
        MOVE,
        COPY,
        UNKNOWN,
    }
}
