package com.termfold.app.ui.components

import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat

/**
 * Full-screen [androidx.compose.ui.window.Dialog] windows do not inherit the activity's
 * `adjustResize`; without this, [androidx.compose.foundation.layout.imePadding] stays zero and
 * the keyboard covers the terminal and key row.
 */
@Composable
fun ConfigureDialogWindowForKeyboard() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        window?.let { w ->
            WindowCompat.setDecorFitsSystemWindows(w, false)
            w.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED,
            )
        }
        onDispose { }
    }
}
