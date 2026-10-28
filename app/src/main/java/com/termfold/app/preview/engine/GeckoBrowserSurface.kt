package com.termfold.app.preview.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.KeyEvent
import android.view.MotionEvent
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import kotlin.coroutines.resume

class GeckoBrowserSurface(
    private val session: GeckoSession,
    geckoView: GeckoView?,
    private val urlProvider: () -> String? = { null },
    private val historyState: () -> Pair<Boolean, Boolean> = { false to false },
    private val sessionKey: String? = null,
    private val onBeforeDraw: (() -> Unit)? = null,
) : BrowserSurface {

    @Volatile private var geckoView: GeckoView? = geckoView

    fun bindView(view: GeckoView?) {
        geckoView = view
    }

    override fun url(): String? = urlProvider()

    override fun width(): Int = geckoView?.width ?: 0

    override fun height(): Int = geckoView?.height ?: 0

    override suspend fun evaluateJavascript(code: String, timeoutMs: Long): String {
        val response = GeckoTermfoldExtension.request(
            JSONObject().put("op", "eval").put("code", code).put("sessionKey", sessionKey ?: ""),
            timeoutMs = timeoutMs,
            session = session,
        ) ?: return JSONObject().put("error", "engine_not_ready").toString()
        if (response.has("error")) {
            return JSONObject().put("error", response.optString("error")).toString()
        }
        return response.optString("json", "null")
    }

    suspend fun callTf(expression: String, timeoutMs: Long = BrowserBridgeTimeouts.EXTENSION_RPC_MS): JSONObject? {
        val response = GeckoTermfoldExtension.request(
            JSONObject().put("op", "tf").put("expr", expression).put("sessionKey", sessionKey ?: ""),
            timeoutMs = timeoutMs,
            session = session,
        ) ?: return JSONObject().put("error", "engine_not_ready")
        if (response.has("error")) {
            return JSONObject().put("error", response.optString("error"))
        }
        val json = response.optString("json", "")
        if (json.isBlank()) return JSONObject().put("error", "empty_response")
        return com.termfold.app.preview.BrowserJsResults.parseTfJson(json)
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
        onBeforeDraw?.invoke()
        runCatching { geckoView?.draw(canvas) }
    }

    override fun createScreenshotBitmap(): Bitmap? {
        val v = geckoView ?: return null
        if (v.width <= 0 || v.height <= 0) return null
        return Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
    }

    /** Reads compositor pixels; works for headless offscreen views where [draw] is blank. */
    suspend fun captureScreenshot(timeoutMs: Long = 5_000L): Bitmap? {
        onBeforeDraw?.invoke()
        val view = geckoView ?: return null
        if (view.width <= 0 || view.height <= 0) return null
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                view.capturePixels().accept(
                    { bmp ->
                        if (cont.isActive) cont.resume(bmp)
                    },
                    { _ ->
                        if (cont.isActive) cont.resume(null)
                    },
                )
            }
        }
    }

}
