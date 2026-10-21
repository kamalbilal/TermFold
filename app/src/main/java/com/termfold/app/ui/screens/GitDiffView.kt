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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
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

@Composable
fun GitDiffDisplayRow(
    row: DiffDisplayRow,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
    onExpandContextFold: ((String) -> Unit)? = null,
) {
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
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        is DiffDisplayRow.HunkHeader -> Text(
            row.text,
            style = textStyle,
            color = GitDiffColors.hunkHeaderFg,
            modifier = modifier
                .fillMaxWidth()
                .background(GitDiffColors.hunkHeaderBg)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
        is DiffDisplayRow.Unified -> DiffUnifiedLine(row.prefix, row.cell, textStyle, modifier)
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
) {
    val (bg, fg) = cellColors(cell.kind)
    Row(
        modifier
            .fillMaxWidth()
            .background(bg)
            .padding(vertical = 1.dp),
    ) {
        Text(
            cell.lineNumber?.toString() ?: "",
            style = textStyle,
            color = GitDiffColors.gutterFg,
            modifier = Modifier.width(36.dp).padding(start = 4.dp),
        )
        Text(prefix.toString(), style = textStyle, color = fg, modifier = Modifier.width(12.dp))
        Text(
            cell.text.ifEmpty { " " },
            style = textStyle,
            color = fg,
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
        Text(
            cell.text.ifEmpty { " " },
            style = textStyle,
            color = fg,
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
