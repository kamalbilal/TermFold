package com.termfold.app.preview.engine

import android.content.Context
import org.mozilla.geckoview.GeckoView

/** In-browser page color preference (prefers-color-scheme), not the TermFold app theme. */
object PreviewBrowserTheme {
    const val DARK_BACKGROUND = 0xFF08080A.toInt()
    const val LIGHT_BACKGROUND = 0xFFFFFFFF.toInt()

    fun apply(context: Context, dark: Boolean) {
        GeckoRuntimeHolder.setPreferredColorScheme(context, dark)
    }

    fun backgroundColor(dark: Boolean): Int = if (dark) DARK_BACKGROUND else LIGHT_BACKGROUND

    fun applyGeckoViewChrome(view: GeckoView?, dark: Boolean) {
        view?.setBackgroundColor(backgroundColor(dark))
    }
}
