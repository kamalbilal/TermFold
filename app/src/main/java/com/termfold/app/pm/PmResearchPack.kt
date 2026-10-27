package com.termfold.app.pm

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object PmResearchPack {
    private const val MAX_TOTAL_BYTES = 120_000
    private val ROOT_FILE_HINTS = listOf(
        "readme.md",
        "readme",
        "package.json",
        "pyproject.toml",
        "cargo.toml",
        "go.mod",
        "pubspec.yaml",
        "composer.json",
        "gemfile",
        "openspec/config.yaml",
        "agents.md",
        "contributing.md",
    )
    private val ROOT_TREE_HINTS = listOf(
        "docs",
        "doc",
        "openspec",
        "openspec/specs",
        "src",
        "app",
        "lib",
    )

    data class Progress(val step: String)

    suspend fun buildRepoPack(
        context: Context,
        goldenGuestPath: String,
        viewBranch: String,
        onProgress: (Progress) -> Unit = {},
    ): String = withContext(Dispatchers.IO) {
        onProgress(Progress("Reading repo at $viewBranch"))
        val facts = JSONArray()
        var used = 0
        val log = PmRepoResearch.logAtRef(context, goldenGuestPath, viewBranch, 25)
            .getOrElse { "" }
        val rootEntries = PmRepoResearch.listFilesAtRef(context, goldenGuestPath, viewBranch, "", maxCount = 80)
            .getOrElse { emptyList() }
        val files = pickFiles(rootEntries)
        for (path in files) {
            if (used >= MAX_TOTAL_BYTES) break
            indexFile(context, goldenGuestPath, viewBranch, path, facts) { bytes ->
                used += bytes
            }?.let { onProgress(Progress("Indexed $path")) }
        }
        val trees = pickTrees(rootEntries)
        for (tree in trees) {
            if (used >= MAX_TOTAL_BYTES) break
            val nested = PmRepoResearch.listFilesAtRef(context, goldenGuestPath, viewBranch, tree, maxCount = 20)
                .getOrElse { emptyList() }
            for (path in nested) {
                if (used >= MAX_TOTAL_BYTES) break
                if (looksBinary(path)) continue
                indexFile(context, goldenGuestPath, viewBranch, path, facts) { bytes ->
                    used += bytes
                }?.let { onProgress(Progress("Indexed $path")) }
            }
        }
        JSONObject()
            .put("viewBranch", viewBranch)
            .put("recentCommits", log.take(8000))
            .put("facts", facts)
            .toString(2)
    }

    fun summarizeForPrompt(packJson: String?, maxChars: Int = 8_000): String {
        if (packJson.isNullOrBlank()) return ""
        val repoJson = extractHeadingJson(packJson, "Repo pack") ?: firstJsonObject(packJson)
        val competitiveJson = extractHeadingJson(packJson, "Competitive pack")
        val repoPart = repoJson?.let { summarizeRepo(it) }.orEmpty()
        val webPart = competitiveJson?.let { summarizeCompetitive(it) }.orEmpty()
        val combined = buildString {
            if (repoPart.isNotBlank()) appendLine(repoPart)
            if (webPart.isNotBlank()) {
                if (isNotEmpty()) appendLine()
                appendLine(webPart)
            }
        }.trim()
        if (combined.isNotBlank()) return combined.take(maxChars)
        return packJson.take(maxChars)
    }

    internal fun pickFiles(rootEntries: List<String>): List<String> {
        val lowered = rootEntries.map { it.trim().trimStart('/') }
        val files = linkedSetOf<String>()
        lowered.forEach { path ->
            if ('/' !in path && ROOT_FILE_HINTS.any { hint -> path.equals(hint, ignoreCase = true) }) {
                files += path
            }
            if (path.equals("openspec/config.yaml", ignoreCase = true)) files += path
        }
        if (files.none { it.startsWith("readme", ignoreCase = true) }) {
            lowered.firstOrNull { it.contains("readme", ignoreCase = true) && it.count { ch -> ch == '/' } <= 1 }
                ?.let { files += it }
        }
        return files.toList()
    }

    internal fun pickTrees(rootEntries: List<String>): List<String> {
        val top = rootEntries.map { it.trim().trimStart('/').substringBefore('/') }.toSet()
        return ROOT_TREE_HINTS.filter { hint ->
            top.any { it.equals(hint.substringBefore('/'), ignoreCase = true) } ||
                rootEntries.any { it.trimStart('/').startsWith("$hint/") }
        }.distinct()
    }

    private fun summarizeRepo(raw: String): String = runCatching {
        val root = JSONObject(raw)
        val facts = root.optJSONArray("facts") ?: JSONArray()
        buildString {
            appendLine("Repo @ ${root.optString("viewBranch")}:")
            val commits = root.optString("recentCommits").trim()
            if (commits.isNotBlank()) {
                appendLine("Recent commits:")
                appendLine(commits.take(1_200))
            }
            for (i in 0 until facts.length()) {
                val f = facts.getJSONObject(i)
                appendLine("- ${f.optString("claim")} [${f.optString("source")}]")
                val excerpt = f.optString("excerpt").trim()
                if (excerpt.isNotBlank()) {
                    appendLine(excerpt.take(700))
                }
            }
        }.trim()
    }.getOrDefault("")

    private fun summarizeCompetitive(raw: String): String = runCatching {
        val root = JSONObject(raw)
        val rows = root.optJSONArray("competitors") ?: JSONArray()
        buildString {
            appendLine("Competitive snapshot:")
            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i)
                val name = row.optString("name")
                val gap = row.optString("gap")
                val url = row.optString("url")
                if (gap.isNotBlank()) {
                    appendLine("- $name [gap]: $gap")
                } else {
                    appendLine("- $name [web: $url]")
                    val excerpt = row.optString("excerpt").trim()
                    if (excerpt.isNotBlank()) appendLine(excerpt.take(500))
                }
            }
        }.trim()
    }.getOrDefault("")

    internal fun extractHeadingJson(text: String, heading: String): String? {
        val marker = "## $heading"
        val start = text.indexOf(marker)
        if (start < 0) return null
        val after = text.substring(start + marker.length)
        val next = after.indexOf("\n## ")
        val section = if (next >= 0) after.substring(0, next) else after
        return firstJsonObject(section)
    }

    internal fun firstJsonObject(text: String): String? {
        val start = text.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escape = false
        for (i in start until text.length) {
            val ch = text[i]
            when {
                escape -> escape = false
                inString && ch == '\\' -> escape = true
                ch == '"' -> inString = !inString
                inString -> Unit
                ch == '{' -> depth++
                ch == '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        return null
    }

    private suspend fun indexFile(
        context: Context,
        goldenGuestPath: String,
        viewBranch: String,
        path: String,
        facts: JSONArray,
        onSized: (Int) -> Unit,
    ): Boolean? {
        val body = PmRepoResearch.showFileAtRef(context, goldenGuestPath, viewBranch, path)
            .getOrNull()?.takeIf { it.isNotBlank() } ?: return null
        val slice = body.take(4000)
        onSized(slice.length)
        facts.put(
            JSONObject()
                .put("claim", "Repo excerpt: $path")
                .put("source", "$path@$viewBranch")
                .put("excerpt", slice),
        )
        return true
    }

    private fun looksBinary(path: String): Boolean {
        val lower = path.lowercase()
        return listOf(".png", ".jpg", ".jpeg", ".gif", ".webp", ".ico", ".pdf", ".zip", ".jar", ".so", ".bin")
            .any { lower.endsWith(it) }
    }
}
