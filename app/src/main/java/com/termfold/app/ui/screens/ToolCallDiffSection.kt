package com.termfold.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termfold.app.R
import com.termfold.app.acp.AcpToolFileDiff
import com.termfold.app.ui.theme.Mono
import com.termfold.app.ui.theme.Palette
import com.termfold.app.ui.theme.TermFoldIcons

@Composable
fun ToolCallFileDiffHeader(
    path: String,
    operationLabel: String,
    added: Int,
    removed: Int,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Palette.Card)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (expanded) TermFoldIcons.ChevronDown else TermFoldIcons.ChevronRight,
            contentDescription = null,
            tint = Palette.TextFaint,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = path,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = Mono, fontSize = 12.sp),
            color = Palette.TextDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (added > 0) {
            Text("+$added", style = MaterialTheme.typography.labelSmall.copy(fontFamily = Mono), color = Palette.Green)
            Spacer(Modifier.width(6.dp))
        }
        if (removed > 0) {
            Text("-$removed", style = MaterialTheme.typography.labelSmall.copy(fontFamily = Mono), color = Palette.Pink)
        }
        if (added == 0 && removed == 0 && operationLabel.isNotBlank()) {
            Text(
                operationLabel,
                style = MaterialTheme.typography.labelSmall,
                color = Palette.TextFaint,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

@Composable
fun toolDiffOperationLabel(op: AcpToolFileDiff.Operation): String = when (op) {
    AcpToolFileDiff.Operation.ADD -> stringResource(R.string.acp_tool_diff_added)
    AcpToolFileDiff.Operation.DELETE -> stringResource(R.string.acp_tool_diff_deleted)
    AcpToolFileDiff.Operation.MOVE -> stringResource(R.string.acp_tool_diff_moved)
    AcpToolFileDiff.Operation.COPY -> stringResource(R.string.acp_tool_diff_copied)
    AcpToolFileDiff.Operation.MODIFY, AcpToolFileDiff.Operation.UNKNOWN -> ""
}
