package com.termfold.app.preview.engine

import android.content.Context
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import java.util.concurrent.atomic.AtomicReference

object GeckoRuntimeHolder {
    private val runtimeRef = AtomicReference<GeckoRuntime?>(null)

    @Volatile
    var preferredDark: Boolean = true

    fun getOrCreate(context: Context): GeckoRuntime {
        runtimeRef.get()?.let { return it }
        synchronized(this) {
            runtimeRef.get()?.let { return it }
            val settings = GeckoRuntimeSettings.Builder()
                .javaScriptEnabled(true)
                .preferredColorScheme(
                    if (preferredDark) {
                        GeckoRuntimeSettings.COLOR_SCHEME_DARK
                    } else {
                        GeckoRuntimeSettings.COLOR_SCHEME_LIGHT
                    },
                )
                .build()
            val runtime = GeckoRuntime.create(context.applicationContext, settings)
            GeckoTermfoldExtension.ensureBuiltIn(runtime)
            runtimeRef.set(runtime)
            return runtime
        }
    }

    fun setPreferredColorScheme(context: Context, dark: Boolean) {
        preferredDark = dark
        val runtime = runtimeRef.get() ?: getOrCreate(context)
        runtime.settings.setPreferredColorScheme(
            if (dark) {
                GeckoRuntimeSettings.COLOR_SCHEME_DARK
            } else {
                GeckoRuntimeSettings.COLOR_SCHEME_LIGHT
            },
        )
    }
}
