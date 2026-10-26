package com.termfold.app.preview.network

import com.termfold.app.preview.BrowserBridge
import com.termfold.app.preview.engine.PreviewUrlRouter

object NetworkRuleKeys {
    fun boundSessionKey(raw: String? = null): String? =
        normalize(raw, BrowserBridge.automationSessionKey, BrowserBridge.codingSessionKey)

    fun normalize(raw: String?, automationKey: String?, codingComposeKey: String?): String? {
        val given = raw?.trim().orEmpty()
        if (given.isNotEmpty()) {
            if (given.startsWith("auto-") || given.startsWith("coding-")) return given
            if (given.contains(':') && !given.contains('/')) return given
            if (automationKey != null && given == automationKey) return given
            return PreviewUrlRouter.sessionKeyForCoding(given)
        }
        automationKey?.let { return it }
        codingComposeKey?.let { return PreviewUrlRouter.sessionKeyForCoding(it) }
        return null
    }
}
