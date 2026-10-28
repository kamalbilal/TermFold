package com.termfold.app.preview.engine

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import com.termfold.app.preview.GeckoConsoleLog
import com.termfold.app.preview.network.NetworkCaptureStore
import com.termfold.app.preview.network.NetworkRulesHub
import org.mozilla.geckoview.WebExtension
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

/** Built-in WebExtension + native messaging for `window.__tf` automation. */
object GeckoTermfoldExtension {
    private const val TAG = "GeckoTermfoldExt"
    private const val EXT_ID = "termfold@termfold"
    private const val EXT_URI = "resource://android/assets/gecko-termfold/"
    const val NATIVE_APP = "termfold_browser"

    private val extensionRef = AtomicReference<WebExtension?>(null)
    private val nativePort = AtomicReference<WebExtension.Port?>(null)
    private val pending = ConcurrentHashMap<String, (JSONObject) -> Unit>()
    private val sessions = java.util.Collections.newSetFromMap(ConcurrentHashMap<GeckoSession, Boolean>())
    private val runtimeRef = AtomicReference<GeckoRuntime?>(null)
    private val activeSession = AtomicReference<GeckoSession?>(null)

    fun ensureBuiltIn(runtime: GeckoRuntime) {
        runtimeRef.set(runtime)
        if (extensionRef.get() != null) {
            sessions.forEach { wireSession(runtime, it) }
            return
        }
        synchronized(this) {
            if (extensionRef.get() != null) return
        }
        runtime.webExtensionController.ensureBuiltIn(EXT_URI, EXT_ID).accept(
            { ext ->
                if (ext == null) {
                    Log.e(TAG, "ensureBuiltIn returned null extension")
                    return@accept
                }
                extensionRef.set(ext)
                ext.setMessageDelegate(
                    object : WebExtension.MessageDelegate {
                        override fun onMessage(
                            nativeApp: String,
                            message: Any,
                            sender: WebExtension.MessageSender,
                        ): GeckoResult<Any>? {
                            if (nativeApp != NATIVE_APP) return null
                            val json = message as? JSONObject ?: return null
                            ingestExtensionEvent(json)
                            val id = json.optString("id")
                            if (id.isNotBlank()) {
                                pending.remove(id)?.invoke(json)
                            }
                            return null
                        }

                        override fun onConnect(port: WebExtension.Port) {
                            if (port.name != NATIVE_APP) return
                            nativePort.set(port)
                            port.setDelegate(
                                object : WebExtension.PortDelegate {
                                    override fun onPortMessage(message: Any, port: WebExtension.Port) {
                                        val json = message as? JSONObject ?: return
                                        if (ingestExtensionEvent(json)) return
                                        val id = json.optString("id")
                                        if (id.isNotBlank()) {
                                            pending.remove(id)?.invoke(json)
                                        }
                                    }

                                    override fun onDisconnect(port: WebExtension.Port) {
                                        if (nativePort.compareAndSet(port, null)) {
                                            Log.w(TAG, "Native port disconnected")
                                        }
                                    }
                                },
                            )
                            Log.i(TAG, "Native port connected")
                            NetworkRulesHub.pushToExtension()
                        }
                    },
                    NATIVE_APP,
                )
                sessions.forEach { wireSession(runtime, it) }
                Log.i(TAG, "Built-in extension ready")
            },
            { e -> Log.e(TAG, "ensureBuiltIn failed", e) },
        )
    }

    fun registerSession(session: GeckoSession) {
        sessions.add(session)
        val runtime = runtimeRef.get() ?: return
        extension()?.let { wireSession(runtime, session) }
    }

    fun unregisterSession(session: GeckoSession) {
        sessions.remove(session)
        if (activeSession.compareAndSet(session, null)) {
            val runtime = runtimeRef.get() ?: return
            runCatching { runtime.webExtensionController.setTabActive(session, false) }
        }
    }

    /** Routes extension `tabs` APIs to this Gecko session before automation RPC. */
    fun activateSession(session: GeckoSession) {
        val runtime = runtimeRef.get() ?: return
        val previous = activeSession.getAndSet(session)
        if (previous != null && previous !== session) {
            runCatching { runtime.webExtensionController.setTabActive(previous, false) }
            runCatching { previous.setActive(false) }
        }
        runCatching { runtime.webExtensionController.setTabActive(session, true) }
        session.setActive(true)
        session.setFocused(true)
    }

    private fun wireSession(runtime: GeckoRuntime, session: GeckoSession) {
        val ext = extensionRef.get() ?: return
        session.webExtensionController.setMessageDelegate(
            ext,
            object : WebExtension.MessageDelegate {},
            NATIVE_APP,
        )
    }

    private fun ingestExtensionEvent(json: JSONObject): Boolean {
        when (json.optString("type")) {
            "network_capture" -> {
                NetworkCaptureStore.ingest(json)
                return true
            }
            "console" -> {
                GeckoConsoleLog.record(
                    json.optString("sessionKey"),
                    json.optString("level", "log"),
                    json.optString("text"),
                    json.optString("where"),
                )
                return true
            }
        }
        return false
    }

    fun extension(): WebExtension? = extensionRef.get()

    fun registerResponse(id: String, handler: (JSONObject) -> Unit) {
        pending[id] = handler
    }

    fun cancelResponse(id: String) {
        pending.remove(id)
    }

    suspend fun request(
        payload: JSONObject,
        timeoutMs: Long = BrowserBridgeTimeouts.EXTENSION_RPC_MS,
        session: GeckoSession? = null,
    ): JSONObject? {
        if (session != null) {
            withContext(Dispatchers.Main.immediate) {
                activateSession(session)
            }
        }
        val deadline = System.currentTimeMillis() + timeoutMs
        while (extension() == null && System.currentTimeMillis() < deadline) {
            kotlinx.coroutines.delay(50)
        }
        if (extension() == null) {
            return JSONObject().put("error", "engine_not_ready")
        }
        var port = nativePort.get()
        if (port == null) {
            while (nativePort.get() == null && System.currentTimeMillis() < deadline) {
                kotlinx.coroutines.delay(50)
            }
            port = nativePort.get()
        }
        if (port == null) {
            return JSONObject().put("error", "extension_port_missing")
        }
        val id = UUID.randomUUID().toString()
        val remaining = (deadline - System.currentTimeMillis()).coerceAtLeast(1L)
        return try {
            withTimeout(remaining) {
                suspendCancellableCoroutine { cont ->
                    registerResponse(id) { response ->
                        if (cont.isActive) cont.resume(response)
                    }
                    cont.invokeOnCancellation { cancelResponse(id) }
                    runCatching {
                        port.postMessage(
                            JSONObject()
                                .put("id", id)
                                .put("payload", payload),
                        )
                    }.onFailure { e ->
                        cancelResponse(id)
                        if (cont.isActive) {
                            cont.resume(JSONObject().put("error", e.message ?: "post_failed"))
                        }
                    }
                }
            }
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            cancelResponse(id)
            JSONObject().put("error", "extension_rpc_timeout")
        }
    }
}
