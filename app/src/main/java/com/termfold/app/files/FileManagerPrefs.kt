package com.termfold.app.files

import android.content.Context
import java.io.File

/** User choices for the built-in file manager (sort, layout, hidden files, last folder). */
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
    private const val KEY_PERSIST_LOCATION = "persist_location"
    private const val KEY_LAST_GUEST_PATH = "last_guest_path"

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

    fun persistLocation(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PERSIST_LOCATION, false)

    fun setPersistLocation(context: Context, persist: Boolean) {
        prefs(context).edit().putBoolean(KEY_PERSIST_LOCATION, persist).apply()
    }

    fun lastGuestPath(context: Context): String? =
        prefs(context).getString(KEY_LAST_GUEST_PATH, null)?.takeIf { it.isNotBlank() }

    fun setLastGuestPath(context: Context, guestPath: String) {
        prefs(context).edit().putString(KEY_LAST_GUEST_PATH, guestPath).apply()
    }

    /** Opening the Files tab: home, or the last folder when [persistLocation] is on. */
    fun resolveInitialGuestPath(context: Context, rootfs: File, default: String): String {
        if (!persistLocation(context)) return default
        val saved = lastGuestPath(context) ?: return default
        val guest = saved.ifBlank { "/" }
        val folder = File(rootfs, guest.trimStart('/'))
        return if (folder.isDirectory) guest else default
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
