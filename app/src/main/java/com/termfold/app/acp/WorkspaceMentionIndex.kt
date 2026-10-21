package com.termfold.app.acp

import android.content.Context
import com.termfold.app.shell.Projects
import java.io.File

object WorkspaceMentionIndex {
    data class Entry(val relativePath: String, val isDirectory: Boolean)

    /** Basename for `@` mention and full path from the project root for the location column. */
    data class DisplayLines(val mentionName: String, val fullPathFromRoot: String)

    fun displayLines(relativePath: String): DisplayLines {
        val fullPathFromRoot = relativePath.replace('\\', '/').trim()
        if (fullPathFromRoot.isBlank()) return DisplayLines("", "")
        val slash = fullPathFromRoot.lastIndexOf('/')
        val mentionName = if (slash < 0) fullPathFromRoot else fullPathFromRoot.substring(slash + 1)
        return DisplayLines(mentionName, fullPathFromRoot)
    }

    private val skipDirNames = setOf(
        ".git",
        ".termfold",
        "node_modules",
        "build",
        "dist",
        ".gradle",
        ".idea",
    )

    fun search(context: Context, guestWorkspace: String, query: String, limit: Int = 40): List<Entry> {
        val root = Projects.hostPath(context, guestWorkspace)
        if (!root.isDirectory) return emptyList()
        return filterEntries(collectFromRoot(root), query, limit)
    }

    /** Workspace files only (no folders), for quick-open / Go to File. */
    fun searchFiles(context: Context, guestWorkspace: String, query: String, limit: Int = 40): List<Entry> {
        val root = Projects.hostPath(context, guestWorkspace)
        if (!root.isDirectory) return emptyList()
        val files = collectFromRoot(root).filter { !it.isDirectory }
        return filterEntries(files, query, limit)
    }

    internal fun collectFromRoot(root: File, maxDepth: Int = 5, maxEntries: Int = 800): List<Entry> {
        val collected = ArrayList<Entry>(maxEntries.coerceAtMost(800))
        walk(root, root, collected, maxDepth, maxEntries)
        return collected
    }

    internal fun filterEntries(entries: List<Entry>, query: String, limit: Int): List<Entry> {
        val q = query.trim()
        if (q.isEmpty()) {
            return entries
                .sortedWith(compareBy<Entry> { it.isDirectory }.thenBy { it.relativePath.lowercase() })
                .take(limit)
        }
        return ComposerFuzzyMatch.rank(
            items = entries.asSequence(),
            query = q,
            limit = limit,
            searchable = { entry ->
                val path = entry.relativePath.replace('\\', '/')
                val slash = path.lastIndexOf('/')
                val base = if (slash < 0) path else path.substring(slash + 1)
                listOf(base, path).joinToString("\n")
            },
        )
    }

    private fun walk(root: File, dir: File, out: MutableList<Entry>, maxDepth: Int, maxEntries: Int) {
        if (out.size >= maxEntries || maxDepth < 0) return
        val children = dir.listFiles()?.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }) ?: return
        for (child in children) {
            if (out.size >= maxEntries) return
            if (child.name.startsWith('.')) continue
            val relative = child.relativeTo(root).path.replace('\\', '/')
            if (relative.isBlank()) continue
            out.add(Entry(relative, child.isDirectory))
            if (child.isDirectory && child.name !in skipDirNames) {
                walk(root, child, out, maxDepth - 1, maxEntries)
            }
        }
    }
}
