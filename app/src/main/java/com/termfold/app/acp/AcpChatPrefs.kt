package com.termfold.app.acp

import android.content.Context

/** User preferences for agent session chat presentation. */
object AcpChatPrefs {
    private const val PREFS = "acp_chat"
    private const val KEY_SHOW_LIVE_TOOL_DIFFS = "show_live_tool_diffs"
    private const val KEY_STICKY_USER_MESSAGES = "sticky_user_messages"

    fun showLiveToolDiffs(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_SHOW_LIVE_TOOL_DIFFS, true)

    fun setShowLiveToolDiffs(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SHOW_LIVE_TOOL_DIFFS, enabled)
            .apply()
    }

    fun stickyUserMessages(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_STICKY_USER_MESSAGES, true)

    fun setStickyUserMessages(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_STICKY_USER_MESSAGES, enabled)
            .apply()
    }
}
