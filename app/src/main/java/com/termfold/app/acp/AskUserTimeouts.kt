package com.termfold.app.acp

/** How long TermFold waits for the user to tap Continue/Skip on an `ask_user` card (hours, not minutes). */
object AskUserTimeouts {
    /** Java [java.net.Socket.setSoTimeout]: 0 = no read timeout. */
    const val SOCKET_READ_TIMEOUT_MS = 0

    /** Node HTTP client to BrowserBridge `/v1` (milliseconds). */
    const val NODE_HTTP_MS = 24L * 60 * 60 * 1000
}
