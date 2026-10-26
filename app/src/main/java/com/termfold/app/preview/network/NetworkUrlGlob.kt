package com.termfold.app.preview.network

/** Chrome-like URL globs: `*` any run of characters, `?` one character. */
object NetworkUrlGlob {
    fun matches(pattern: String, url: String): Boolean {
        if (pattern.isEmpty()) return false
        return toRegex(pattern).matches(url)
    }

    fun toRegex(pattern: String): Regex {
        val sb = StringBuilder(pattern.length + 2)
        sb.append('^')
        for (c in pattern) {
            when (c) {
                '*' -> sb.append(".*")
                '?' -> sb.append('.')
                else -> sb.append(Regex.escape(c.toString()))
            }
        }
        sb.append('$')
        return Regex(sb.toString())
    }
}
