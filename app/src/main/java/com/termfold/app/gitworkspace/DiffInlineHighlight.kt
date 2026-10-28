package com.termfold.app.gitworkspace

/** One contiguous run of text on a diff line; [emphasized] marks changed words (GitHub-style). */
data class InlineDiffSegment(
    val text: String,
    val emphasized: Boolean,
)

private const val MAX_INLINE_DIFF_CHARS = 12_000
private val DIFF_TOKEN_REGEX = Regex("""\w+|\s+|[^\w\s]+""")

/**
 * Word-level diff between a removed and added line. Falls back to character LCS when a
 * token pass would paint most of the line (typos / split words).
 */
fun inlineDiffSegments(oldText: String, newText: String): Pair<List<InlineDiffSegment>, List<InlineDiffSegment>> {
    if (oldText == newText) {
        val plain = listOf(InlineDiffSegment(oldText, emphasized = false))
        return plain to plain
    }
    if (oldText.length > MAX_INLINE_DIFF_CHARS || newText.length > MAX_INLINE_DIFF_CHARS) {
        return listOf(InlineDiffSegment(oldText, emphasized = false)) to
            listOf(InlineDiffSegment(newText, emphasized = false))
    }
    if (oldText.isEmpty()) {
        return emptyList<InlineDiffSegment>() to listOf(InlineDiffSegment(newText, emphasized = true))
    }
    if (newText.isEmpty()) {
        return listOf(InlineDiffSegment(oldText, emphasized = true)) to emptyList()
    }
    val token = diffTokens(tokenizeDiffLine(oldText), tokenizeDiffLine(newText))
    val emphasizedChars = token.first.filter { it.emphasized }.sumOf { it.text.length }
    val tooMuch = oldText.length >= 4 && emphasizedChars * 2 >= oldText.length
    return if (tooMuch) charDiff(oldText, newText) else token
}

private fun tokenizeDiffLine(line: String): List<String> =
    DIFF_TOKEN_REGEX.findAll(line).map { it.value }.toList().ifEmpty { listOf(line) }

private enum class DiffOp { Equal, Delete, Insert }

private fun diffTokens(
    oldTokens: List<String>,
    newTokens: List<String>,
): Pair<List<InlineDiffSegment>, List<InlineDiffSegment>> {
    val ops = lcsOps(oldTokens.size, newTokens.size) { i, j -> oldTokens[i] == newTokens[j] }
    return buildTokenSide(oldTokens, ops, forOld = true) to buildTokenSide(newTokens, ops, forOld = false)
}

private fun charDiff(oldText: String, newText: String): Pair<List<InlineDiffSegment>, List<InlineDiffSegment>> {
    val ops = lcsOps(oldText.length, newText.length) { i, j -> oldText[i] == newText[j] }
    return buildCharSide(oldText, ops, forOld = true) to buildCharSide(newText, ops, forOld = false)
}

private fun lcsOps(n: Int, m: Int, equal: (Int, Int) -> Boolean): List<DiffOp> {
    val dp = Array(n + 1) { IntArray(m + 1) }
    for (i in 1..n) {
        for (j in 1..m) {
            dp[i][j] = if (equal(i - 1, j - 1)) dp[i - 1][j - 1] + 1
            else maxOf(dp[i - 1][j], dp[i][j - 1])
        }
    }
    val ops = ArrayList<DiffOp>(n + m)
    var i = n
    var j = m
    while (i > 0 || j > 0) {
        when {
            i > 0 && j > 0 && equal(i - 1, j - 1) -> {
                ops.add(DiffOp.Equal)
                i--
                j--
            }
            j > 0 && (i == 0 || dp[i][j - 1] >= dp[i - 1][j]) -> {
                ops.add(DiffOp.Insert)
                j--
            }
            else -> {
                ops.add(DiffOp.Delete)
                i--
            }
        }
    }
    ops.reverse()
    return ops
}

private fun buildTokenSide(tokens: List<String>, ops: List<DiffOp>, forOld: Boolean): List<InlineDiffSegment> {
    val out = mutableListOf<InlineDiffSegment>()
    var index = 0
    for (op in ops) {
        when (op) {
            DiffOp.Equal -> {
                mergeSegment(out, InlineDiffSegment(tokens[index], emphasized = false))
                index++
            }
            DiffOp.Delete -> if (forOld) {
                mergeSegment(out, InlineDiffSegment(tokens[index], emphasized = true))
                index++
            }
            DiffOp.Insert -> if (!forOld) {
                mergeSegment(out, InlineDiffSegment(tokens[index], emphasized = true))
                index++
            }
        }
    }
    return out
}

private fun buildCharSide(text: String, ops: List<DiffOp>, forOld: Boolean): List<InlineDiffSegment> {
    val out = mutableListOf<InlineDiffSegment>()
    var index = 0
    for (op in ops) {
        when (op) {
            DiffOp.Equal -> {
                mergeSegment(out, InlineDiffSegment(text[index].toString(), emphasized = false))
                index++
            }
            DiffOp.Delete -> if (forOld) {
                mergeSegment(out, InlineDiffSegment(text[index].toString(), emphasized = true))
                index++
            }
            DiffOp.Insert -> if (!forOld) {
                mergeSegment(out, InlineDiffSegment(text[index].toString(), emphasized = true))
                index++
            }
        }
    }
    return out
}

private fun mergeSegment(out: MutableList<InlineDiffSegment>, seg: InlineDiffSegment) {
    if (seg.text.isEmpty()) return
    val last = out.lastOrNull()
    if (last != null && last.emphasized == seg.emphasized) {
        out[out.lastIndex] = last.copy(text = last.text + seg.text)
    } else {
        out += seg
    }
}

/**
 * Pair every replace block in a hunk. Cursor often emits `+` then `-`; git emits `-` then `+`.
 * Equal-length runs (`--++`) are paired in order.
 */
fun applyInlineHighlights(lines: List<DiffLine>): List<DiffLine> {
    if (lines.isEmpty()) return lines
    val out = lines.toMutableList()
    var i = 0
    while (i < out.size) {
        val kind = out[i].kind
        if (kind != DiffLineKind.ADD && kind != DiffLineKind.REMOVE) {
            i++
            continue
        }
        val start = i
        while (i < out.size) {
            val k = out[i].kind
            if (k != DiffLineKind.ADD && k != DiffLineKind.REMOVE) break
            i++
        }
        val removeIdx = (start until i).filter { out[it].kind == DiffLineKind.REMOVE }
        val addIdx = (start until i).filter { out[it].kind == DiffLineKind.ADD }
        if (removeIdx.isEmpty() || addIdx.isEmpty()) continue
        val pairs = minOf(removeIdx.size, addIdx.size)
        for (p in 0 until pairs) {
            val ri = removeIdx[p]
            val ai = addIdx[p]
            val (oldSpans, newSpans) = inlineDiffSegments(out[ri].text, out[ai].text)
            out[ri] = out[ri].copy(inlineSpans = oldSpans)
            out[ai] = out[ai].copy(inlineSpans = newSpans)
        }
    }
    return out
}
