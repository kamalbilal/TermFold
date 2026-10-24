package com.termfold.app.acp

import com.termfold.app.gitworkspace.capToolPatchText
import com.termfold.app.gitworkspace.splitGitPatchByFile
import com.termfold.app.gitworkspace.synthesizeUnifiedDiff
import org.json.JSONArray
import org.json.JSONObject

data class ParsedToolContent(
    val detail: String,
    val fileDiffs: List<AcpToolFileDiff>,
)

object AcpToolContentParser {

    fun parseContent(array: JSONArray?): ParsedToolContent {
        array ?: return ParsedToolContent("", emptyList())
        val textParts = mutableListOf<String>()
        val diffs = mutableListOf<AcpToolFileDiff>()
        for (i in 0 until array.length()) {
            val entry = array.optJSONObject(i) ?: continue
            when (entry.optString("type")) {
                "content" -> textFromAcpContent(entry.opt("content"))?.let(textParts::add)
                "text" -> entry.optString("text").takeIf { it.isNotBlank() }?.let(textParts::add)
                "diff" -> diffs += parseDiffEntry(entry)
            }
        }
        return ParsedToolContent(
            detail = textParts.filter { it.isNotBlank() }.joinToString("\n").trim(),
            fileDiffs = dedupeToolFileDiffs(diffs),
        )
    }

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
    val list = when (content) {
        is JSONObject -> listOf(content)
        is JSONArray -> List(content.length()) { content.optJSONObject(it) }.filterNotNull()
        else -> return null
    }
    return list.mapNotNull { block ->
        when (block.optString("type")) {
            "text" -> block.optString("text").takeIf { it.isNotBlank() }
            else -> block.optString("text").takeIf { it.isNotBlank() }
        }
    }.joinToString("\n").takeIf { it.isNotBlank() }
}
