package com.termfold.app.preview.engine

import android.content.Context
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView

/**
 * One isolated Gecko profile ([storageContextId]) for preview or headless agent work.
 * The same instance is reused when the preview GeckoView attaches so we never open two
 * sessions on one profile (that froze the compositor and crashed the app).
 */
class GeckoBrowserSession(
    private val appContext: Context,
    val storageContextId: String,
    geckoView: GeckoView? = null,
    onTitle: ((String) -> Unit)? = null,
    onProgress: ((Int) -> Unit)? = null,
    onHistory: ((canBack: Boolean, canForward: Boolean) -> Unit)? = null,
    onLoadError: ((String?) -> Unit)? = null,
) {
    val session: GeckoSession
    val surface: GeckoBrowserSurface
    private var currentUrl: String? = null
    @Volatile private var canGoBack = false
    @Volatile private var canGoForward = false
    @Volatile var progress: Int = 100
    private val loadProgress = GeckoLoadProgress()
    @Volatile var loadError: String? = null
    @Volatile private var displayBound = false
    @Volatile private var closed = false
    private var view: GeckoView? = geckoView
    private var onTitle: ((String) -> Unit)? = onTitle
    private var onProgress: ((Int) -> Unit)? = onProgress
    private var onHistory: ((canBack: Boolean, canForward: Boolean) -> Unit)? = onHistory
    private var onLoadError: ((String?) -> Unit)? = onLoadError

    init {
        val runtime = GeckoRuntimeHolder.getOrCreate(appContext)
        val settings = GeckoSessionSettings.Builder()
            .contextId(storageContextId)
            .build()
        session = GeckoSession(settings)
        session.open(runtime)
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                loadProgress.onPageStart(url)
                progress = loadProgress.value
                this@GeckoBrowserSession.onProgress?.invoke(progress)
                if (url.isNotBlank() && url != "about:blank") {
                    com.termfold.app.preview.network.NetworkCaptureStore.onMainFrameNavigation(storageContextId)
                }
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                progress = loadProgress.onPageStop()
                this@GeckoBrowserSession.onProgress?.invoke(progress)
                com.termfold.app.preview.network.NetworkRulesHub.bindSession(storageContextId)
                if (!success) {
                    loadError = loadError ?: "Page load failed"
                    this@GeckoBrowserSession.onLoadError?.invoke(loadError)
                }
            }

            override fun onProgressChange(session: GeckoSession, geckoProgress: Int) {
                progress = loadProgress.onProgressChange(geckoProgress)
                this@GeckoBrowserSession.onProgress?.invoke(progress)
            }
        }
        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                if (!title.isNullOrBlank()) this@GeckoBrowserSession.onTitle?.invoke(title)
            }
        }
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                permissions: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean,
            ) {
                currentUrl = url
                if (url != null && url != "about:blank") {
                    loadError = null
                    this@GeckoBrowserSession.onLoadError?.invoke(null)
                    com.termfold.app.preview.GeckoConsoleLog.clear(storageContextId)
                }
            }
        }
        session.historyDelegate = object : GeckoSession.HistoryDelegate {
            override fun onHistoryStateChange(
                session: GeckoSession,
                historyList: GeckoSession.HistoryDelegate.HistoryList,
            ) {
                val index = historyList.currentIndex
                canGoBack = index > 0
                canGoForward = index >= 0 && index < historyList.size - 1
                this@GeckoBrowserSession.onHistory?.invoke(canGoBack, canGoForward)
            }
        }
        // UI sessions bind in bindToDisplay() once GeckoView is on screen; binding too early paints blank.
        surface = GeckoBrowserSurface(
            session,
            geckoView,
            { currentUrl },
            { canGoBack to canGoForward },
            storageContextId,
            onBeforeDraw = { bindToDisplay() },
        )
        GeckoTermfoldExtension.registerSession(session)
        com.termfold.app.preview.network.NetworkRulesHub.bindSession(storageContextId)
        com.termfold.app.preview.network.NetworkRulesHub.pushToExtension()
    }

    fun setUiCallbacks(
        onTitle: ((String) -> Unit)? = null,
        onProgress: ((Int) -> Unit)? = null,
        onHistory: ((canBack: Boolean, canForward: Boolean) -> Unit)? = null,
        onLoadError: ((String?) -> Unit)? = null,
    ) {
        this.onTitle = onTitle
        this.onProgress = onProgress
        this.onHistory = onHistory
        this.onLoadError = onLoadError
    }

    fun clearUiCallbacks() = setUiCallbacks()

    /** Point this session at a preview [GeckoView] without opening a second engine. */
    fun attachGeckoView(view: GeckoView) {
        if (closed) return
        if (this.view === view) {
            surface.bindView(view)
            return
        }
        runCatching { this.view?.releaseSession() }
        displayBound = false
        this.view = view
        surface.bindView(view)
    }

    fun detachGeckoView() {
        runCatching { view?.releaseSession() }
        displayBound = false
        view = null
        surface.bindView(null)
    }

    /** Attach session to [GeckoView] after it is in the window; safe to call repeatedly. */
    fun bindToDisplay() {
        if (closed) return
        val view = view ?: return
        if (displayBound) return
        view.setSession(session)
        displayBound = true
    }

    fun stopLoading() {
        if (!closed) session.stop()
    }

    fun loadUrl(url: String) {
        if (closed) return
        currentUrl = url
        loadError = null
        loadProgress.onNavigate()
        progress = loadProgress.value
        onProgress?.invoke(progress)
        val projectFile = com.termfold.app.preview.PreviewFiles.bytesForUrl(appContext, url)
        if (projectFile != null) {
            session.load(
                GeckoSession.Loader()
                    .uri(url)
                    .data(projectFile.first, projectFile.second),
            )
        } else {
            session.loadUri(url)
        }
        com.termfold.app.preview.network.NetworkRulesHub.bindSession(storageContextId)
    }

    fun release() {
        if (closed) return
        closed = true
        runCatching {
            GeckoTermfoldExtension.unregisterSession(session)
            view?.releaseSession()
            displayBound = false
            view = null
            surface.bindView(null)
            session.close()
        }
    }
}
