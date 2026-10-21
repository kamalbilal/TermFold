package com.termfold.app.acp

/**
 * Detects an in-progress `@path` or `/command` token at the text caret (including mid-sentence).
 */
object ComposerTriggers {
    data class ActiveToken(
        val start: Int,
        val end: Int,
        val trigger: Char,
        val query: String,
    ) {
        val dismissKey: String = "$start:$trigger$query"
    }

    fun activeToken(text: String, selectionStart: Int, selectionEnd: Int): ActiveToken? {
        if (text.isEmpty()) return null
        val caret = selectionStart.coerceIn(0, text.length)
        val other = selectionEnd.coerceIn(0, text.length)
        val anchor = if (caret == other) caret else minOf(caret, other)

        var start = anchor
        while (start > 0 && !text[start - 1].isWhitespace()) start--
        var end = anchor
        while (end < text.length && !text[end].isWhitespace()) end++
        if (start >= end) return null

        val token = text.substring(start, end)
        val trigger = token.firstOrNull() ?: return null
        if (trigger != '@' && trigger != '/') return null
        val query = if (token.length <= 1) "" else token.substring(1)
        if (query.any { it.isWhitespace() }) return null
        return ActiveToken(start, end, trigger, query)
    }
}
