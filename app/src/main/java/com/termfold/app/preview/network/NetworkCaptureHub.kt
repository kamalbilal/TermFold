package com.termfold.app.preview.network

import com.termfold.app.preview.engine.GeckoTermfoldExtension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** Pushes capture options to gecko-termfold (body tee is expensive on mobile). */
object NetworkCaptureHub {
    const val MODE_OFF = "off"
    const val MODE_LITE = "lite"
    const val MODE_FULL = "full"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val uiSubscribed = ConcurrentHashMap<String, Boolean>()

    fun setUiSubscribed(sessionKey: String, subscribed: Boolean) {
        if (sessionKey.isBlank()) return
        if (subscribed) {
            uiSubscribed[sessionKey] = true
        } else {
            uiSubscribed.remove(sessionKey)
        }
        pushCaptureOptions(sessionKey)
    }

    fun bodyCaptureEnabled(sessionKey: String): Boolean =
        NetworkCaptureStore.isPreserve(sessionKey) || uiSubscribed[sessionKey] == true

    /** Full headers + optional bodies; lite is one summary event per interesting request. */
    fun captureMode(sessionKey: String): String = when {
        bodyCaptureEnabled(sessionKey) -> MODE_FULL
        sessionKey.isBlank() -> MODE_OFF
        else -> MODE_LITE
    }

    fun pushCaptureOptions(sessionKey: String) {
        if (sessionKey.isBlank()) return
        val mode = captureMode(sessionKey)
        val body = mode == MODE_FULL && bodyCaptureEnabled(sessionKey)
        scope.launch {
            GeckoTermfoldExtension.request(
                JSONObject()
                    .put("op", "set_capture_options")
                    .put("sessionKey", sessionKey)
                    .put("captureMode", mode)
                    .put("bodyCapture", body),
            )
        }
    }

    fun pushCaptureOff(sessionKey: String) {
        if (sessionKey.isBlank()) return
        scope.launch {
            GeckoTermfoldExtension.request(
                JSONObject()
                    .put("op", "set_capture_options")
                    .put("sessionKey", sessionKey)
                    .put("captureMode", MODE_OFF)
                    .put("bodyCapture", false),
            )
        }
    }
}
