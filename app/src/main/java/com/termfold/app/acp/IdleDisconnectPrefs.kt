package com.termfold.app.acp

import android.content.Context

/** User preferences for disconnecting idle ACP agents in the background. */
object IdleDisconnectPrefs {
    private const val PREFS = "acp_idle_disconnect"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_TIMEOUT_MINUTES = "timeout_minutes"

    const val DEFAULT_ENABLED = true
    const val DEFAULT_TIMEOUT_MINUTES = 10
    const val MIN_TIMEOUT_MINUTES = 1
    const val MAX_TIMEOUT_MINUTES = 120

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, DEFAULT_ENABLED)

    fun setEnabled(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, value)
            .apply()
    }

    fun timeoutMinutes(context: Context): Int =
        clampMinutes(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_TIMEOUT_MINUTES, DEFAULT_TIMEOUT_MINUTES),
        )

    fun setTimeoutMinutes(context: Context, minutes: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_TIMEOUT_MINUTES, clampMinutes(minutes))
            .apply()
    }

    fun timeoutMs(context: Context): Long = timeoutMinutes(context).toLong() * 60_000L

    fun clampMinutes(minutes: Int): Int = minutes.coerceIn(MIN_TIMEOUT_MINUTES, MAX_TIMEOUT_MINUTES)
}
