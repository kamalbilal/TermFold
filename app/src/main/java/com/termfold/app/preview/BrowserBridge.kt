package com.termfold.app.preview

import android.content.Context
import android.util.Log
import com.termfold.app.automation.AutomationSessionContext
import com.termfold.app.automation.memory.AutomationEpisodeTracker
import com.termfold.app.automation.memory.AutomationMemoryBridge
import com.termfold.app.shell.ShellPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import com.termfold.app.preview.engine.BrowserBridgeTimeouts
import com.termfold.app.preview.engine.BrowserSessionRegistry
import com.termfold.app.preview.engine.PreviewUrlRouter
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Lets agents in the Linux environment use the preview browser: open pages, read them, click,
 * type, scroll, take screenshots, read the console. The agent side is `termfold-browser` (a
 * command and an MCP server) talking to this over loopback.
 *
 * Only programs in the guest can use it: the port and a random key are written to
 * ~/.termfold/browser.json, inside the app's private storage, and every request must carry the
 * key. The listener is bound to 127.0.0.1 only.
 */
object BrowserBridge {

    private const val TAG = "BrowserBridge"
    private val memoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private const val CONFIG = "root/.termfold/browser.json"

    /** What drives the WebView: the preview pane registers one while it is on screen. */
    interface Controller {
        suspend fun run(action: String, args: JSONObject): JSONObject
    }

    @Volatile
    var controller: Controller? = null

    /** When set, browser commands apply only to this automation session (`projectId:sessionId`). */
    @Volatile
    var automationSessionKey: String? = null

    /** Active Product Manager session id for pm_memory_* tools. */
    @Volatile
    var pmSessionId: String? = null

    /** Active PR Review session id for pr_review_* tools. */
    @Volatile
    var prReviewSessionId: String? = null

    /** Active coding-agent session compose key (workspace chat); lazy headless browser profile. */
    @Volatile
    var codingSessionKey: String? = null

    /** What the user sees: whether an agent is driving, what it just did, and whether it is paused. */
    data class Activity(val active: Boolean = false, val label: String = "", val paused: Boolean = false)

    private val _activity = MutableStateFlow(Activity())
    val activity: StateFlow<Activity> = _activity.asStateFlow()

    @Volatile private var lastCommand = 0L

    /** Commands being carried out right now; the frame stays lit while any is running. */
    private val running = java.util.concurrent.atomic.AtomicInteger(0)
    private val _commandInFlight = MutableStateFlow(false)
    /** True while an agent browser command is executing on the main thread. */
    val commandInFlight: StateFlow<Boolean> = _commandInFlight.asStateFlow()

    private fun syncCommandInFlight() {
        _commandInFlight.value = running.get() > 0
    }

    @Volatile var captchaPending: Boolean = false

    private val actionLog = ArrayDeque<ActionEntry>(8)
    data class ActionEntry(val label: String, val tabId: Int = 0, val at: Long = System.currentTimeMillis())

    @Volatile var networkGeneration: Int = 0

    fun actionLog(): List<ActionEntry> = synchronized(actionLog) { actionLog.toList() }

    fun recordAction(label: String, tabId: Int = 0) = synchronized(actionLog) {
        actionLog.addLast(ActionEntry(label, tabId))
        while (actionLog.size > 8) actionLog.removeFirst()
        val sessionKey = automationSessionKey
        val ctx = appContext
        if (sessionKey != null && ctx != null) {
            memoryScope.launch {
                runCatching {
                    com.termfold.app.automation.memory.AutomationMemoryRepository(ctx).appendEvent(
                        automationSessionId = sessionKey,
                        type = "browser",
                        url = lastBrowserUrl,
                        summary = label,
                        outcome = "ok",
                    )
                    AutomationEpisodeTracker.onBrowserEvent(ctx, sessionKey, lastBrowserUrl, label)
                }
            }
        }
    }

    @Volatile
    var lastBrowserUrl: String? = null

    fun writeBrowserConfig(context: Context, port: Int? = null) {
        if (!::token.isInitialized) return
        val file = File(ShellPaths.rootfsDir(context), CONFIG)
        file.parentFile?.mkdirs()
        val body = JSONObject()
            .put("port", port ?: runCatching {
                JSONObject(file.readText()).optInt("port")
            }.getOrDefault(0))
            .put("token", token)
            .put("automation_session_key", automationSessionKey ?: AutomationSessionContext.activeKey ?: "")
            .put("pm_session_id", pmSessionId ?: com.termfold.app.pm.PmSessionContext.activeSessionId ?: "")
            .put(
                "pr_review_session_id",
                prReviewSessionId ?: com.termfold.app.prreview.PrReviewSessionContext.activeSessionId ?: "",
            )
            .put("agent_session_key", codingSessionKey ?: "")
        file.writeText(body.toString())
        file.setReadable(false, false)
        file.setReadable(true, true)
    }

    fun bumpNetworkGeneration() {
        networkGeneration++
    }
    @Volatile private var started = false
    private lateinit var token: String
    @Volatile private var appContext: Context? = null

    /** The user took the browser back: commands are refused until they let the agent continue. */
    fun pause() = _activity.update { it.copy(paused = true, active = false) }

    fun resume() = _activity.update { it.copy(paused = false) }

    fun start(context: Context) {
        if (started) return
        synchronized(this) {
            if (started) return
            started = true
        }
        val app = context.applicationContext
        appContext = app
        com.termfold.app.preview.network.NetworkRulesHub.attach(app)
        com.termfold.app.preview.network.NetworkCaptureStore.attach(app)
        Thread({ serve(app) }, "termfold-browser").apply { isDaemon = true }.start()
        // The busy frame fades a few seconds after the agent's last action.
        Thread({
            while (true) {
                Thread.sleep(1000)
                val a = _activity.value
                if (a.active && running.get() == 0 && System.currentTimeMillis() - lastCommand > 6000) _activity.update { it.copy(active = false) }
            }
        }, "termfold-browser-idle").apply { isDaemon = true }.start()
    }

    private fun serve(context: Context) {
        val server = runCatching {
            ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
        }.getOrElse {
            Log.w(TAG, "Could not start the browser bridge", it)
            return
        }
        token = ByteArray(24).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        runCatching {
            val file = File(ShellPaths.rootfsDir(context), CONFIG)
            file.parentFile?.mkdirs()
            val port = server.localPort
            writeBrowserConfig(context, port)
            file.setReadable(false, false)
            file.setReadable(true, true)
        }.onFailure { Log.w(TAG, "Could not write the browser config", it) }
        while (true) {
            val socket = runCatching { server.accept() }.getOrNull() ?: continue
            Thread({ handle(context, socket) }, "termfold-browser-call").apply { isDaemon = true }.start()
        }
    }

    private fun handle(context: Context, socket: Socket) {
        socket.use { s ->
            s.soTimeout = 180_000
            val input = BufferedInputStream(s.getInputStream())
            val headers = readHeaders(input) ?: return
            val length = headers["content-length"]?.toIntOrNull() ?: 0
            if (length > 2_000_000) return respond(s, 413, JSONObject().put("error", "Request too large"))
            val body = ByteArray(length)
            var read = 0
            while (read < length) {
                val n = input.read(body, read, length - read)
                if (n < 0) break
                read += n
            }
            val request = runCatching { JSONObject(String(body, Charsets.UTF_8)) }.getOrNull()
                ?: return respond(s, 400, JSONObject().put("error", "Bad request"))
            if (!sameKey(request.optString("token"))) return respond(s, 403, JSONObject().put("error", "Wrong key"))
            val result = runBlocking { execute(context, request.optString("action"), request.optJSONObject("args") ?: JSONObject()) }
            respond(s, 200, result)
        }
    }

    private suspend fun execute(context: Context, action: String, args: JSONObject): JSONObject {
        if (action.startsWith("pm_memory_")) {
            val requiredId = pmSessionId
            if (requiredId != null && com.termfold.app.pm.PmSessionContext.activeSessionId != requiredId) {
                return JSONObject().put("error", "Browser command is bound to another PM session")
            }
            return com.termfold.app.pm.memory.PmMemoryBridge.handle(context, action, args)
        }
        if (action.startsWith("pr_review_")) {
            val requiredId = prReviewSessionId
            if (requiredId != null && com.termfold.app.prreview.PrReviewSessionContext.activeSessionId != requiredId) {
                return JSONObject().put("error", "Browser command is bound to another PR Review session")
            }
            if (requiredId != null) {
                val given = args.optString("session_id")
                if (given.isNotBlank() && given != requiredId) {
                    return JSONObject().put("error", "Browser command is bound to another PR Review session")
                }
            }
            return com.termfold.app.prreview.PrReviewReportBridge.handle(action, args)
        }
        if (action.startsWith("memory_")) {
            val requiredKey = automationSessionKey
            if (requiredKey != null && AutomationSessionContext.activeKey != requiredKey) {
                return JSONObject().put("error", "Browser command is bound to another automation session")
            }
            return AutomationMemoryBridge.handle(context, action, args)
        }
        val requiredKey = automationSessionKey
        if (requiredKey != null) {
            val given = args.optString("session_key")
            if (given.isNotBlank() && given != requiredKey) {
                return JSONObject().put("error", "Browser command is bound to another automation session")
            }
        }
        if (_activity.value.paused) {
            return JSONObject().put(
                "error",
                "The user has taken over the browser and paused agent control. Ask them to tap " +
                    "\"Let agent continue\" in the preview, then try again.",
            )
        }
        if (captchaPending && action !in setOf("dismiss_captcha", "status", "health", "tab_list")) {
            return JSONObject().put(
                "error",
                "A CAPTCHA or security challenge is on screen. Ask the user to solve it on the device, " +
                    "then use wait with captcha_cleared or tap Continue in the preview.",
            )
        }
        lastCommand = System.currentTimeMillis()
        _activity.update { it.copy(active = true, label = labelFor(action, args)) }
        var registryKey = resolveRegistryKey(requiredKey, args)
        val codingKey = codingSessionKey?.takeIf { it.isNotBlank() }
        if (registryKey == null && requiredKey == null && codingKey != null) {
            registryKey = PreviewUrlRouter.sessionKeyForCoding(codingKey)
        }
        if (registryKey != null || codingKey != null) {
            withContext(Dispatchers.Main) {
                if (codingKey != null) {
                    registryKey = BrowserSessionRegistry.ensureCoding(context.applicationContext, codingKey).key
                }
                Preview.revealForAgent()
            }
        }
        val registryDriver = { key: String? -> key?.let { BrowserSessionRegistry.controllerFor(it) } }
        var driver = registryDriver(registryKey) ?: controller
        if (driver == null) {
            val nobodyLooking = !com.termfold.app.notify.AppPresence.inForeground
            if (nobodyLooking) com.termfold.app.notify.Notifier.askForBrowser(context)
            val until = System.currentTimeMillis() + if (nobodyLooking) {
                BrowserBridgeTimeouts.PREVIEW_ATTACH_BACKGROUND_MS
            } else {
                BrowserBridgeTimeouts.PREVIEW_ATTACH_MS
            }
            while (System.currentTimeMillis() < until) {
                if (registryKey != null && BrowserSessionRegistry.isUiAttached(registryKey)) {
                    driver = registryDriver(registryKey)
                    break
                }
                driver = registryDriver(registryKey) ?: controller
                if (driver != null && (registryKey == null || BrowserSessionRegistry.isUiAttached(registryKey))) {
                    break
                }
                delay(50)
            }
            if (nobodyLooking) com.termfold.app.notify.Notifier.cancelBrowserRequest(context)
            driver = registryDriver(registryKey) ?: controller ?: driver
        }
        if (driver == null) {
            return JSONObject().put(
                "error",
                if (registryKey != null) {
                    "No browser session is registered for this agent run. Open preview once or restart the session."
                } else {
                    "The browser can only work while TermFold is on screen (the app, or its bubble opened). " +
                        "The user has been asked to open it; try the command again once they have."
                },
            )
        }
        running.incrementAndGet()
        syncCommandInFlight()
        val runBlock: suspend () -> JSONObject = { driver!!.run(action, args) }
        return runCatching {
            if (registryKey != null) {
                BrowserSessionRegistry.runExclusive(registryKey) {
                    withTimeout(BrowserBridgeTimeouts.MCP_COMMAND_MS) { runBlock() }
                }
            } else {
                withTimeout(BrowserBridgeTimeouts.MCP_COMMAND_MS) { runBlock() }
            }
        }.getOrElse { e ->
            val message = when (e) {
                is kotlinx.coroutines.TimeoutCancellationException ->
                    "The browser command timed out. The page may still be loading — try browser_health, then retry."
                else -> e.message ?: e.javaClass.simpleName
            }
            JSONObject().put("error", message)
        }.also { result ->
            running.decrementAndGet()
            syncCommandInFlight()
            lastCommand = System.currentTimeMillis()
            if (!result.has("error")) {
                bumpNetworkGeneration()
                if (action == "open") {
                    lastBrowserUrl = Preview.state.value.url
                }
                recordAction(labelFor(action, args))
            }
        }
    }

    private fun resolveRegistryKey(requiredKey: String?, args: JSONObject): String? {
        if (!requiredKey.isNullOrBlank() && BrowserSessionRegistry.get(requiredKey) != null) {
            return requiredKey
        }
        if (!codingSessionKey.isNullOrBlank()) {
            val activeCoding = PreviewUrlRouter.sessionKeyForCoding(codingSessionKey!!)
            if (BrowserSessionRegistry.get(activeCoding) != null) return activeCoding
        }
        val given = args.optString("session_key")
        if (given.isBlank()) return null
        if (BrowserSessionRegistry.get(given) != null) return given
        val coding = PreviewUrlRouter.sessionKeyForCoding(given)
        if (BrowserSessionRegistry.get(coding) != null) return coding
        return null
    }

    private fun labelFor(action: String, args: JSONObject): String = when (action) {
        "open" -> "Opening " + Preview.display(Preview.normalize(args.optString("url"), null))
        "click" -> "Clicking"
        "fill", "type" -> "Typing"
        "press" -> "Pressing " + args.optString("key")
        "scroll" -> "Scrolling"
        "screenshot" -> "Taking a screenshot"
        "snapshot", "text" -> "Reading the page"
        "wait" -> "Waiting"
        "select", "check" -> "Choosing"
        "hover" -> "Hovering"
        "upload" -> "Uploading a file"
        "console", "network" -> "Reading the console"
        "health" -> "Checking page health"
        "tab_list", "tab_new", "tab_switch", "tab_close" -> "Managing tabs"
        "dismiss_captcha" -> "Dismissing captcha hint"
        else -> "Working"
    }

    /** The label shown on the pointer and in the banner while a step runs. */
    fun setLabel(label: String) = _activity.update { it.copy(label = label) }

    private fun sameKey(given: String): Boolean =
        ::token.isInitialized && MessageDigest.isEqual(given.toByteArray(), token.toByteArray())

    private fun readHeaders(input: BufferedInputStream): Map<String, String>? {
        val lines = ArrayList<String>()
        val line = StringBuilder()
        while (true) {
            val c = input.read()
            if (c < 0) return null
            if (c == '\n'.code) {
                val text = line.toString().trimEnd('\r')
                if (text.isEmpty()) break
                lines += text
                line.setLength(0)
                if (lines.size > 64) return null
            } else {
                line.append(c.toChar())
                if (line.length > 8192) return null
            }
        }
        if (lines.isEmpty() || !lines[0].startsWith("POST ")) return null
        return lines.drop(1).mapNotNull { h ->
            val i = h.indexOf(':')
            if (i <= 0) null else h.substring(0, i).trim().lowercase() to h.substring(i + 1).trim()
        }.toMap()
    }

    private fun respond(socket: Socket, code: Int, body: JSONObject) {
        val bytes = body.toString().toByteArray(Charsets.UTF_8)
        val head = "HTTP/1.1 $code ${if (code == 200) "OK" else "Error"}\r\n" +
            "Content-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
        runCatching {
            socket.getOutputStream().apply {
                write(head.toByteArray())
                write(bytes)
                flush()
            }
        }
    }
}
