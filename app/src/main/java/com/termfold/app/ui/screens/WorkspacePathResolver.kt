package com.termfold.app.ui.screens

import android.content.Context
import com.termfold.app.shell.Projects
import java.io.File

/**
 * Turns path strings in agent chat into host [File]s under the guest rootfs.
 * This is not LSP; it is path resolution for tappable links in Markdown.
 */
internal object WorkspacePathResolver {
    private val skipDirNames = setOf(".git", "node_modules", "build", "dist", ".gradle", ".idea", ".termfold")

    fun resolve(context: Context, raw: String, guestWorkspace: String): File? {
        val path = raw.trim().trimEnd(',', ';', ':', ')', ']', '"', '\'', '`')
        if (path.isEmpty()) return null
        if (Projects.isInGuest(context, path)) {
            return File(path).takeIf { it.isFile }
        }
        if (path.startsWith("/")) {
            val host = Projects.hostPath(context, path)
            return host.takeIf { it.isFile }
        }
        if (path.contains('/')) {
            val base = guestWorkspace.trimEnd('/').ifEmpty { return null }
            val guest = "$base/${path.trimStart('/')}"
            return Projects.hostPath(context, guest).takeIf { it.isFile }
        }
        return resolveBasename(context, path, guestWorkspace)
    }

    private fun resolveBasename(context: Context, name: String, guestWorkspace: String): File? {
        val ws = Projects.hostPath(context, guestWorkspace.trimEnd('/').ifEmpty { return null })
        if (!ws.isDirectory) return null
        listOf(
            File(ws, name),
            File(ws, "assets/$name"),
            File(ws, "src/$name"),
        ).firstOrNull { it.isFile }?.let { return it }
        return findByName(ws, name, maxDepth = 5)
    }

    private fun findByName(dir: File, name: String, maxDepth: Int): File? {
        if (maxDepth < 0 || !dir.isDirectory) return null
        val children = dir.listFiles() ?: return null
        for (child in children) {
            if (child.isFile && child.name.equals(name, ignoreCase = false)) return child
        }
        for (child in children) {
            if (!child.isDirectory || child.name in skipDirNames || child.name.startsWith('.')) continue
            findByName(child, name, maxDepth - 1)?.let { return it }
        }
        return null
    }
}
