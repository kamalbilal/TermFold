package com.termfold.app.acp

import com.termfold.app.gitworkspace.capToolPatchText
import com.termfold.app.gitworkspace.splitGitPatchByFile
import com.termfold.app.gitworkspace.synthesizeUnifiedDiff
import org.json.JSONArray
import org.json.JSONObject

data class ParsedToolContent(
    val detail: String,
    val fileDiffs: List<AcpToolFileDiff>,
    val terminalIds: List<String> = emptyList(),
)

object AcpToolContentParser {

    const val MAX_DETAIL_CHARS = 48_000

    private val PATH_LIST_KEYS = arrayOf(
        "files", "filenames", "paths", "filePaths", "items", "uris", "entries", "hits",
    )
    private val MATCH_COUNT_KEYS = arrayOf(
        "totalMatches", "total_matches", "count", "matchCount", "total", "numMatches",
    )
    private val NESTED_RAW_OUTPUT_KEYS = arrayOf(
        "result", "data", "output", "value", "payload", "structuredContent", "structured_content",
    )
    private val RAW_OUTPUT_META_KEYS = setOf(
        "success", "ok", "isError", "is_error", "error", "exitCode", "exit_code",
        "durationMs", "duration_ms", "truncated", "hasMore", "status", "type", "kind",
    )

    fun contentArray(update: JSONObject?): JSONArray? {
        update ?: return null
        return when (val raw = update.opt("content")) {
            is JSONArray -> raw
            is JSONObject -> JSONArray().put(raw)
            else -> null
        }
    }

    fun parseContent(array: JSONArray?): ParsedToolContent {
        array ?: return ParsedToolContent("", emptyList())
        val textParts = mutableListOf<String>()
        val diffs = mutableListOf<AcpToolFileDiff>()
        val terminalIds = linkedSetOf<String>()
        for (i in 0 until array.length()) {
            val entry = array.optJSONObject(i) ?: continue
            when (entry.optString("type")) {
                "content" -> textFromAcpContent(entry.opt("content"))?.let(textParts::add)
                "text" -> entry.optString("text").takeIf { it.isNotBlank() }?.let(textParts::add)
                "diff" -> diffs += parseDiffEntry(entry)
                "terminal" -> entry.optString("terminalId").takeIf { it.isNotBlank() }?.let(terminalIds::add)
                else -> textFromAcpContent(entry)?.let(textParts::add)
            }
        }
        return ParsedToolContent(
            detail = capDetail(textParts.filter { it.isNotBlank() }.joinToString("\n").trim()),
            fileDiffs = dedupeToolFileDiffs(diffs),
            terminalIds = terminalIds.toList(),
        )
    }

    /** Adjacent streamed text chunks compose into one body (ACP tool_call_content_chunk). */
    fun appendStreamedDetail(previous: String, chunk: String): String {
        if (chunk.isBlank()) return previous
        if (previous.isBlank()) return capDetail(chunk)
        return capDetail(previous + chunk)
    }

    /** Text body for one `tool_call_content_chunk` (content item and/or rawOutput delta). */
    fun chunkDetailFromUpdate(update: JSONObject): String {
        val fromContent = parseContent(contentArray(update)).detail
        if (fromContent.isNotBlank()) return fromContent
        if (update.has("rawOutput") && !update.isNull("rawOutput")) {
            when (val raw = update.opt("rawOutput")) {
                is JSONObject -> {
                    if (raw.has("stdout") && !raw.isNull("stdout")) {
                        return capDetail(raw.optString("stdout"))
                    }
                }
            }
            val parsed = parseRawOutput(update.opt("rawOutput"))
            if (parsed.isNotBlank() && !isMetadataOnlyToolOutput(parsed)) return parsed
        }
        return update.optString("text").takeIf { it.isNotEmpty() }.orEmpty()
    }

    /**
     * Cursor (and some other agents) leave ACP `content` empty and put the tool result in
     * `rawOutput`: `{stdout,stderr,exitCode}`, a `content` string, glob `files`, grep `matches`,
     * or `formatted_output`.
     */
    fun parseRawOutput(value: Any?, depth: Int = 0): String {
        if (depth > 5) return ""
        val text = when (value) {
            null, JSONObject.NULL -> ""
            is String -> parseRawOutputString(value, depth)
            is Number, is Boolean -> if (depth == 0) value.toString() else ""
            is JSONArray -> parseRawOutputArray(value, depth)
            is JSONObject -> parseRawOutputObject(value, depth)
            else -> if (depth == 0) value.toString() else ""
        }
        return capDetail(text.trim())
    }

    private fun parseRawOutputString(value: String, depth: Int): String {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return ""
        if (depth < 3 && trimmed.length >= 2) {
            try {
                when (trimmed.first()) {
                    '{' -> {
                        val nested = parseRawOutputObject(JSONObject(trimmed), depth + 1)
                        if (nested.isNotBlank()) return nested
                    }
                    '[' -> {
                        val nested = parseRawOutputArray(JSONArray(trimmed), depth + 1)
                        if (nested.isNotBlank()) return nested
                    }
                }
            } catch (_: Exception) {
                // plain text
            }
        }
        return trimmed
    }

    fun detailFromUpdate(update: JSONObject, previous: String = ""): String {
        if (update.has("content") && (update.isNull("content") || contentArray(update)?.length() == 0)) {
            val cleared = parseContent(contentArray(update)).detail
            val fromRaw = if (update.has("rawOutput") && !update.isNull("rawOutput")) {
                parseRawOutput(update.opt("rawOutput")).takeUnless { isMetadataOnlyToolOutput(it) }.orEmpty()
            } else {
                ""
            }
            return fromRaw.ifBlank { cleared }
        }
        val fromContent = parseContent(contentArray(update)).detail
        val fromRaw = if (update.has("rawOutput") && !update.isNull("rawOutput")) {
            parseRawOutput(update.opt("rawOutput"))
        } else {
            ""
        }
        val substantiveRaw = fromRaw.takeUnless { isMetadataOnlyToolOutput(it) }.orEmpty()
        return fromContent.ifBlank { substantiveRaw }.ifBlank { previous }
    }

    /** Cursor often finishes MCP tools with `rawOutput: {success:true}` and no text body. */
    fun isMetadataOnlyToolOutput(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return true
        if (!trimmed.startsWith("{")) return false
        return try {
            isMetadataOnlyJsonObject(JSONObject(trimmed))
        } catch (_: Exception) {
            false
        }
    }

    private fun isMetadataOnlyJsonObject(obj: JSONObject): Boolean {
        if (obj.length() == 0) return true
        if (rawMatchCount(obj) != null && collectRawOutputPaths(obj).isEmpty()) return false
        val keys = mutableListOf<String>()
        obj.keys().forEach { keys += it }
        if (keys.any { it in SUBSTANTIVE_RAW_OUTPUT_KEYS }) return false
        if (keys.all { it in RAW_OUTPUT_META_KEYS }) return true
        if (keys.size == 1 && keys.single() in RAW_OUTPUT_META_KEYS) return true
        if (keys.all { key ->
                key in RAW_OUTPUT_META_KEYS ||
                    obj.opt(key) is Boolean ||
                    obj.opt(key) is Number
            }
        ) {
            return true
        }
        return false
    }

    private val SUBSTANTIVE_RAW_OUTPUT_KEYS = setOf(
        "content", "text", "stdout", "stderr", "files", "paths", "matches", "results",
        "formatted_output", "formattedOutput", "message", "output", "body", "nodes", "edges",
    )

    fun pathsFromContent(array: JSONArray?): List<String> {
        array ?: return emptyList()
        val out = linkedSetOf<String>()
        for (i in 0 until array.length()) {
            val entry = array.optJSONObject(i) ?: continue
            when (entry.optString("type")) {
                "diff" -> collectDiffPaths(entry, out)
            }
        }
        return out.toList()
    }

    private fun parseRawOutputObject(obj: JSONObject, depth: Int): String {
        stringField(obj, "formatted_output", "formattedOutput")?.let { return it }
        obj.optJSONArray("content")?.let { arr ->
            extractMcpStyleContentTexts(arr).takeIf { it.isNotBlank() }?.let { return it }
        }
        if (obj.has("stdout") || obj.has("stderr") || obj.has("exitCode")) {
            return buildString {
                val stdout = stringField(obj, "stdout").orEmpty()
                val stderr = stringField(obj, "stderr").orEmpty()
                append(stdout)
                if (stderr.isNotBlank()) {
                    if (isNotEmpty()) append('\n')
                    append(stderr)
                }
                if (obj.has("exitCode") && !obj.isNull("exitCode")) {
                    if (isNotEmpty()) append('\n')
                    append("[exit ${obj.opt("exitCode")}]")
                }
            }
        }
        when (val content = obj.opt("content")) {
            is String -> if (content.isNotBlank()) return content
            is JSONArray -> parseRawOutputArray(content, depth).takeIf { it.isNotBlank() }?.let { return it }
            is JSONObject -> parseRawOutput(content, depth + 1).takeIf { it.isNotBlank() }?.let { return it }
        }
        stringField(obj, "text", "output", "message", "response", "body", "result")?.let { return it }
        val paths = collectRawOutputPaths(obj)
        if (paths.isNotEmpty()) {
            return formatPathList(paths, rawMatchCount(obj))
        }
        rawMatchCount(obj)?.takeIf { it > 0 }?.let { count ->
            return if (count == 1) "1 match" else "$count matches"
        }
        for (nestedKey in NESTED_RAW_OUTPUT_KEYS) {
            obj.optJSONObject(nestedKey)?.let { nested ->
                val nestedText = parseRawOutputObject(nested, depth + 1)
                if (nestedText.isNotBlank()) return nestedText
            }
            obj.optString(nestedKey).takeIf { it.isNotBlank() }?.let { nestedStr ->
                parseRawOutputString(nestedStr, depth + 1).takeIf { it.isNotBlank() }?.let { return it }
            }
        }
        if (depth <= 1 && obj.length() > 0) {
            return formatJsonToolFallback(obj)
        }
        return ""
    }

    /** MCP CallToolResult-style `content: [{type:text,...}]` or plain strings. */
    private fun extractMcpStyleContentTexts(array: JSONArray): String {
        val parts = mutableListOf<String>()
        for (i in 0 until array.length()) {
            when (val item = array.opt(i)) {
                is String -> if (item.isNotBlank()) parts += item
                is JSONObject -> {
                    when (item.optString("type")) {
                        "text" -> item.optString("text").takeIf { it.isNotBlank() }?.let(parts::add)
                        "resource" -> textFromAcpContent(item)?.let(parts::add)
                        else -> textFromAcpContent(item)?.let(parts::add)
                    }
                }
            }
        }
        return parts.joinToString("\n")
    }

    private fun formatJsonToolFallback(obj: JSONObject): String {
        if (isMetadataOnlyJsonObject(obj)) return ""
        rawMatchCount(obj)?.takeIf { collectRawOutputPaths(obj).isEmpty() }?.let { count ->
            if (obj.length() <= 4) {
                return if (count == 1) "1 match" else "$count matches"
            }
        }
        return capDetail(obj.toString(2))
    }

    private fun formatPathList(paths: List<String>, total: Int?): String = buildString {
        append(paths.joinToString("\n"))
        if (total != null && total > paths.size) {
            if (isNotEmpty()) append('\n')
            append("($total matches)")
        }
    }

    private fun collectRawOutputPaths(obj: JSONObject, depth: Int = 0): List<String> {
        if (depth > 2) return emptyList()
        for (key in PATH_LIST_KEYS) {
            listField(obj, key).takeIf { it.isNotEmpty() }?.let { return it }
        }
        listField(obj, "matches", "results").takeIf { it.isNotEmpty() }?.let { return it }
        for (nestedKey in NESTED_RAW_OUTPUT_KEYS) {
            obj.optJSONObject(nestedKey)?.let { nested ->
                collectRawOutputPaths(nested, depth + 1).takeIf { it.isNotEmpty() }?.let { return it }
            }
        }
        return emptyList()
    }

    private fun rawMatchCount(obj: JSONObject): Int? {
        for (key in MATCH_COUNT_KEYS) {
            val v = obj.opt(key)
            if (v is Number) return v.toInt()
        }
        for (nestedKey in NESTED_RAW_OUTPUT_KEYS) {
            obj.optJSONObject(nestedKey)?.let { return rawMatchCount(it) }
        }
        return null
    }

    private fun parseRawOutputArray(array: JSONArray, depth: Int): String {
        val parts = mutableListOf<String>()
        for (i in 0 until array.length()) {
            val item = array.opt(i)
            val piece = when (item) {
                is String -> item
                is JSONObject -> {
                    when (item.optString("type")) {
                        "text" -> item.optString("text").takeIf { it.isNotBlank() }
                        else -> formatMatchLine(item) ?: parseRawOutputObject(item, depth + 1).ifBlank {
                            textFromAcpContent(item)
                        }
                    }
                }
                else -> parseRawOutput(item, depth + 1)
            }
            if (!piece.isNullOrBlank()) parts += piece
        }
        return parts.joinToString("\n")
    }

    private fun formatMatchLine(obj: JSONObject): String? {
        val path = stringField(obj, "path", "file", "filename", "uri") ?: return null
        val line = obj.opt("lineNumber") ?: obj.opt("line") ?: obj.opt("startLine")
        val snippet = stringField(obj, "content", "text", "preview", "line")
        return buildString {
            append(path)
            if (line is Number) append(':').append(line.toInt())
            if (!snippet.isNullOrBlank() && snippet != path) {
                append(": ")
                append(snippet.trim())
            }
        }
    }

    private fun stringField(obj: JSONObject, vararg keys: String): String? {
        for (key in keys) {
            if (!obj.has(key) || obj.isNull(key)) continue
            when (val v = obj.opt(key)) {
                is String -> if (v.isNotBlank()) return v
            }
        }
        return null
    }

    private fun listField(obj: JSONObject, vararg keys: String): List<String> {
        for (key in keys) {
            val raw = obj.opt(key) ?: continue
            val list = when (raw) {
                is JSONArray -> parseRawOutputArray(raw, 1)
                is String -> raw
                else -> continue
            }
            if (list.isNotBlank()) return list.lines().filter { it.isNotBlank() }
        }
        return emptyList()
    }

    private fun capDetail(text: String): String {
        if (text.length <= MAX_DETAIL_CHARS) return text
        return text.take(MAX_DETAIL_CHARS) + "\n… truncated …"
    }

    private fun parseDiffEntry(entry: JSONObject): List<AcpToolFileDiff> {
        val changes = entry.optJSONArray("changes")
        if (changes != null && changes.length() > 0) {
            return parseV2Diff(entry, changes)
        }
        val path = entry.optString("path").takeIf { it.isNotBlank() } ?: return emptyList()
        val oldText = jsonStringOrNull(entry, "oldText")
        val newText = jsonStringOrNull(entry, "newText")
        val patch = capToolPatchText(synthesizeUnifiedDiff(path, oldText, newText))
        val op = when {
            oldText == null && newText != null -> AcpToolFileDiff.Operation.ADD
            newText == null && oldText != null -> AcpToolFileDiff.Operation.DELETE
            else -> AcpToolFileDiff.Operation.MODIFY
        }
        return listOf(AcpToolFileDiff(path = path, operation = op, patchText = patch.ifBlank { null }))
    }

    private fun parseV2Diff(entry: JSONObject, changes: JSONArray): List<AcpToolFileDiff> {
        val changeList = buildList {
            for (i in 0 until changes.length()) {
                val c = changes.optJSONObject(i) ?: continue
                val path = c.optString("path").takeIf { it.isNotBlank() } ?: continue
                add(
                    AcpToolFileDiff(
                        path = path,
                        operation = operationFrom(c.optString("operation")),
                        oldPath = c.optString("oldPath").takeIf { it.isNotBlank() },
                    ),
                )
            }
        }
        if (changeList.isEmpty()) return emptyList()
        val patchObj = entry.optJSONObject("patch")
        val patchText = if (patchObj?.optString("format") == "git_patch") {
            patchObj.optString("text").takeIf { it.isNotBlank() }
        } else {
            null
        }
        if (patchText == null) {
            return changeList.map { it.copy(patchText = null) }
        }
        val sections = splitGitPatchByFile(patchText)
        if (sections.size <= 1) {
            val single = capToolPatchText(patchText)
            return if (changeList.size == 1) {
                listOf(changeList.single().copy(patchText = single))
            } else {
                changeList.mapIndexed { index, diff ->
                    diff.copy(patchText = sections.getOrNull(index)?.let { capToolPatchText(it) } ?: single)
                }
            }
        }
        return changeList.map { diff ->
            val section = sections.firstOrNull { sec ->
                sec.contains(diff.path) || (diff.oldPath != null && sec.contains(diff.oldPath))
            }
            diff.copy(patchText = section?.let { capToolPatchText(it) })
        }
    }

    private fun collectDiffPaths(entry: JSONObject, out: MutableSet<String>) {
        val changes = entry.optJSONArray("changes")
        if (changes != null) {
            for (i in 0 until changes.length()) {
                val c = changes.optJSONObject(i) ?: continue
                c.optString("path").takeIf { it.isNotBlank() }?.let(out::add)
                c.optString("oldPath").takeIf { it.isNotBlank() }?.let(out::add)
            }
        }
        entry.optString("path").takeIf { it.isNotBlank() }?.let(out::add)
    }

    private fun jsonStringOrNull(obj: JSONObject, key: String): String? {
        if (!obj.has(key) || obj.isNull(key)) return null
        return obj.optString(key).ifBlank { null }
    }

    private fun operationFrom(raw: String): AcpToolFileDiff.Operation = when (raw.lowercase()) {
        "add" -> AcpToolFileDiff.Operation.ADD
        "delete" -> AcpToolFileDiff.Operation.DELETE
        "modify" -> AcpToolFileDiff.Operation.MODIFY
        "move" -> AcpToolFileDiff.Operation.MOVE
        "copy" -> AcpToolFileDiff.Operation.COPY
        else -> AcpToolFileDiff.Operation.UNKNOWN
    }
}

private fun textFromAcpContent(content: Any?): String? {
    if (content is String) return content.takeIf { it.isNotBlank() }
    val list = when (content) {
        is JSONObject -> listOf(content)
        is JSONArray -> List(content.length()) { content.optJSONObject(it) }.filterNotNull()
        else -> return null
    }
    return list.mapNotNull { block ->
        when (block.optString("type")) {
            "text" -> block.optString("text").takeIf { it.isNotBlank() }
            "resource" -> {
                val resource = block.optJSONObject("resource") ?: block
                resource.optString("text").takeIf { it.isNotBlank() }
                    ?: resource.optJSONObject("resource")?.optString("text")?.takeIf { it.isNotBlank() }
            }
            else -> block.optString("text").takeIf { it.isNotBlank() }
        }
    }.joinToString("\n").takeIf { it.isNotBlank() }
}
