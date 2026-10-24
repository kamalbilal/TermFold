package com.termfold.app.gitworkspace

/** Maximum characters stored or synthesized for a single tool-call file patch. */
const val MAX_TOOL_PATCH_CHARS = 512_000

const val TOOL_PATCH_TRUNCATED_MARKER = "\n… (diff truncated)\n"

/**
 * Builds a unified diff string for [path] from optional before/after text (ACP v1 shape).
 * Output is compatible with [parseUnifiedDiff].
 */
fun synthesizeUnifiedDiff(path: String, oldText: String?, newText: String?): String {
    val old = oldText ?: ""
    val new = newText ?: ""
    val cappedOld = capPatchInput(old)
    val cappedNew = capPatchInput(new)
    return when {
        cappedOld.isEmpty() && cappedNew.isEmpty() -> ""
        cappedOld.isEmpty() -> fileHeaderAdd(path, cappedNew)
        cappedNew.isEmpty() -> fileHeaderDelete(path, cappedOld)
        cappedOld == cappedNew -> ""
        else -> fileHeaderModify(path, cappedOld, cappedNew)
    }
}

/** Splits a multi-section `git_patch` into one patch string per `diff --git` file section. */
fun splitGitPatchByFile(patchText: String): List<String> {
    if (patchText.isBlank()) return emptyList()
    val lines = patchText.lines()
    val sections = mutableListOf<MutableList<String>>()
    var current: MutableList<String>? = null
    for (line in lines) {
        if (line.startsWith("diff --git ")) {
            current = mutableListOf(line)
            sections += current
        } else if (current != null) {
            current += line
        }
    }
    if (sections.isEmpty()) return listOf(patchText.trimEnd())
    return sections.map { it.joinToString("\n").trimEnd() }.filter { it.isNotBlank() }
}

/** Truncates patch text for storage or display when over [MAX_TOOL_PATCH_CHARS]. */
fun capToolPatchText(patch: String): String {
    if (patch.length <= MAX_TOOL_PATCH_CHARS) return patch
    return patch.take(MAX_TOOL_PATCH_CHARS) + TOOL_PATCH_TRUNCATED_MARKER
}

fun diffLineStats(patchText: String): Pair<Int, Int> {
    if (patchText.isBlank()) return 0 to 0
    var adds = 0
    var removes = 0
    for (line in patchText.lineSequence()) {
        when {
            line.startsWith("+++") || line.startsWith("---") || line.startsWith("@@") ||
                line.startsWith("diff ") || line.startsWith("index ") -> Unit
            line.startsWith("+") -> adds++
            line.startsWith("-") -> removes++
        }
    }
    return adds to removes
}

private fun capPatchInput(text: String): String =
    if (text.length <= MAX_TOOL_PATCH_CHARS) text else text.take(MAX_TOOL_PATCH_CHARS)

private fun fileHeaderAdd(path: String, newContent: String): String {
    val lines = newContent.lines()
    val body = lines.map { "+$it" }.joinToString("\n")
    return buildString {
        appendLine("--- /dev/null")
        appendLine("+++ $path")
        appendLine("@@ -0,0 +1,${lines.size} @@")
        append(body)
    }.trimEnd()
}

private fun fileHeaderDelete(path: String, oldContent: String): String {
    val lines = oldContent.lines()
    val body = lines.map { "-$it" }.joinToString("\n")
    return buildString {
        appendLine("--- $path")
        appendLine("+++ /dev/null")
        appendLine("@@ -1,${lines.size} +0,0 @@")
        append(body)
    }.trimEnd()
}

private fun fileHeaderModify(path: String, oldContent: String, newContent: String): String {
    val oldLines = oldContent.lines()
    val newLines = newContent.lines()
    val edits = lineDiffEdits(oldLines, newLines)
    if (edits.isEmpty()) return ""
    val hunks = buildHunks(edits, oldLines, newLines, context = 3)
    return buildString {
        appendLine("--- $path")
        appendLine("+++ $path")
        hunks.forEach { appendLine(it) }
    }.trimEnd()
}

private sealed interface LineEdit {
    data class Keep(val oldIndex: Int, val newIndex: Int) : LineEdit
    data class Remove(val oldIndex: Int) : LineEdit
    data class Add(val newIndex: Int) : LineEdit
}

private fun lineDiffEdits(oldLines: List<String>, newLines: List<String>): List<LineEdit> {
    val n = oldLines.size
    val m = newLines.size
    val lcs = lcsTable(oldLines, newLines)
    val out = mutableListOf<LineEdit>()
    var i = n
    var j = m
    val stack = ArrayDeque<LineEdit>()
    while (i > 0 || j > 0) {
        when {
            i > 0 && j > 0 && oldLines[i - 1] == newLines[j - 1] -> {
                stack.addFirst(LineEdit.Keep(i - 1, j - 1))
                i--
                j--
            }
            i > 0 && (j == 0 || lcs[i - 1][j] >= lcs[i][j - 1]) -> {
                stack.addFirst(LineEdit.Remove(i - 1))
                i--
            }
            j > 0 -> {
                stack.addFirst(LineEdit.Add(j - 1))
                j--
            }
        }
    }
    out.addAll(stack)
    return out
}

private fun lcsTable(a: List<String>, b: List<String>): Array<IntArray> {
    val n = a.size
    val m = b.size
    val dp = Array(n + 1) { IntArray(m + 1) }
    for (i in 1..n) {
        for (j in 1..m) {
            dp[i][j] = if (a[i - 1] == b[j - 1]) {
                dp[i - 1][j - 1] + 1
            } else {
                maxOf(dp[i - 1][j], dp[i][j - 1])
            }
        }
    }
    return dp
}

private fun buildHunks(
    edits: List<LineEdit>,
    oldLines: List<String>,
    newLines: List<String>,
    context: Int,
): List<String> {
    if (edits.none { it !is LineEdit.Keep }) return emptyList()
    val changeIndices = edits.indices.filter { edits[it] !is LineEdit.Keep }
    if (changeIndices.isEmpty()) return emptyList()
    val regions = mutableListOf<IntRange>()
    for (idx in changeIndices) {
        val start = (idx - context).coerceAtLeast(0)
        val end = (idx + context).coerceAtMost(edits.lastIndex)
        val last = regions.lastOrNull()
        if (last != null && start <= last.last + 1) {
            regions[regions.size - 1] = last.first..maxOf(last.last, end)
        } else {
            regions += start..end
        }
    }
    return regions.mapNotNull { range ->
        var oldLine = 1
        var newLine = 1
        for (i in 0 until range.first) {
            when (edits[i]) {
                is LineEdit.Keep -> {
                    oldLine++
                    newLine++
                }
                is LineEdit.Remove -> oldLine++
                is LineEdit.Add -> newLine++
            }
        }
        val oldStart = oldLine
        val newStart = newLine
        var oldCount = 0
        var newCount = 0
        val body = buildString {
            for (i in range) {
                when (val edit = edits[i]) {
                    is LineEdit.Keep -> {
                        appendLine(" ${oldLines[edit.oldIndex]}")
                        oldCount++
                        newCount++
                    }
                    is LineEdit.Remove -> {
                        appendLine("-${oldLines[edit.oldIndex]}")
                        oldCount++
                    }
                    is LineEdit.Add -> {
                        appendLine("+${newLines[edit.newIndex]}")
                        newCount++
                    }
                }
            }
        }.trimEnd()
        if (body.isEmpty() || (oldCount == 0 && newCount == 0)) return@mapNotNull null
        "@@ -$oldStart,$oldCount +$newStart,$newCount @@\n$body"
    }
}
