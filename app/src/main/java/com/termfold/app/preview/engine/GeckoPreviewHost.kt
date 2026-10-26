package com.termfold.app.preview.engine

import android.content.Context
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.mozilla.geckoview.GeckoView

/** GeckoView surface for in-app preview. */
class GeckoPreviewHost(
    appContext: Context,
    storageContextId: String,
    onTitle: ((String) -> Unit)? = null,
    onProgress: ((Int) -> Unit)? = null,
    onHistory: ((canBack: Boolean, canForward: Boolean) -> Unit)? = null,
) {
    val geckoView: GeckoView = GeckoView(appContext)
    private val sessionHost: GeckoBrowserSession

    val session get() = sessionHost.session
    val surface get() = sessionHost.surface

    init {
        PreviewBrowserTheme.applyGeckoViewChrome(geckoView, GeckoRuntimeHolder.preferredDark)
        sessionHost = GeckoBrowserSession(
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

    fun release() = sessionHost.release()
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
