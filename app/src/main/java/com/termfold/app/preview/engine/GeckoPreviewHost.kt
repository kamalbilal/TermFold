package com.termfold.app.preview.engine

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.mozilla.geckoview.GeckoView

/** GeckoView surface for in-app preview. */
class GeckoPreviewHost(
    appContext: Context,
    private val storageContextId: String,
    onTitle: ((String) -> Unit)? = null,
    onProgress: ((Int) -> Unit)? = null,
    onHistory: ((canBack: Boolean, canForward: Boolean) -> Unit)? = null,
) {
    val geckoView: GeckoView = GeckoView(appContext)
    private val sessionHost: GeckoBrowserSession

    val session get() = sessionHost.session
    val surface get() = sessionHost.surface
    val browserSession get() = sessionHost

    init {
        // TextureView composites through Compose clipping; SurfaceView stays blank in clipToBounds.
        geckoView.setViewBackend(GeckoView.BACKEND_TEXTURE_VIEW)
        geckoView.coverUntilFirstPaint(PreviewBrowserTheme.backgroundColor(GeckoRuntimeHolder.preferredDark))
        geckoView.setActivityContextDelegate { unwrapActivity(geckoView.context) }
        PreviewBrowserTheme.applyGeckoViewChrome(geckoView, GeckoRuntimeHolder.preferredDark)
        sessionHost = BrowserSessionRegistry.bindPreview(
            appContext,
            storageContextId,
            geckoView,
            onTitle,
            onProgress,
            onHistory,
        )
    }

    fun stopLoading() = sessionHost.stopLoading()

    fun loadUrl(url: String) = sessionHost.loadUrl(url)

    fun bindToDisplay() = sessionHost.bindToDisplay()

    fun release() {
        BrowserSessionRegistry.unbindPreview(storageContextId)
    }

    companion object {
        /**
         * Bind the session to a sized [GeckoView], then load. Loading before [GeckoView.setSession]
         * paints off-screen; attaching before the view has a compositor leaves a blank surface
         * until a manual reload.
         */
        fun loadUrlWhenReady(host: GeckoPreviewHost, url: String) {
            if (url.isBlank() || url == "about:blank") return
            val view = host.geckoView
            runWhenViewReady(view) {
                host.bindToDisplay()
                val current = host.surface.url()?.takeUnless { it == "about:blank" }
                if (current != url) {
                    host.loadUrl(url)
                }
                view.post {
                    view.requestLayout()
                    view.invalidate()
                }
            }
        }

        internal fun runWhenViewReady(view: View, block: () -> Unit) {
            fun sized() = view.isAttachedToWindow && view.width > 0 && view.height > 0
            fun runAfterSurface() {
                // Two frames: TextureView's hardware layer exists after the first draw.
                view.post { view.post(block) }
            }
            if (sized()) {
                runAfterSurface()
                return
            }
            val attachAndLayout = object : View.OnAttachStateChangeListener, ViewTreeObserver.OnGlobalLayoutListener {
                override fun onViewAttachedToWindow(v: View) {
                    tryRun()
                }

                override fun onViewDetachedFromWindow(v: View) {}

                override fun onGlobalLayout() {
                    tryRun()
                }

                private fun tryRun() {
                    if (!sized()) return
                    view.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    view.removeOnAttachStateChangeListener(this)
                    runAfterSurface()
                }
            }
            view.addOnAttachStateChangeListener(attachAndLayout)
            view.viewTreeObserver.addOnGlobalLayoutListener(attachAndLayout)
            view.post { attachAndLayout.onGlobalLayout() }
        }

        private fun unwrapActivity(context: Context): Activity? {
            var ctx: Context? = context
            while (ctx is ContextWrapper) {
                if (ctx is Activity) return ctx
                ctx = ctx.baseContext
            }
            return null
        }
    }
}

@Composable
fun GeckoPreviewAndroidView(
    host: GeckoPreviewHost,
    modifier: Modifier = Modifier,
    releaseOnDispose: Boolean = true,
) {
    if (releaseOnDispose) {
        DisposableEffect(host) {
            onDispose { host.release() }
        }
    }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            host.geckoView.apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setActivityContextDelegate {
                    var c: Context? = ctx
                    while (c is ContextWrapper) {
                        if (c is Activity) return@setActivityContextDelegate c
                        c = c.baseContext
                    }
                    null
                }
            }
        },
    )
}

@Composable
fun rememberGeckoPreviewHost(
    context: Context,
    storageContextId: String,
): GeckoPreviewHost = remember(storageContextId) {
    GeckoPreviewHost(context, storageContextId)
}
