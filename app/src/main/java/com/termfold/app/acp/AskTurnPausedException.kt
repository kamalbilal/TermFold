package com.termfold.app.acp

/** Unblocks an in-flight `session/prompt` when pausing the turn for an interactive ask card. */
internal class AskTurnPausedException : java.util.concurrent.CancellationException("AskTurnPaused")
