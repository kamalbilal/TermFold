package com.termfold.app.gitworkspace

internal fun parsePorcelain(lines: List<String>): Pair<List<PorcelainEntry>, List<PorcelainEntry>> {
    val staged = mutableListOf<PorcelainEntry>()
    val unstaged = mutableListOf<PorcelainEntry>()
    for (line in lines) {
        if (line.length < 3) continue
        val x = line[0]
        val y = line[1]
        if (x == ' ' && y == ' ') continue
        var path = line.substring(3).trim()
        if (path.startsWith("\"") && path.endsWith("\"")) {
            path = path.substring(1, path.length - 1).replace("\\\"", "\"")
        }
        if (path.contains(" -> ")) {
            path = path.substringAfter(" -> ").trim()
        }
        if (path.isBlank()) continue
        val entry = PorcelainEntry(path, x, y)
        if (x != ' ' && x != '?') {
            staged += entry
        }
        if (y != ' ' || (x == '?' && y == '?')) {
            unstaged += entry
        }
    }
    return staged to unstaged
}

internal data class PorcelainEntry(val path: String, val index: Char, val workTree: Char)

internal fun statusFromPorcelain(index: Char, workTree: Char, staged: Boolean): GitFileStatus {
    val c = if (staged) index else workTree
    return when (c) {
        'A' -> GitFileStatus.ADDED
        'M' -> GitFileStatus.MODIFIED
        'D' -> GitFileStatus.DELETED
        'R' -> GitFileStatus.RENAMED
        'C' -> GitFileStatus.COPIED
        '?' -> GitFileStatus.UNTRACKED
        'U' -> GitFileStatus.UNMERGED
        else -> GitFileStatus.MODIFIED
    }
}

internal fun parseNumstat(lines: List<String>): Map<String, Pair<Int, Int>> {
    val map = HashMap<String, Pair<Int, Int>>()
    for (line in lines) {
        if (line.isBlank()) continue
        val parts = line.split('\t')
        if (parts.size < 3) continue
        val add = parts[0].toIntOrNull() ?: if (parts[0] == "-") null else 0
        val del = parts[1].toIntOrNull() ?: if (parts[1] == "-") null else 0
        val path = parts[2]
        val additions = add ?: 0
        val deletions = del ?: 0
        map[path] = additions to deletions
    }
    return map
}

/** True when [path] is a directory row from `git status` (e.g. `scripts/`). */
internal fun isDirectoryDiffPath(path: String): Boolean = path.endsWith("/")

/** Pathspec for listing every file under a directory in git commands. */
internal fun directoryPathspec(path: String): String {
    val trimmed = path.trimEnd('/')
    return if (trimmed.isEmpty()) "." else "$trimmed/"
}

internal fun parseGitNameOnlyOutput(output: String): List<String> =
    output.lines().map { it.trim() }.filter { it.isNotBlank() }

internal fun buildChangeFiles(
    entries: List<PorcelainEntry>,
    staged: Boolean,
    numstat: Map<String, Pair<Int, Int>>,
): List<GitChangeFile> =
    entries.map { entry ->
        val stats = numstat[entry.path]
        GitChangeFile(
            path = entry.path,
            status = statusFromPorcelain(entry.index, entry.workTree, staged),
            additions = stats?.first,
            deletions = stats?.second,
            staged = staged,
        )
    }
