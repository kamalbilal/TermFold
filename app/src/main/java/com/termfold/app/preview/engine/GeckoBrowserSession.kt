package com.termfold.app.preview.engine

import android.content.Context
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView

/**
 * One isolated Gecko profile ([storageContextId]) for preview or headless agent work.
 * [geckoView] is null for registry-only sessions (automation/coding without UI).
 */
class GeckoBrowserSession(
    private val appContext: Context,
    val storageContextId: String,
    val geckoView: GeckoView? = null,
    private val onTitle: ((String) -> Unit)? = null,
    private val onProgress: ((Int) -> Unit)? = null,
    private val onHistory: ((canBack: Boolean, canForward: Boolean) -> Unit)? = null,
    private val onLoadError: ((String?) -> Unit)? = null,
) {
    val session: GeckoSession
    val surface: GeckoBrowserSurface
    private var currentUrl: String? = null
    @Volatile private var canGoBack = false
    @Volatile private var canGoForward = false
    @Volatile var progress: Int = 100
    @Volatile var loadError: String? = null
    @Volatile private var displayBound = false

    init {
        val runtime = GeckoRuntimeHolder.getOrCreate(appContext)
        val settings = GeckoSessionSettings.Builder()
            .contextId(storageContextId)
            .build()
        session = GeckoSession(settings)
        session.open(runtime)
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                if (url.isNotBlank() && url != "about:blank") {
                    com.termfold.app.preview.network.NetworkCaptureStore.onMainFrameNavigation(storageContextId)
                }
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                progress = 100
                onProgress?.invoke(100)
                com.termfold.app.preview.network.NetworkRulesHub.bindSession(storageContextId)
                if (!success) {
                    loadError = loadError ?: "Page load failed"
                    onLoadError?.invoke(loadError)
                }
            }

            override fun onProgressChange(session: GeckoSession, progress: Int) {
                this@GeckoBrowserSession.progress = progress
                onProgress?.invoke(progress)
            }
        }
        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                if (!title.isNullOrBlank()) onTitle?.invoke(title)
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
                    onLoadError?.invoke(null)
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
                onHistory?.invoke(canGoBack, canGoForward)
            }
        }
        // UI sessions bind in bindToDisplay() once GeckoView is on screen; binding too early paints blank.
        surface = GeckoBrowserSurface(session, geckoView, { currentUrl }, { canGoBack to canGoForward }, storageContextId)
        GeckoTermfoldExtension.registerSession(session)
        com.termfold.app.preview.network.NetworkRulesHub.bindSession(storageContextId)
        com.termfold.app.preview.network.NetworkRulesHub.pushToExtension()
    }

    /** Attach session to [GeckoView] after it is in the window; safe to call repeatedly. */
    fun bindToDisplay() {
        val view = geckoView ?: return
        if (displayBound) return
        view.setSession(session)
        displayBound = true
    }

    fun stopLoading() {
        session.stop()
    }

    fun loadUrl(url: String) {
        currentUrl = url
        loadError = null
        progress = 0
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
        GeckoTermfoldExtension.unregisterSession(session)
        geckoView?.releaseSession()
        displayBound = false
        session.close()
    }
}
