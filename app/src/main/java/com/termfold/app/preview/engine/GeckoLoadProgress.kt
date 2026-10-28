package com.termfold.app.preview.engine

/**
 * Gecko keeps firing [onProgressChange] for media/XHR after the document is up
 * (YouTube stays at 70–90). Agent waits and the toolbar spinner never finish.
 * Latch 100 at [onPageStop] until the next main-document start.
 */
class GeckoLoadProgress {
    @Volatile var value: Int = 100
        private set
    @Volatile var documentSettled: Boolean = true
        private set

    fun onNavigate() {
        documentSettled = false
        value = 0
    }

    fun onPageStart(url: String) {
        if (url.isBlank() || url == "about:blank") return
        // YouTube and other SPAs fire onPageStart after the document is up. Ignore those
        // or the toolbar spinner and agent `open` never finish.
        if (documentSettled) return
        if (value >= 100) value = 0
    }

    fun onProgressChange(progress: Int): Int {
        if (documentSettled) return value
        value = progress.coerceIn(0, 100)
        return value
    }

    fun onPageStop(): Int {
        documentSettled = true
        value = 100
        return 100
    }
}
