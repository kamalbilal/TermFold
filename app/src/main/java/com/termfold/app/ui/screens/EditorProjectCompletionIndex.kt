package com.termfold.app.ui.screens

import org.json.JSONObject
import java.io.File

/**
 * Lightweight cross-file identifier index for editor autocomplete (not LSP).
 * Collects `object.member` usages seen in project text sources.
 */
internal object EditorProjectCompletionIndex {
    private val skipDirNames = setOf(
        ".git",
        ".termfold",
        "node_modules",
        "build",
        "dist",
        ".gradle",
        ".idea",
    )

    private val textExtensions = setOf(
        "kt", "kts", "java", "js", "mjs", "cjs", "jsx", "ts", "tsx", "py", "pyi",
        "go", "rs", "c", "h", "cpp", "cc", "hpp", "cs", "swift", "rb", "php",
        "sql", "sh", "bash", "zsh", "json", "jsonc", "yaml", "yml", "md", "gradle",
        "xml", "html", "htm", "css", "scss", "less", "vue", "svelte", "dart",
    )

    private val memberPattern = Regex("""\b([A-Za-z_]\w*)\.([A-Za-z_]\w*)\b""")

    fun jsonFor(
        projectRoot: File,
        maxFiles: Int = 250,
        maxBytesPerFile: Long = 512_000,
        maxDepth: Int = 6,
    ): String {
        if (!projectRoot.isDirectory) return "null"
        val members = linkedMapOf<String, LinkedHashSet<String>>()
        var filesRead = 0
        walk(projectRoot, projectRoot, maxDepth, maxFiles) { file ->
            if (filesRead >= maxFiles) return@walk
            if (file.length() > maxBytesPerFile) return@walk
            val ext = file.extension.lowercase()
            if (ext !in textExtensions && file.name !in specialNames) return@walk
            val text = runCatching { file.readText() }.getOrNull() ?: return@walk
            filesRead++
            for (match in memberPattern.findAll(text)) {
                val obj = match.groupValues[1]
                val member = match.groupValues[2]
                members.getOrPut(obj) { LinkedHashSet() }.add(member)
            }
        }
        val root = JSONObject()
        val membersJson = JSONObject()
        for ((obj, set) in members.toSortedMap()) {
            membersJson.put(obj, org.json.JSONArray(set.sorted()))
        }
        root.put("members", membersJson)
        return root.toString()
    }

    private val specialNames = setOf("Dockerfile", "Makefile", "Gemfile", "Rakefile")

    private fun walk(
        root: File,
        dir: File,
        depth: Int,
        maxFiles: Int,
        onFile: (File) -> Unit,
    ) {
        if (depth < 0) return
        val children = dir.listFiles()?.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }) ?: return
        for (child in children) {
            if (child.name.startsWith('.')) continue
            if (child.isDirectory) {
                if (child.name in skipDirNames) continue
                walk(root, child, depth - 1, maxFiles, onFile)
            } else {
                onFile(child)
            }
        }
    }
}
