package com.termfold.app.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorModifierStateTest {
    @Test
    fun interceptsImeWhenAnyModifierLatched() {
        val state = EditorModifierState()
        assertFalse(state.interceptsIme)
        state.shiftDown = true
        assertTrue(state.interceptsIme)
        state.shiftDown = false
        state.controlDown = true
        assertTrue(state.interceptsIme)
        state.controlDown = false
        state.altDown = true
        assertTrue(state.interceptsIme)
    }

    @Test
    fun suspendBlocksImeIntercept() {
        val state = EditorModifierState()
        state.controlDown = true
        assertTrue(state.interceptsIme)
        state.imeRoutingSuspended = true
        assertFalse(state.interceptsIme)
    }
}
