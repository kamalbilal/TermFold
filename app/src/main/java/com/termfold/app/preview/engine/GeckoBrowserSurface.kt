package com.termfold.app.preview.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.KeyEvent
import android.view.MotionEvent
import org.json.JSONObject
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

class GeckoBrowserSurface(
    private val session: GeckoSession,
    private val geckoView: GeckoView?,
    private val urlProvider: () -> String? = { null },
    private val historyState: () -> Pair<Boolean, Boolean> = { false to false },
    private val sessionKey: String? = null,
) : BrowserSurface {

    override fun url(): String? = urlProvider()

    override fun width(): Int = geckoView?.width ?: 0

    override fun height(): Int = geckoView?.height ?: 0

    override suspend fun evaluateJavascript(code: String, timeoutMs: Long): String {
        val response = GeckoTermfoldExtension.request(
            JSONObject().put("op", "eval").put("code", code).put("sessionKey", sessionKey ?: ""),
            timeoutMs,
        ) ?: return "null"
        if (response.has("error")) return "null"
        return response.optString("json", "null")
    }

    suspend fun callTf(expression: String, timeoutMs: Long = BrowserBridgeTimeouts.EXTENSION_RPC_MS): JSONObject? {
        val response = GeckoTermfoldExtension.request(
            JSONObject().put("op", "tf").put("expr", expression).put("sessionKey", sessionKey ?: ""),
            timeoutMs,
        ) ?: return JSONObject().put("error", "engine_not_ready")
        if (response.has("error")) {
            return JSONObject().put("error", response.optString("error"))
        }
        val json = response.optString("json", "")
        if (json.isBlank()) return JSONObject().put("error", "empty_response")
        return runCatching { JSONObject(json) }.getOrElse {
            JSONObject().put("error", it.message ?: "parse_failed")
        }
    }

    override fun onResume() {
        // GeckoSession stays active without WebView-style onResume.
    }

    override fun canGoBack(): Boolean = historyState().first
    override fun canGoForward(): Boolean = historyState().second
    override fun goBack() {
        session.goBack()
    }
    override fun goForward() {
        session.goForward()
    }
    override fun reload() {
        session.reload()
    }

    override fun requestFocus() {
        geckoView?.requestFocus()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean =
        geckoView?.dispatchTouchEvent(event) ?: false

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        geckoView?.dispatchKeyEvent(event) ?: false

    override fun drawScreenshot(canvas: Canvas) {
        geckoView?.draw(canvas)
    }

    override fun createScreenshotBitmap(): Bitmap? {
        val v = geckoView ?: return null
        if (v.width <= 0 || v.height <= 0) return null
        return Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
    }

}
