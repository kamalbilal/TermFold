package com.termfold.app.preview.engine

import android.content.Context
import android.os.Looper
import android.view.View
import android.webkit.WebView
import com.termfold.app.preview.BrowserBridge
import com.termfold.app.preview.BrowserDriver
import com.termfold.app.preview.DriverUi
import kotlinx.coroutines.sync.Mutex
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

enum class BrowserPowerTier {
    RUNNING,
    IDLE_WARM,
    IDLE_COLD,
}

data class BrowserSessionEntry(
    val key: String,
    val controller: BrowserBridge.Controller,
    val surface: BrowserSurface,
    val storageContextId: String,
    val mutex: Mutex = Mutex(),
    @Volatile var tier: BrowserPowerTier = BrowserPowerTier.IDLE_WARM,
    @Volatile var uiAttached: Boolean = false,
    val casual: Boolean = false,
    private val headlessTeardown: (() -> Unit)? = null,
) {
    fun teardown() {
        headlessTeardown?.invoke()
    }

    fun releaseHeadlessResources() {
        headlessTeardown?.invoke()
    }
}

/**
 * Keeps agent-bound browser sessions alive while automation or coding agents run,
 * independent of whether the preview pane is visible.
 */
object BrowserSessionRegistry {
    private const val MAX_WARM = 3
    private val entries = ConcurrentHashMap<String, BrowserSessionEntry>()
    private val inFlight = ConcurrentHashMap<String, Mutex>()

    fun get(key: String): BrowserSessionEntry? = entries[key]

    fun controllerFor(key: String?): BrowserBridge.Controller? {
        if (key.isNullOrBlank()) return null
        return entries[key]?.controller
    }

    suspend fun runExclusive(key: String, block: suspend () -> JSONObject): JSONObject {
        val entry = entries[key]
        if (entry == null) {
            return JSONObject().put("error", "No browser session registered for key \"$key\".")
        }
        val gate = inFlight.getOrPut(key) { Mutex() }
        if (!gate.tryLock()) {
            return JSONObject().put("error", "session_busy")
        }
        return try {
            entry.tier = BrowserPowerTier.RUNNING
            block()
        } finally {
            gate.unlock()
            entry.tier = BrowserPowerTier.IDLE_WARM
        }
    }

    fun ensureAutomation(
        context: Context,
        sessionKey: String,
        webViewSuffix: String,
    ): BrowserSessionEntry {
        checkMainThread()
        return entries.getOrPut(sessionKey) {
            createHeadlessEntry(context, sessionKey, storageContextId = sessionKey)
        }
    }

    fun ensureCoding(
        context: Context,
        sessionKey: String,
    ): BrowserSessionEntry {
        checkMainThread()
        val key = PreviewUrlRouter.sessionKeyForCoding(sessionKey)
        return entries.getOrPut(key) {
            createHeadlessEntry(context, key, storageContextId = key)
        }
    }

    fun release(key: String) {
        entries.remove(key)?.let { evict(it) }
        inFlight.remove(key)
    }

    fun releaseCoding(composeKey: String) {
        release(PreviewUrlRouter.sessionKeyForCoding(composeKey))
    }

    fun attachUi(key: String, surface: BrowserSurface, controller: BrowserBridge.Controller) {
        checkMainThread()
        val previous = entries[key]
        previous?.releaseHeadlessResources()
        entries[key] = BrowserSessionEntry(
            key = key,
            controller = controller,
            surface = surface,
            storageContextId = previous?.storageContextId ?: key,
            mutex = previous?.mutex ?: Mutex(),
            tier = previous?.tier ?: BrowserPowerTier.IDLE_WARM,
            uiAttached = true,
            casual = false,
            headlessTeardown = null,
        )
    }

    /** Preview went away: run agent commands on a headless Gecko session again (same profile). */
    fun restoreHeadless(context: Context, key: String) {
        checkMainThread()
        val previous = entries[key] ?: return
        if (!previous.uiAttached) return
        val url = previous.surface.url()
        val storageId = previous.storageContextId
        val fresh = createHeadlessEntry(
            context,
            key,
            storageContextId = storageId,
            initialUrl = url,
        )
        entries[key] = fresh
    }

    private fun createHeadlessEntry(
        context: Context,
        key: String,
        storageContextId: String,
        casual: Boolean = false,
        initialUrl: String? = null,
    ): BrowserSessionEntry {
        checkMainThread()
        evictIfNeeded()
        val app = context.applicationContext
        val gecko = GeckoBrowserSession(
            app,
            storageContextId,
            geckoView = null,
        )
        if (!initialUrl.isNullOrBlank() && initialUrl != "about:blank") {
            gecko.loadUrl(initialUrl)
        }
        val driverStub = WebView(app).apply {
            visibility = View.GONE
        }
        val stubUi = HeadlessGeckoDriverUi(gecko, driverStub)
        val driver = BrowserDriver(app, stubUi)
        val teardown = {
            gecko.release()
            driverStub.destroy()
        }
        return BrowserSessionEntry(
            key = key,
            controller = driver,
            surface = gecko.surface,
            storageContextId = storageContextId,
            casual = casual,
            headlessTeardown = teardown,
        )
    }

    private fun evictIfNeeded() {
        val warm = entries.values.filter { !it.casual && it.tier != BrowserPowerTier.IDLE_COLD }
        if (warm.size < MAX_WARM) return
        val victim = entries.values
            .filter { it.casual }
            .minByOrNull { if (it.tier == BrowserPowerTier.IDLE_WARM) 0 else 1 }
            ?: warm.filter { !it.uiAttached }.firstOrNull()
        victim?.let {
            evict(it)
            entries.remove(it.key)
        }
    }

    private fun evict(entry: BrowserSessionEntry) {
        entry.tier = BrowserPowerTier.IDLE_COLD
        entry.teardown()
    }

    private fun checkMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "BrowserSessionRegistry must run on the main thread"
        }
    }

    /** Minimal [DriverUi] for headless Gecko registry sessions. */
    private class HeadlessGeckoDriverUi(
        private val gecko: GeckoBrowserSession,
        private val driverStub: WebView,
    ) : DriverUi {
        @Volatile private var viewportName: String = "fit"

        override fun activeWebView(): WebView = driverStub
        override fun browserSurface(): BrowserSurface = gecko.surface
        override fun projectDir(): String? = null
        override fun navigate(url: String) {
            gecko.loadUrl(url)
        }

        override fun navigateNewTab(url: String): Int? {
            navigate(url)
            return 1
        }

        override fun progress(): Int = gecko.progress
        override fun loadError(): String? = gecko.loadError
        override fun httpError(): String? = null
        override fun setViewport(mode: String): Boolean {
            val ok = mode.equals("fit", true) || mode.equals("phone", true) || mode.equals("desktop", true)
            if (!ok) return false
            viewportName = mode.lowercase()
            kotlinx.coroutines.runBlocking {
                GeckoViewport.apply(gecko.surface, mode)
            }
            return true
        }

        override fun viewportName(): String = viewportName
        override suspend fun moveCursor(x: Float, y: Float, label: String) {}
        override fun pulse() {}
        override fun consoleSize(): Int = 0
        override fun consoleText(errorsOnly: Boolean, since: Int): String =
            if (errorsOnly) "No console errors." else "The console is empty."

        override fun consoleErrorsSince(since: Int): Int = 0
        override fun networkText(failedOnly: Boolean, sinceGeneration: Int): String =
            if (failedOnly) "No failed requests." else "No requests recorded yet."

        override fun networkInFlight(): Int = if (gecko.progress < 100) 1 else 0
        override fun networkGeneration(): Int = BrowserBridge.networkGeneration
        override fun failedRequestCount(): Int = 0
        override fun visibleControlCount(): Int = 0
        override fun setVisibleControls(count: Int) {}
        override fun offerUpload(file: java.io.File) {}
        override fun uploadTaken(): Boolean = false
        override fun captchaPending(): Boolean = false
        override fun updateCaptchaFromPage(detected: Boolean) {}
        override fun dismissCaptchaFalsePositive() {}
        override fun clearSiteData() {
            gecko.surface.reload()
        }

        override fun tabList(): JSONArray = JSONArray().put(
            JSONObject()
                .put("id", 1)
                .put("title", "")
                .put("url", gecko.surface.url().orEmpty())
                .put("active", true),
        )

        override fun tabSwitch(id: Int): Boolean = id == 1
        override fun tabNew(url: String?): Int? {
            if (url != null) navigate(url)
            return 1
        }

        override fun tabClose(id: Int): Boolean = false
        override fun ensureResumed() {
            gecko.surface.onResume()
        }
    }
}
