package com.termfold.app.ui.screens

import android.content.Context
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.webkit.WebView

/** Modifier latch state shared between the accessory row and the editor WebView IME path. */
internal class EditorModifierState {
    var controlDown: Boolean = false
    var shiftDown: Boolean = false
    var altDown: Boolean = false
    /** While the page is applying a key programmatically, do not re-enter [commitText] routing. */
    var imeRoutingSuspended: Boolean = false

    val interceptsIme: Boolean
        get() = !imeRoutingSuspended && (controlDown || shiftDown || altDown)
}

/**
 * Routes soft-keyboard commits through [onImeKey] when accessory modifiers are latched.
 * Without this, toggling Ctrl/Shift/Alt only affects accessory buttons, not IME typing.
 */
internal class EditorWebView(
    context: Context,
    val modifiers: EditorModifierState,
) : WebView(context) {

    var onImeKey: ((logicalKey: String) -> Unit)? = null

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val base = super.onCreateInputConnection(outAttrs) ?: return null
        if (onImeKey == null) return base
        return object : InputConnectionWrapper(base, true) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                if (!modifiers.interceptsIme) {
                    return super.commitText(text, newCursorPosition)
                }
                val s = text?.toString() ?: return super.commitText(text, newCursorPosition)
                if (s == "\n") {
                    post { onImeKey?.invoke("Enter") }
                    return true
                }
                if (s.length == 1) {
                    post { onImeKey?.invoke(s) }
                    return true
                }
                return super.commitText(text, newCursorPosition)
            }
        }
    }
}
