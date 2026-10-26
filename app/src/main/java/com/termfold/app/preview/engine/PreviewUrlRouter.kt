package com.termfold.app.preview.engine

import com.termfold.app.preview.Preview
import java.net.URI

/** URL helpers for preview routing (engine is always Gecko). */
object PreviewUrlRouter {
    const val FILE_HOST = Preview.FILES_HOST

    fun isProjectFileUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val host = runCatching { URI(url).host?.lowercase() }.getOrNull()
        return host == FILE_HOST
    }

    fun sessionKeyForAutomation(sessionId: String): String = "auto-$sessionId"

    fun sessionKeyForCoding(composeKey: String): String = "coding-$composeKey"
}
