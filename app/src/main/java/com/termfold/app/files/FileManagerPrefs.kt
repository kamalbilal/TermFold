package com.termfold.app.files

import android.content.Context

/** User choices for the built-in file manager (sort, layout, hidden files). */
enum class FileSortBy {
    NAME,
    MODIFIED,
    SIZE,
}

/**
 * Persists file-manager display options across tab switches and app restarts.
 *
 * [androidx.compose.runtime.saveable.rememberSaveable] does not survive [androidx.compose.animation.AnimatedContent]
 * tab changes in [com.termfold.app.MainActivity], so these live in SharedPreferences instead.
 */
object FileManagerPrefs {

    private const val PREFS = "file_manager"
    private const val KEY_SORT = "sort"
    private const val KEY_GRID = "grid"
    private const val KEY_SHOW_HIDDEN = "show_hidden"

    fun sort(context: Context): FileSortBy {
        val raw = prefs(context).getString(KEY_SORT, FileSortBy.NAME.name) ?: FileSortBy.NAME.name
        return runCatching { FileSortBy.valueOf(raw) }.getOrDefault(FileSortBy.NAME)
    }

    fun setSort(context: Context, sort: FileSortBy) {
        prefs(context).edit().putString(KEY_SORT, sort.name).apply()
    }

    fun gridView(context: Context): Boolean = prefs(context).getBoolean(KEY_GRID, false)

    fun setGridView(context: Context, grid: Boolean) {
        prefs(context).edit().putBoolean(KEY_GRID, grid).apply()
    }

    fun showHidden(context: Context): Boolean = prefs(context).getBoolean(KEY_SHOW_HIDDEN, false)

    fun setShowHidden(context: Context, show: Boolean) {
        prefs(context).edit().putBoolean(KEY_SHOW_HIDDEN, show).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
