package com.termfold.app.notify

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager

/**
 * Whether the display is on. Linux child processes are more likely to be frozen or trimmed when
 * the screen is off, even if a TermFold activity is still in the recents stack.
 */
object ScreenState {

    @Volatile
    var interactive: Boolean = true

    fun register(app: Application) {
        val power = app.getSystemService(Context.POWER_SERVICE) as PowerManager
        interactive = power.isInteractive
        app.registerReceiver(
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    when (intent.action) {
                        Intent.ACTION_SCREEN_ON -> interactive = true
                        Intent.ACTION_SCREEN_OFF -> interactive = false
                    }
                    SessionWatcher.onDisplayChanged(context.applicationContext)
                }
            },
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            },
        )
    }
}
