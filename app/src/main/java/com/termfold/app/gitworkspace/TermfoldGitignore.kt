package com.termfold.app.gitworkspace

import java.io.File

object TermfoldGitignore {
    private const val ENTRY = ".termfold/"

    fun ensureTermfoldIgnored(projectDir: File): Boolean {
        if (!projectDir.isDirectory) return false
        val gitignore = File(projectDir, ".gitignore")
        if (!gitignore.isFile) {
            gitignore.writeText("$ENTRY\n")
            return true
        }
        val lines = gitignore.readLines()
        if (lines.any { isTermfoldIgnoreLine(it) }) return false
        val suffix = if (lines.isEmpty() || lines.last().isBlank()) "" else "\n"
        gitignore.appendText("$suffix$ENTRY")
        return true
    }

    internal fun isTermfoldIgnoreLine(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith('#')) return false
        val body = trimmed.removePrefix("!").trim()
        return body == ".termfold" ||
            body == ".termfold/" ||
            body.endsWith("/.termfold") ||
            body.endsWith("/.termfold/") ||
            body.contains(".termfold/")
    }
}
