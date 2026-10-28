package com.termfold.app.preview.engine

object BrowserBridgeTimeouts {
    const val MCP_COMMAND_MS = 90_000L
    const val EXTENSION_RPC_MS = 30_000L
    /** Wait this long for a previous command on the same session before failing. */
    const val EXCLUSIVE_WAIT_MS = 45_000L
    /** Short eval while polling load; full RPC timeout would stall `open` past MCP_COMMAND_MS. */
    const val READY_STATE_EVAL_MS = 2_500L
    const val EXTENSION_PORT_WAIT_MS = 8_000L
    const val PREVIEW_ATTACH_MS = 15_000L
    const val PREVIEW_ATTACH_BACKGROUND_MS = 75_000L
}
