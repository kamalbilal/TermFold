package com.termfold.app.gitworkspace

enum class DiffLineKind {
    FILE_HEADER,
    HUNK_HEADER,
    CONTEXT,
    ADD,
    REMOVE,
}

data class DiffLine(
    val kind: DiffLineKind,
    val text: String,
    val oldLineNumber: Int?,
    val newLineNumber: Int?,
)

data class DiffHunk(
    val header: String,
    val lines: List<DiffLine>,
)

data class ParsedUnifiedDiff(
    val hunks: List<DiffHunk>,
    val isBinary: Boolean,
    val truncatedMessage: String?,
)

data class SideBySideCell(
    val lineNumber: Int?,
    val text: String,
    val kind: DiffLineKind,
)

data class SideBySideRow(
    val left: SideBySideCell?,
    val right: SideBySideCell?,
)

fun parseUnifiedDiff(raw: String): ParsedUnifiedDiff {
    if (raw.contains("Binary files")) {
        return ParsedUnifiedDiff(hunks = emptyList(), isBinary = true, truncatedMessage = null)
    }
    val hunks = mutableListOf<DiffHunk>()
    var currentHeader: String? = null
    var currentLines = mutableListOf<DiffLine>()
    var oldLine = 0
    var newLine = 0

    fun flushHunk() {
        val header = currentHeader
        if (header != null) {
            hunks += DiffHunk(header, currentLines.toList())
        }
        currentHeader = null
        currentLines = mutableListOf()
    }

    for (line in raw.lineSequence()) {
        when {
            line.startsWith("@@") -> {
                flushHunk()
                currentHeader = line
                val range = HUNK_HEADER_REGEX.find(line)
                if (range != null) {
                    oldLine = range.groupValues[1].toIntOrNull() ?: 0
                    newLine = range.groupValues[2].toIntOrNull() ?: 0
                }
            }
            line.startsWith("+++") || line.startsWith("---") || line.startsWith("diff ") ||
                line.startsWith("index ") || line.startsWith("new file") ||
                line.startsWith("deleted file") || line.startsWith("similarity index") -> {
                // File-level metadata; not shown inside hunk bodies.
            }
            line.startsWith("+") -> {
                currentLines += DiffLine(DiffLineKind.ADD, line.drop(1), null, newLine)
                if (newLine > 0) newLine++
            }
            line.startsWith("-") -> {
                currentLines += DiffLine(DiffLineKind.REMOVE, line.drop(1), oldLine, null)
                if (oldLine > 0) oldLine++
            }
            line.startsWith("\\") -> {
                currentLines += DiffLine(DiffLineKind.CONTEXT, line, null, null)
            }
            line.startsWith(" ") || line.isEmpty() -> {
                val body = if (line.isEmpty()) "" else line.drop(1)
                currentLines += DiffLine(DiffLineKind.CONTEXT, body, oldLine, newLine)
                if (oldLine > 0) oldLine++
                if (newLine > 0) newLine++
            }
            currentHeader != null -> {
                currentLines += DiffLine(DiffLineKind.CONTEXT, line, oldLine, newLine)
                if (oldLine > 0) oldLine++
                if (newLine > 0) newLine++
            }
        }
    }
    flushHunk()
    return ParsedUnifiedDiff(hunks = hunks, isBinary = false, truncatedMessage = null)
}

const val CONTEXT_FOLD_THRESHOLD = 8

const val MAX_CONCURRENT_DIFF_LOADS = 3

sealed interface DiffDisplayRow {
    data class HunkHeader(val text: String) : DiffDisplayRow
    data class Split(val row: SideBySideRow) : DiffDisplayRow
    data class Unified(val prefix: Char, val cell: SideBySideCell) : DiffDisplayRow
    data class ContextFold(val foldKey: String, val omittedLineCount: Int) : DiffDisplayRow
}

data class CachedDiffPresentation(
    val isBinary: Boolean,
    val rowsUnified: List<DiffDisplayRow>,
    val rowsSplit: List<DiffDisplayRow>,
    val fallbackLines: List<String>,
)

fun prepareDiffPresentation(diff: String): CachedDiffPresentation {
    val parsed = parseUnifiedDiff(diff)
    if (parsed.isBinary) {
        return CachedDiffPresentation(
            isBinary = true,
            rowsUnified = emptyList(),
            rowsSplit = emptyList(),
            fallbackLines = emptyList(),
        )
    }
    val unified = buildDiffDisplayRows(parsed, sideBySide = false)
    val split = buildDiffDisplayRows(parsed, sideBySide = true)
    val fallback = if (unified.isEmpty()) diff.lines() else emptyList()
    return CachedDiffPresentation(
        isBinary = false,
        rowsUnified = unified,
        rowsSplit = split,
        fallbackLines = fallback,
    )
}

fun rowsForMode(presentation: CachedDiffPresentation, sideBySide: Boolean): List<DiffDisplayRow> =
    if (sideBySide) presentation.rowsSplit else presentation.rowsUnified

fun buildDiffDisplayRows(diff: String, sideBySide: Boolean): List<DiffDisplayRow> =
    buildDiffDisplayRows(parseUnifiedDiff(diff), sideBySide)

fun buildDiffDisplayRows(parsed: ParsedUnifiedDiff, sideBySide: Boolean): List<DiffDisplayRow> {
    if (parsed.isBinary || parsed.hunks.isEmpty()) return emptyList()
    val out = mutableListOf<DiffDisplayRow>()
    for ((header, rows) in buildSideBySideRows(parsed.hunks)) {
        out += DiffDisplayRow.HunkHeader(header)
        for (row in rows) {
            if (sideBySide) {
                out += DiffDisplayRow.Split(row)
            } else {
                val left = row.left
                val right = row.right
                when {
                    left?.kind == DiffLineKind.CONTEXT -> out += DiffDisplayRow.Unified(' ', left)
                    left?.kind == DiffLineKind.REMOVE -> out += DiffDisplayRow.Unified('-', left)
                    right?.kind == DiffLineKind.ADD -> out += DiffDisplayRow.Unified('+', right)
                }
            }
        }
    }
    return out
}

fun applyContextFolding(
    rows: List<DiffDisplayRow>,
    foldKeyPrefix: String,
    expandedFolds: Set<String>,
    threshold: Int = CONTEXT_FOLD_THRESHOLD,
): List<DiffDisplayRow> {
    if (rows.isEmpty() || threshold < 2) return rows
    val out = mutableListOf<DiffDisplayRow>()
    var index = 0
    var foldIndex = 0
    while (index < rows.size) {
        val row = rows[index]
        if (!isContextOnlyRow(row)) {
            out += row
            index++
            continue
        }
        var end = index
        while (end < rows.size && isContextOnlyRow(rows[end])) {
            end++
        }
        val runLength = end - index
        if (runLength >= threshold) {
            val foldKey = "$foldKeyPrefix$foldIndex"
            foldIndex++
            if (foldKey in expandedFolds) {
                for (i in index until end) {
                    out += rows[i]
                }
            } else {
                out += DiffDisplayRow.ContextFold(foldKey, runLength)
            }
        } else {
            for (i in index until end) {
                out += rows[i]
            }
        }
        index = end
    }
    return out
}

private fun isContextOnlyRow(row: DiffDisplayRow): Boolean = when (row) {
    is DiffDisplayRow.HunkHeader -> false
    is DiffDisplayRow.ContextFold -> false
    is DiffDisplayRow.Split -> {
        val left = row.row.left
        val right = row.row.right
        left?.kind == DiffLineKind.CONTEXT && right?.kind == DiffLineKind.CONTEXT
    }
    is DiffDisplayRow.Unified -> row.prefix == ' ' && row.cell.kind == DiffLineKind.CONTEXT
}

fun buildSideBySideRows(hunks: List<DiffHunk>): List<Pair<String, List<SideBySideRow>>> =
    hunks.map { hunk ->
        hunk.header to hunk.lines.map { line ->
            when (line.kind) {
                DiffLineKind.CONTEXT -> SideBySideRow(
                    left = SideBySideCell(line.oldLineNumber, line.text, DiffLineKind.CONTEXT),
                    right = SideBySideCell(line.newLineNumber, line.text, DiffLineKind.CONTEXT),
                )
                DiffLineKind.REMOVE -> SideBySideRow(
                    left = SideBySideCell(line.oldLineNumber, line.text, DiffLineKind.REMOVE),
                    right = null,
                )
                DiffLineKind.ADD -> SideBySideRow(
                    left = null,
                    right = SideBySideCell(line.newLineNumber, line.text, DiffLineKind.ADD),
                )
                else -> SideBySideRow(null, null)
            }
        }.filter { it.left != null || it.right != null }
    }

private val HUNK_HEADER_REGEX =
    Regex("""@@ -(\d+)(?:,\d+)? \+(\d+)(?:,\d+)? @@""")
