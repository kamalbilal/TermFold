package com.termfold.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termfold.app.R
import com.termfold.app.acp.AcpItem
import com.termfold.app.ui.theme.Mono
import com.termfold.app.ui.theme.Palette
import com.termfold.app.ui.theme.TermFoldIcons

/** Compact tool row (previous design); expand reveals [ToolCallOutputPane] below in the timeline. */
@Composable
fun ToolCallCompactHeader(
    item: AcpItem.ToolCall,
    workspaceDir: String,
    collapsed: Boolean,
    onToggle: () -> Unit,
) {
    val label = toolCallLabel(item, workspaceDir)
    val preview = label.lines().firstOrNull { it.isNotBlank() }?.trim() ?: label.trim()
    val headerText = if (collapsed) preview else label.lineSequence().joinToString(" ").trim()
    val failed = item.status == "failed"
    val inProgress = item.status != "completed" && item.status != "failed"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = toolCallIcon(item),
            contentDescription = null,
            tint = if (failed) Palette.Pink else Palette.TextFaint,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = headerText,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = Mono,
                fontSize = 12.5.sp,
                lineHeight = 17.sp,
            ),
            color = when {
                failed -> Palette.Pink
                collapsed -> Palette.TextFaint
                else -> Palette.TextDim
            },
            maxLines = if (collapsed) 1 else 12,
            overflow = if (collapsed) TextOverflow.Ellipsis else TextOverflow.Clip,
            modifier = Modifier.weight(1f),
        )
        if (inProgress) {
            Spacer(Modifier.width(8.dp))
            CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp, color = Palette.Accent)
        }
        Spacer(Modifier.width(6.dp))
        Icon(
            imageVector = if (collapsed) TermFoldIcons.ChevronRight else TermFoldIcons.ChevronDown,
            contentDescription = stringResource(
                if (collapsed) R.string.acp_tool_expand else R.string.acp_tool_collapse,
            ),
            tint = Palette.TextFaint,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
fun ToolCallOutputPane(item: AcpItem.ToolCall) {
    val scroll = rememberScrollState()
    val hScroll = rememberScrollState()
    val live = item.status == "in_progress"
    val text = item.detail.trim()
    LaunchedEffect(text, live) {
        if (live) scroll.scrollTo(scroll.maxValue)
    }
    SelectionContainer {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 25.dp, bottom = 4.dp)
                .heightIn(min = 40.dp, max = 220.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Palette.Card)
                .padding(10.dp)
                .verticalScroll(scroll)
                .horizontalScroll(hScroll),
        ) {
            val display = when {
                text.isNotBlank() -> text
                live -> stringResource(R.string.acp_tool_output_waiting)
                toolCallExpectsTextBody(item) ->
                    stringResource(R.string.acp_tool_output_mcp_not_streamed)
                else -> stringResource(R.string.acp_tool_output_empty)
            }
            Text(
                text = display,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = Mono,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                ),
                color = if (text.isBlank()) Palette.TextFaint else Palette.TermOut,
            )
        }
    }
}

fun toolCallLabel(item: AcpItem.ToolCall, workspaceDir: String): String {
    val path = item.paths.firstOrNull() ?: return item.title
    val shown = path.removePrefix(workspaceDir.trimEnd('/') + "/").ifBlank { path }
    val name = shown.substringAfterLast('/')
    if (item.title.contains(shown) || item.title.contains(name)) return item.title
    val more = if (item.paths.size > 1) " +${item.paths.size - 1}" else ""
    return "${item.title}  $shown$more"
}

fun toolCallShowsOutputPane(item: AcpItem.ToolCall): Boolean {
    if (item.detail.isNotBlank()) return true
    if (item.status == "in_progress" || item.status == "pending") return true
    if (item.fileDiffs.isNotEmpty()) return false
    return toolCallExpectsTextBody(item)
}

/** Tools that should show an output area even when Cursor sends no standard diff block. */
fun toolCallExpectsTextBody(item: AcpItem.ToolCall): Boolean {
    if (isSubagentToolCall(item)) return true
    val t = item.title.lowercase()
    val k = item.kind.lowercase()
    if (k in listOf("execute", "command", "search", "fetch", "read", "mcp")) return true
    if (t.contains("mcp") || t.contains("memory") || t.contains("codegraph")) return true
    if (t.contains("terminal") || t.contains("shell") || t.contains("search") || t.contains("web")) {
        return true
    }
    // MCP tools often appear as `server: tool_name` with kind `other`.
    if (':' in item.title) {
        val head = t.substringBefore(':').trim()
        if (head !in listOf("read", "write", "edit", "delete", "move")) return true
    }
    return false
}

private fun isSubagentToolCall(item: AcpItem.ToolCall): Boolean {
    val t = item.title.lowercase()
    val k = item.kind.lowercase()
    return k in listOf("subagent", "task", "delegate") ||
        t.contains("subagent") ||
        t.contains("sub-agent") ||
        t.contains("run task") ||
        (t.startsWith("task ") || t == "task")
}

fun toolCallIcon(item: AcpItem.ToolCall): ImageVector {
    val t = item.title.lowercase()
    return when {
        isSubagentToolCall(item) -> TermFoldIcons.Sparkle
        item.kind in listOf("execute", "command") || t.contains("terminal") || t.contains("shell") ->
            TermFoldIcons.Terminal
        item.kind in listOf("search", "fetch") || t.contains("search") || t.contains("web") ->
            TermFoldIcons.Search
        item.kind == "read" || t.startsWith("read ") -> TermFoldIcons.FileText
        t.contains("mcp") || t.contains("memory") || t.contains("codegraph") ||
            item.kind.equals("mcp", ignoreCase = true) ||
            (':' in item.title && toolCallExpectsTextBody(item)) ->
            TermFoldIcons.Globe
        item.kind in listOf("edit", "delete", "move") -> TermFoldIcons.Pencil
        else -> TermFoldIcons.Gear
    }
}
