package com.termfold.app.preview.network

import com.termfold.app.preview.BrowserBridge

/** @deprecated Agent/UI use [NetworkCaptureStore]; thin delegate for legacy extension events. */
object GeckoNetworkLog {
    fun record(sessionKey: String, method: String, url: String, outcome: String, detail: String = "") {
        NetworkCaptureStore.recordLegacy(sessionKey, method, url, outcome, detail)
    }

    fun text(sessionKey: String, failedOnly: Boolean, sinceGeneration: Int): String {
        val (text, _) = NetworkCaptureStore.listText(
            sessionKey,
            sinceCursor = 0,
            failedOnly = failedOnly,
            xhrOnly = false,
            urlContains = null,
            limit = NetworkCaptureStore.LIST_DEFAULT_LIMIT,
            sinceGeneration = sinceGeneration,
        )
        return text
    }

    fun failedCount(sessionKey: String): Int = NetworkCaptureStore.failedCount(sessionKey)
}
