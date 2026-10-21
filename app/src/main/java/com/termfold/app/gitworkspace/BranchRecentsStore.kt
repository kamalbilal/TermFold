package com.termfold.app.gitworkspace

import android.content.Context

/** Recently checked-out branch names per workspace guest path. */
object BranchRecentsStore {
    private const val PREFS = "git_workspace_branches"
    private const val MAX = 10

    fun load(context: Context, guestPath: String): List<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(key(guestPath), "")
            .orEmpty()
            .split('\n')
            .filter { it.isNotBlank() }

    fun remember(context: Context, guestPath: String, branch: String) {
        val trimmed = branch.trim()
        if (trimmed.isBlank()) return
        val list = (listOf(trimmed) + load(context, guestPath).filter { it != trimmed }).take(MAX)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(key(guestPath), list.joinToString("\n"))
            .apply()
    }

    private fun key(guestPath: String) = "recent_${guestPath.trimEnd('/')}"
}
