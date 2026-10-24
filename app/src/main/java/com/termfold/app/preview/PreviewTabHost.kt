package com.termfold.app.preview

import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject

/** In-preview browser tabs for agents and the tab strip. */
class PreviewTabHost(private val maxTabs: Int = 8) {
    data class Tab(
        val id: Int,
        var title: String = "",
        var url: String? = null,
        var webView: WebView? = null,
        var captchaDismissed: Boolean = false,
        var captchaDetected: Boolean = false,
        var visibleControls: Int = 0,
    )

    private val tabs = mutableListOf(Tab(1))
    var activeId: Int = 1
        private set
    private var nextId = 2

    fun activeTab(): Tab = tabs.first { it.id == activeId }
    fun activeWebView(): WebView = activeTab().webView ?: error("Browser is not ready.")

    fun listJson(): JSONArray {
        val arr = JSONArray()
        tabs.forEach { t ->
            arr.put(
                JSONObject()
                    .put("id", t.id)
                    .put("title", t.title)
                    .put("url", t.url.orEmpty())
                    .put("active", t.id == activeId),
            )
        }
        return arr
    }

    fun switch(id: Int): Boolean {
        if (tabs.none { it.id == id }) return false
        tabs.find { it.id == activeId }?.webView?.onPause()
        activeId = id
        activeTab().webView?.onResume()
        return true
    }

    fun newTab(url: String?): Int? {
        if (tabs.size >= maxTabs) return null
        tabs.find { it.id == activeId }?.webView?.onPause()
        val id = nextId++
        tabs.add(Tab(id, url = url))
        activeId = id
        return id
    }

    fun close(id: Int): Boolean {
        if (tabs.size <= 1) return false
        val tab = tabs.find { it.id == id } ?: return false
        tab.webView?.apply {
            stopLoading()
            destroy()
        }
        tabs.remove(tab)
        if (activeId == id) activeId = tabs.last().id
        activeTab().webView?.onResume()
        return true
    }

    fun attachWebView(id: Int, view: WebView) {
        tabs.find { it.id == id }?.webView = view
    }

    fun onWebViewReleased(view: WebView) {
        tabs.find { it.webView === view }?.webView = null
    }
}
