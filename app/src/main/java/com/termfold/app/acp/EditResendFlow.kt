package com.termfold.app.acp

/** Ordered steps for edit-and-resend; kept explicit for tests and client alignment. */
internal enum class EditResendStep {
    Restore,
    Truncate,
    BeginTurn,
    WireResync,
    CompleteTurn,
}

internal fun editResendSteps(revertFiles: Boolean): List<EditResendStep> =
    buildList {
        if (revertFiles) add(EditResendStep.Restore)
        add(EditResendStep.Truncate)
        if (revertFiles) add(EditResendStep.WireResync)
        add(EditResendStep.BeginTurn)
        add(EditResendStep.CompleteTurn)
    }

/** True when a user turn would send at least one prompt block (including pending wire context). */
internal fun hasPromptPayload(
    pendingWireContext: String?,
    text: String,
    imageCount: Int,
    fileCount: Int,
): Boolean =
    !pendingWireContext.isNullOrBlank() ||
        text.isNotBlank() ||
        imageCount > 0 ||
        fileCount > 0
