package com.termfold.app.preview.engine

import android.content.ComponentCallbacks2
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.WebView
import com.termfold.app.preview.BrowserBridge
import com.termfold.app.preview.BrowserDriver
import com.termfold.app.preview.DriverUi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull
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
    val gecko: GeckoBrowserSession? = null,
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
    private const val MAX_WARM = 2
    private val entries = ConcurrentHashMap<String, BrowserSessionEntry>()
    private val inFlight = ConcurrentHashMap<String, Mutex>()

    fun get(key: String): BrowserSessionEntry? = entries[key]

    fun isUiAttached(key: String): Boolean = entries[key]?.uiAttached == true

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
        val locked = withTimeoutOrNull(BrowserBridgeTimeouts.EXCLUSIVE_WAIT_MS) {
            gate.lock()
            true
        } == true
        if (!locked) {
            return JSONObject().put(
                "error",
                "The browser is still finishing the previous command. Wait a few seconds and try again.",
            )
        }
        return try {
            entries[key]?.tier = BrowserPowerTier.RUNNING
            block()
        } finally {
            gate.unlock()
            entries[key]?.tier = BrowserPowerTier.IDLE_WARM
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

    /** Drop idle headless Gecko sessions when the system is low on RAM. */
    fun onTrimMemory(level: Int) {
        if (level < ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) return
        val work = Runnable { trimMemoryOnMain(level) }
        if (Looper.myLooper() == Looper.getMainLooper()) work.run()
        else Handler(Looper.getMainLooper()).post(work)
    }

    private fun trimMemoryOnMain(level: Int) {
        checkMainThread()
        val keys = entries.keys.toList()
        for (key in keys) {
            val entry = entries[key] ?: continue
            if (isBusy(key)) continue
            val evictCasual = entry.casual
            val evictIdleHeadless =
                level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL &&
                    !entry.uiAttached &&
                    entry.tier == BrowserPowerTier.IDLE_WARM
            if (evictCasual || evictIdleHeadless) {
                evict(entry)
                entries.remove(key)
                inFlight.remove(key)
            }
        }
    }

    fun releaseCoding(composeKey: String) {
        release(PreviewUrlRouter.sessionKeyForCoding(composeKey))
    }

    fun geckoFor(storageContextId: String): GeckoBrowserSession? = entries[storageContextId]?.gecko

    /**
     * Bind the registry's single Gecko session to the preview view. Never opens a second
     * engine for the same profile.
     */
    fun bindPreview(
        context: Context,
        storageContextId: String,
        geckoView: org.mozilla.geckoview.GeckoView,
        onTitle: ((String) -> Unit)?,
        onProgress: ((Int) -> Unit)?,
        onHistory: ((canBack: Boolean, canForward: Boolean) -> Unit)?,
    ): GeckoBrowserSession {
        checkMainThread()
        val entry = entries.getOrPut(storageContextId) {
            createHeadlessEntry(context, storageContextId, storageContextId)
        }
        val gecko = entry.gecko ?: run {
            val created = GeckoBrowserSession(context.applicationContext, storageContextId)
            entries[storageContextId] = entry.copy(gecko = created, surface = created.surface)
            created
        }
        gecko.setUiCallbacks(onTitle = onTitle, onProgress = onProgress, onHistory = onHistory)
        gecko.attachGeckoView(geckoView)
        return gecko
    }

    fun unbindPreview(storageContextId: String) {
        checkMainThread()
        val entry = entries[storageContextId] ?: return
        entry.gecko?.clearUiCallbacks()
        entry.gecko?.detachGeckoView()
    }

    private fun isBusy(key: String): Boolean {
        val entry = entries[key] ?: return false
        return entry.tier == BrowserPowerTier.RUNNING || (inFlight[key]?.isLocked == true)
    }

    fun attachUi(key: String, surface: BrowserSurface, controller: BrowserBridge.Controller) {
        checkMainThread()
        val previous = entries[key]
        if (previous?.uiAttached == true) {
            entries[key] = previous.copy(
                controller = controller,
                surface = surface,
                uiAttached = true,
            )
            return
        }
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
            gecko = previous?.gecko,
            headlessTeardown = null,
        )
    }

    /** Preview pane left composition: keep the same Gecko session, drop only the view. */
    fun restoreHeadless(context: Context, key: String) {
        checkMainThread()
        val previous = entries[key] ?: return
        val gecko = previous.gecko
        previous.gecko?.clearUiCallbacks()
        if (previous.uiAttached) {
            previous.gecko?.detachGeckoView()
        } else if ((gecko?.surface?.width() ?: 0) > 0) {
            return
        }
        if (gecko == null) {
            entries[key] = createHeadlessEntry(
                context,
                key,
                storageContextId = previous.storageContextId,
                initialUrl = previous.surface.url(),
            )
            return
        }
        val app = context.applicationContext
        val offscreen = bindHeadlessGeckoView(app, gecko)
        val driverStub = WebView(app).apply { visibility = View.GONE }
        val driver = BrowserDriver(app, HeadlessGeckoDriverUi(gecko, driverStub))
        val headlessTeardown: () -> Unit = {
            runCatching { driverStub.destroy() }
            runCatching { offscreen.releaseSession() }
        }
        entries[key] = previous.copy(
            controller = driver,
            surface = gecko.surface,
            uiAttached = false,
            gecko = gecko,
            headlessTeardown = headlessTeardown,
        )
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
        val offscreen = bindHeadlessGeckoView(app, gecko)
        if (!initialUrl.isNullOrBlank() && initialUrl != "about:blank") {
            gecko.loadUrl(initialUrl)
        }
        val driverStub = WebView(app).apply {
            visibility = View.GONE
        }
        val stubUi = HeadlessGeckoDriverUi(gecko, driverStub)
        val driver = BrowserDriver(app, stubUi)
        return BrowserSessionEntry(
            key = key,
            controller = driver,
            surface = gecko.surface,
            storageContextId = storageContextId,
            casual = casual,
            gecko = gecko,
            headlessTeardown = {
                runCatching { driverStub.destroy() }
                runCatching { offscreen.releaseSession() }
                Unit
            },
        )
    }

    private fun newOffscreenGeckoView(context: Context): org.mozilla.geckoview.GeckoView =
        org.mozilla.geckoview.GeckoView(context.applicationContext).apply {
            visibility = View.GONE
            val w = View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY)
            val h = View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY)
            measure(w, h)
            layout(0, 0, 1280, 720)
        }

    private fun bindHeadlessGeckoView(context: Context, gecko: GeckoBrowserSession): org.mozilla.geckoview.GeckoView {
        val offscreen = newOffscreenGeckoView(context)
        gecko.attachGeckoView(offscreen)
        gecko.bindToDisplay()
        return offscreen
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
        com.termfold.app.preview.network.NetworkCaptureStore.clear(entry.storageContextId)
        com.termfold.app.preview.network.NetworkCaptureHub.setUiSubscribed(entry.storageContextId, false)
        com.termfold.app.preview.network.NetworkCaptureHub.pushCaptureOff(entry.storageContextId)
        runCatching { entry.gecko?.release() }
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
        @Volatile private var visibleControls: Int = 0

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
            return true
        }

        override fun viewportName(): String = viewportName
        override suspend fun moveCursor(x: Float, y: Float, label: String) {}
        override fun pulse() {}
        override fun consoleSize(): Int = com.termfold.app.preview.GeckoConsoleLog.marker(gecko.storageContextId).toInt()

        override fun consoleText(errorsOnly: Boolean, since: Int): String =
            com.termfold.app.preview.GeckoConsoleLog.text(gecko.storageContextId, errorsOnly, since.toLong())

        override fun consoleErrorsSince(since: Int): Int =
            com.termfold.app.preview.GeckoConsoleLog.errorsSince(gecko.storageContextId, since.toLong())

        override fun appendConsole(level: String, text: String, where: String) {
            com.termfold.app.preview.GeckoConsoleLog.record(gecko.storageContextId, level, text, where)
        }
        override fun networkText(failedOnly: Boolean, sinceGeneration: Int): String =
            com.termfold.app.preview.network.GeckoNetworkLog.text(
                gecko.storageContextId,
                failedOnly,
                sinceGeneration,
            )

        override fun networkInFlight(): Int = if (gecko.progress < 100) 1 else 0
        override fun networkGeneration(): Int = BrowserBridge.networkGeneration
        override fun failedRequestCount(): Int =
            com.termfold.app.preview.network.GeckoNetworkLog.failedCount(gecko.storageContextId)
        override fun visibleControlCount(): Int = visibleControls
        override fun setVisibleControls(count: Int) {
            visibleControls = count.coerceAtLeast(0)
        }
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
            gecko.bindToDisplay()
        }
    }
}
