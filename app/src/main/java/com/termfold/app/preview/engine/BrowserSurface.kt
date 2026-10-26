package com.termfold.app.preview.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.KeyEvent
import android.view.MotionEvent
import android.webkit.WebView

/** Engine-neutral page automation surface (WebView today, Gecko via extension bridge). */
interface BrowserSurface {
    fun url(): String?
    fun width(): Int
    fun height(): Int
    suspend fun evaluateJavascript(code: String, timeoutMs: Long = BrowserBridgeTimeouts.EXTENSION_RPC_MS): String
    fun onResume()
    fun canGoBack(): Boolean
    fun canGoForward(): Boolean
    fun goBack()
    fun goForward()
    fun reload()
    fun requestFocus()
    fun dispatchTouchEvent(event: MotionEvent): Boolean
    fun dispatchKeyEvent(event: KeyEvent): Boolean
    fun drawScreenshot(canvas: Canvas)
    fun createScreenshotBitmap(): Bitmap?
}

class WebViewBrowserSurface(private val webView: WebView) : BrowserSurface {
    override fun url(): String? = webView.url
    override fun width(): Int = webView.width
    override fun height(): Int = webView.height

    override suspend fun evaluateJavascript(code: String, timeoutMs: Long): String =
        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            webView.evaluateJavascript(code) { value ->
                if (cont.isActive) cont.resume(value ?: "null") {}
            }
        }

    override fun onResume() {
        webView.onResume()
    }

    override fun canGoBack(): Boolean = webView.canGoBack()
    override fun canGoForward(): Boolean = webView.canGoForward()
    override fun goBack() {
        webView.goBack()
    }

    override fun goForward() {
        webView.goForward()
    }

    override fun reload() {
        webView.reload()
    }

    override fun requestFocus() {
        webView.requestFocus()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean = webView.dispatchTouchEvent(event)
    override fun dispatchKeyEvent(event: KeyEvent): Boolean = webView.dispatchKeyEvent(event)

    override fun drawScreenshot(canvas: Canvas) {
        webView.draw(canvas)
    }

    override fun createScreenshotBitmap(): Bitmap? {
        if (webView.width <= 0 || webView.height <= 0) return null
        return Bitmap.createBitmap(webView.width, webView.height, Bitmap.Config.ARGB_8888)
    }
}
