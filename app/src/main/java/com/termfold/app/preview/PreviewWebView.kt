package com.termfold.app.preview

import android.annotation.SuppressLint
import android.content.Context
import android.view.ViewTreeObserver
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

/** WebView tweaks for dev servers and modern SPAs inside Compose (layout + darkening). */
object PreviewWebView {

    @SuppressLint("SetJavaScriptEnabled")
    fun applySettings(context: Context, settings: WebSettings, desktopUserAgent: Boolean) {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.setSupportMultipleWindows(true)
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = false
        settings.offscreenPreRaster = true
        var ua = WebSettings.getDefaultUserAgent(context).replace("; wv", "")
        if (desktopUserAgent) {
            ua = ua.replace(Regex("\\(Linux; Android [^)]*\\)"), "(X11; Linux x86_64)").replace(" Mobile", "")
        }
        settings.userAgentString = ua
    }

    fun applyRenderingPolicy(view: WebView) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(view.settings, false)
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
            WebSettingsCompat.setForceDark(view.settings, WebSettingsCompat.FORCE_DARK_OFF)
        }
        runCatching {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK_STRATEGY)) {
                WebSettingsCompat.setForceDarkStrategy(
                    view.settings,
                    WebSettingsCompat.DARK_STRATEGY_PREFER_WEB_THEME_OVER_USER_AGENT_DARKENING,
                )
            }
        }
    }

    /**
     * Loads [url] only after the view has non-zero size. SPAs that read `innerHeight` on boot
     * paint nothing if the first layout pass was 0×0 (common with Compose AndroidView).
     */
    fun loadUrlWhenReady(view: WebView, url: String) {
        if (url.isBlank() || url == "about:blank") return
        fun go() {
            val current = view.url?.takeUnless { it == "about:blank" }
            if (current != url) view.loadUrl(url) else nudgeAfterLoad(view, url)
        }
        if (view.width > 0 && view.height > 0) {
            go()
            return
        }
        view.post {
            if (view.width > 0 && view.height > 0) {
                go()
                return@post
            }
            val observer = view.viewTreeObserver
            val listener = object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    if (view.width <= 0 || view.height <= 0) return
                    observer.removeOnGlobalLayoutListener(this)
                    go()
                }
            }
            observer.addOnGlobalLayoutListener(listener)
        }
    }

    fun injectViewportMeta(view: WebView, desktopWidth: Int, desktop: Boolean) {
        val content = if (desktop) "width=$desktopWidth" else "width=device-width, initial-scale=1"
        view.evaluateJavascript(
            "(function(){var m=document.querySelector('meta[name=viewport]');" +
                "if(!m){m=document.createElement('meta');m.name='viewport';(document.head||document.documentElement).appendChild(m);}" +
                "m.content='$content';})();",
            null,
        )
    }

    fun nudgeAfterLoad(view: WebView, url: String?) {
        if (url.isNullOrBlank() || url == "about:blank") return
        view.post {
            view.requestLayout()
            view.invalidate()
            view.evaluateJavascript(NUDGE_JS, null)
            view.postDelayed({ view.evaluateJavascript(NUDGE_JS, null) }, 120)
        }
    }

    private val NUDGE_JS = """
        (function(){
          try {
            var h = window.innerHeight || document.documentElement.clientHeight || 0;
            if (h > 0) {
              document.documentElement.style.setProperty('--vh', (h * 0.01) + 'px');
              document.documentElement.style.minHeight = h + 'px';
              if (document.body) document.body.style.minHeight = h + 'px';
            }
            window.dispatchEvent(new Event('resize'));
            if (window.visualViewport) window.visualViewport.dispatchEvent(new Event('resize'));
            var y = window.scrollY || 0;
            window.scrollTo(0, y + 1);
            window.scrollTo(0, y);
            requestAnimationFrame(function(){
              window.dispatchEvent(new Event('resize'));
            });
          } catch (e) {}
        })();
    """.trimIndent()
}
