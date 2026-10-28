package com.termfold.app.ui.theme

import androidx.compose.ui.graphics.Color

object GitDiffColors {
    val addBg = Color(0xFF1B2E1B)
    val addFg = Color(0xFF7EE787)
    /** Stronger fill on changed text within an added line (GitHub-style). */
    val addWordBg = Color(0xFF3FB950)
    val addWordFg = Color(0xFFE6FFED)
    val removeBg = Color(0xFF3E1A1A)
    val removeFg = Color(0xFFFF7B72)
    /** Stronger fill on changed text within a removed line (GitHub-style). */
    val removeWordBg = Color(0xFFDA3633)
    val removeWordFg = Color(0xFFFFECEC)
    val hunkHeaderBg = Color(0xFF1C2D41)
    val hunkHeaderFg = Color(0xFF8BB4E8)
    val gutterFg = Color(0xFF6E7681)
    val contextFg = Palette.TextDim
    val statusModified = Color(0xFFE5A50A)
}
