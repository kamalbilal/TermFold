package com.termfold.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termfold.app.R
import com.termfold.app.gitworkspace.DiffDisplayRow
import com.termfold.app.gitworkspace.DiffLineKind
import com.termfold.app.gitworkspace.SideBySideCell
import com.termfold.app.gitworkspace.SideBySideRow
import com.termfold.app.ui.theme.GitDiffColors
import com.termfold.app.ui.theme.Palette

@Composable
fun gitDiffLineTextStyle(): TextStyle =
    MaterialTheme.typography.bodySmall.copy(
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        lineHeight = 14.sp,
    )

/** Same size as [gitDiffLineTextStyle], with trimmed line metrics for dense agent-chat diffs. */
@Composable
fun agentChatDiffLineTextStyle(): TextStyle =
    gitDiffLineTextStyle().copy(
        lineHeight = 12.sp,
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.Both,
        ),
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )

@Composable
fun GitDiffDisplayRow(
    row: DiffDisplayRow,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
    onExpandContextFold: ((String) -> Unit)? = null,
    compact: Boolean = false,
) {
    val contextVPad = if (compact) 0.dp else 6.dp
    val hunkVPad = if (compact) 0.dp else 4.dp
    when (row) {
        is DiffDisplayRow.ContextFold -> {
            val label = stringResource(R.string.git_changes_expand_context, row.omittedLineCount)
            Text(
                label,
                style = textStyle,
                color = GitDiffColors.hunkHeaderFg,
                modifier = modifier
                    .fillMaxWidth()
                    .background(GitDiffColors.hunkHeaderBg)
                    .clickable(enabled = onExpandContextFold != null) {
                        onExpandContextFold?.invoke(row.foldKey)
                    }
                    .padding(horizontal = 12.dp, vertical = contextVPad),
            )
        }
        is DiffDisplayRow.HunkHeader -> Text(
            row.text,
            style = textStyle,
            color = GitDiffColors.hunkHeaderFg,
            modifier = modifier
                .fillMaxWidth()
                .background(GitDiffColors.hunkHeaderBg)
                .padding(horizontal = 8.dp, vertical = hunkVPad),
        )
        is DiffDisplayRow.Unified -> DiffUnifiedLine(row.prefix, row.cell, textStyle, modifier, compact)
        is DiffDisplayRow.Split -> SideBySideDiffRow(row.row, textStyle, modifier)
    }
}

@Composable
fun GitDiffFallbackLine(
    line: String,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    val (bg, fg) = when {
        line.startsWith("@@") -> GitDiffColors.hunkHeaderBg to GitDiffColors.hunkHeaderFg
        line.startsWith("+") && !line.startsWith("+++") -> GitDiffColors.addBg to GitDiffColors.addFg
        line.startsWith("-") && !line.startsWith("---") -> GitDiffColors.removeBg to GitDiffColors.removeFg
        else -> Palette.Bg to GitDiffColors.contextFg
    }
    Text(
        line.ifEmpty { " " },
        style = textStyle,
        color = fg,
        modifier = modifier
            .fillMaxWidth()
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 1.dp),
    )
}

@Composable
private fun DiffUnifiedLine(
    prefix: Char,
    cell: SideBySideCell,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val (bg, fg) = cellColors(cell.kind)
    val lineVPad = if (compact) 0.dp else 1.dp
    val gutterWidth = if (compact) 28.dp else 36.dp
    val prefixWidth = if (compact) 10.dp else 12.dp
    Row(
        modifier
            .fillMaxWidth()
            .background(bg)
            .padding(vertical = lineVPad),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            cell.lineNumber?.toString() ?: "",
            style = textStyle,
            color = GitDiffColors.gutterFg,
            modifier = Modifier.width(gutterWidth).padding(start = if (compact) 2.dp else 4.dp),
        )
        Text(prefix.toString(), style = textStyle, color = fg, modifier = Modifier.width(prefixWidth))
        DiffLineText(
            cell = cell,
            textStyle = textStyle,
            fg = fg,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
        )
    }
}

@Composable
private fun SideBySideDiffRow(
    row: SideBySideRow,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth()) {
        DiffHalfPane(row.left, textStyle, Modifier.weight(1f))
        Box(
            Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(Palette.BorderSoft),
        )
        DiffHalfPane(row.right, textStyle, Modifier.weight(1f))
    }
}

@Composable
private fun DiffHalfPane(
    cell: SideBySideCell?,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    if (cell == null) {
        Box(modifier.fillMaxWidth().padding(vertical = 8.dp))
        return
    }
    val (bg, fg) = cellColors(cell.kind)
    Row(
        modifier
            .fillMaxWidth()
            .background(bg)
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            cell.lineNumber?.toString() ?: "",
            style = textStyle,
            color = GitDiffColors.gutterFg,
            modifier = Modifier.width(32.dp).padding(start = 4.dp),
        )
        Text(
            when (cell.kind) {
                DiffLineKind.REMOVE -> "-"
                DiffLineKind.ADD -> "+"
                else -> " "
            },
            style = textStyle,
            color = fg,
            modifier = Modifier.width(10.dp),
        )
        DiffLineText(
            cell = cell,
            textStyle = textStyle,
            fg = fg,
            modifier = Modifier.padding(end = 4.dp),
        )
    }
}

private fun cellColors(kind: DiffLineKind): Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color> =
    when (kind) {
        DiffLineKind.ADD -> GitDiffColors.addBg to GitDiffColors.addFg
        DiffLineKind.REMOVE -> GitDiffColors.removeBg to GitDiffColors.removeFg
        else -> Palette.Bg to GitDiffColors.contextFg
    }

@Composable
private fun DiffLineText(
    cell: SideBySideCell,
    textStyle: TextStyle,
    fg: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    val display = cell.text.ifEmpty { " " }
    val spans = cell.inlineSpans
    val hasEmphasis = spans != null && spans.any { it.emphasized }
    if (!hasEmphasis) {
        Text(display, style = textStyle, color = fg, modifier = modifier)
        return
    }
    val wordBg = when (cell.kind) {
        DiffLineKind.REMOVE -> GitDiffColors.removeWordBg
        DiffLineKind.ADD -> GitDiffColors.addWordBg
        else -> null
    }
    val wordFg = when (cell.kind) {
        DiffLineKind.REMOVE -> GitDiffColors.removeWordFg
        DiffLineKind.ADD -> GitDiffColors.addWordFg
        else -> fg
    }
    Text(
        buildAnnotatedString {
            spans.orEmpty().forEach { seg ->
                if (seg.text.isEmpty()) return@forEach
                if (seg.emphasized && wordBg != null) {
                    withStyle(
                        SpanStyle(
                            background = wordBg,
                            color = wordFg,
                            fontWeight = FontWeight.Bold,
                        ),
                    ) { append(seg.text) }
                } else {
                    append(seg.text)
                }
            }
            if (length == 0) append(" ")
        },
        style = textStyle,
        color = fg,
        modifier = modifier,
    )
}
