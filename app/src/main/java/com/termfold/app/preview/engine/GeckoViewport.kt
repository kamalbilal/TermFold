package com.termfold.app.preview.engine

import org.json.JSONObject

object GeckoViewport {
    const val DESKTOP_WIDTH = 1280

    fun injectMetaJs(desktopWidth: Int, desktop: Boolean): String {
        val content = if (desktop) "width=$desktopWidth" else "width=device-width, initial-scale=1"
        return "(function(){var m=document.querySelector('meta[name=viewport]');" +
            "if(!m){m=document.createElement('meta');m.name='viewport';(document.head||document.documentElement).appendChild(m);}" +
            "m.content='$content';})();"
    }

    suspend fun apply(surface: BrowserSurface, mode: String): Boolean {
        val desktop = mode.equals("desktop", ignoreCase = true)
        val fit = mode.equals("fit", ignoreCase = true) || mode.equals("phone", ignoreCase = true)
        if (!desktop && !fit) return false
        val code = injectMetaJs(DESKTOP_WIDTH, desktop)
        surface.evaluateJavascript(code)
        surface.reload()
        return true
    }
}
