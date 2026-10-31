package com.termfold.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ripple
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.termfold.app.R
import com.termfold.app.shell.GuestStorageAnalyzer
import com.termfold.app.shell.GuestStorageCategory
import com.termfold.app.shell.GuestStorageDeletePolicy
import com.termfold.app.shell.GuestStorageEntry
import com.termfold.app.shell.GuestStorageNode
import com.termfold.app.shell.ShellRuntime
import com.termfold.app.shell.toEntry
import com.termfold.app.ui.components.BareIconButton
import com.termfold.app.ui.theme.Mono
import com.termfold.app.ui.theme.Palette
import com.termfold.app.ui.theme.TermFoldIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class VisibleStorageRow(
    val node: GuestStorageNode,
    val depth: Int,
)

@Composable
fun GuestStorageAnalyzerDialog(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(Palette.Bg)
                .statusBarsPadding()
                .navigationBarsPadding(),
            color = Palette.Bg,
        ) {
            GuestStorageAnalyzerContent(onDismiss = onDismiss)
        }
    }
}

@Composable
private fun GuestStorageAnalyzerContent(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rootNodes = remember { mutableStateListOf<GuestStorageNode>() }
    var expandedIds by remember { mutableStateOf(emptySet<String>()) }
    var childrenById by remember { mutableStateOf(emptyMap<String, List<GuestStorageNode>>()) }
    var loadingChildIds by remember { mutableStateOf(emptySet<String>()) }
    var treeEpoch by remember { mutableIntStateOf(0) }
    var statusLine by remember { mutableStateOf<String?>(null) }
    var scanning by remember { mutableStateOf(true) }
    var scanError by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<GuestStorageEntry?>(null) }
    var deleting by remember { mutableStateOf(false) }

    fun resortRoots() {
        val sorted = rootNodes.sortedByDescending { it.bytes }
        rootNodes.clear()
        rootNodes.addAll(sorted)
    }

    fun startScan() {
        scope.launch {
            scanning = true
            scanError = null
            rootNodes.clear()
            expandedIds = emptySet()
            childrenById = emptyMap()
            loadingChildIds = emptySet()
            treeEpoch++
            withContext(Dispatchers.IO) {
                statusLine = context.getString(R.string.storage_analyzer_status_host)
                val host = GuestStorageAnalyzer.scanHostLayer(context)
                withContext(Dispatchers.Main) {
                    rootNodes.addAll(host)
                }
                if (ShellRuntime.isReady(context)) {
                    for (guestPath in listOf("/root", "/var", "/opt", "/usr", "/tmp")) {
                        statusLine = context.getString(R.string.storage_analyzer_status_path, guestPath)
                        val bytes = GuestStorageAnalyzer.measureGuestPath(context, guestPath)
                        if (bytes != null) {
                            val node = GuestStorageAnalyzer.nodeFromGuestPath(context, guestPath, bytes)
                            withContext(Dispatchers.Main) {
                                rootNodes.add(node)
                                resortRoots()
                            }
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        scanError = context.getString(R.string.storage_analyzer_shell_not_ready_short)
                    }
                }
            }
            statusLine = null
            scanning = false
        }
    }

    fun loadChildren(node: GuestStorageNode) {
        val guest = node.guestPath ?: return
        if (childrenById.containsKey(node.id) || node.id in loadingChildIds) return
        loadingChildIds = loadingChildIds + node.id
        treeEpoch++
        scope.launch {
            statusLine = context.getString(R.string.storage_analyzer_status_children, guest)
            val kids = withContext(Dispatchers.IO) {
                GuestStorageAnalyzer.loadChildren(context, guest)
            }
            childrenById = childrenById + (node.id to kids)
            loadingChildIds = loadingChildIds - node.id
            statusLine = null
            treeEpoch++
        }
    }

    fun toggleExpand(node: GuestStorageNode) {
        if (!node.expandable) return
        val opening = node.id !in expandedIds
        expandedIds = if (opening) expandedIds + node.id else expandedIds - node.id
        treeEpoch++
        if (opening && !childrenById.containsKey(node.id)) {
            loadChildren(node)
        }
    }

    LaunchedEffect(Unit) { startScan() }

    val totalBytes = rootNodes.sumOf { it.bytes }
    @Suppress("UNUSED_VARIABLE")
    val treeSnapshot = treeEpoch
    val visibleRows = buildVisibleRows(
        roots = rootNodes,
        expandedIds = expandedIds,
        childrenById = childrenById,
        loadingChildIds = loadingChildIds,
    )

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BareIconButton(
                icon = TermFoldIcons.Back,
                contentDescription = stringResource(R.string.cd_back),
                onClick = onDismiss,
            )
            Text(
                text = stringResource(R.string.storage_analyzer_title),
                style = MaterialTheme.typography.titleLarge,
                color = Palette.Text,
                modifier = Modifier.weight(1f),
            )
            BareIconButton(
                icon = TermFoldIcons.Refresh,
                contentDescription = stringResource(R.string.storage_analyzer_refresh),
                onClick = { if (!scanning && !deleting) startScan() },
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                StorageSummaryCard(
                    totalBytes = totalBytes,
                    scanning = scanning,
                    statusLine = statusLine,
                )
            }
            if (scanError != null) {
                item {
                    Text(scanError!!, color = Palette.Pink, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (rootNodes.isEmpty() && scanning) {
                item {
                    Row(Modifier.padding(vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Palette.Accent,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            statusLine ?: stringResource(R.string.storage_analyzer_scanning),
                            color = Palette.TextDim,
                        )
                    }
                }
            }
            items(visibleRows, key = { "${it.node.id}-${it.depth}" }) { row ->
                StorageTreeRow(
                    node = row.node,
                    depth = row.depth,
                    totalBytes = totalBytes.coerceAtLeast(1L),
                    expanded = row.node.id in expandedIds,
                    childrenLoading = row.node.id in loadingChildIds,
                    onToggleExpand = { toggleExpand(row.node) },
                    onDelete = { pendingDelete = row.node.toEntry() },
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { if (!deleting) pendingDelete = null },
            title = { Text(stringResource(R.string.storage_analyzer_delete_title)) },
            text = {
                Column {
                    Text(
                        stringResource(
                            R.string.storage_analyzer_delete_body,
                            entry.label,
                            formatBytes(entry.bytes),
                        ),
                        color = Palette.TextDim,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        entry.subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = Mono),
                        color = Palette.TextFaint,
                    )
                    if (entry.deletePolicy == GuestStorageDeletePolicy.PROJECT) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.storage_analyzer_delete_project_warn),
                            color = Palette.Pink,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !deleting,
                    onClick = {
                        scope.launch {
                            deleting = true
                            val err = withContext(Dispatchers.IO) {
                                GuestStorageAnalyzer.delete(context, entry)
                            }
                            deleting = false
                            pendingDelete = null
                            if (err != null) {
                                scanError = err
                            } else {
                                scanError = null
                                invalidateShellSizeCache()
                                startScan()
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.storage_analyzer_delete_confirm), color = Palette.Pink)
                }
            },
            dismissButton = {
                TextButton(enabled = !deleting, onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

private fun buildVisibleRows(
    roots: SnapshotStateList<GuestStorageNode>,
    expandedIds: Set<String>,
    childrenById: Map<String, List<GuestStorageNode>>,
    loadingChildIds: Set<String>,
): List<VisibleStorageRow> {
    val out = mutableListOf<VisibleStorageRow>()
    fun walk(nodes: List<GuestStorageNode>, depth: Int) {
        for (node in nodes.sortedByDescending { it.bytes }) {
            out += VisibleStorageRow(node, depth)
            if (node.id !in expandedIds) continue
            when {
                node.id in loadingChildIds -> {
                    out += loadingRow(node.id, depth + 1)
                }
                childrenById.containsKey(node.id) -> {
                    val kids = childrenById[node.id].orEmpty()
                    if (kids.isEmpty()) {
                        out += emptyRow(node.id, depth + 1)
                    } else {
                        walk(kids, depth + 1)
                    }
                }
                else -> out += loadingRow(node.id, depth + 1)
            }
        }
    }
    walk(roots, 0)
    return out
}

private fun loadingRow(parentId: String, depth: Int) = VisibleStorageRow(
    node = GuestStorageNode(
        id = "loading-$parentId",
        label = "…",
        subtitle = "",
        bytes = 0L,
        category = GuestStorageCategory.SYSTEM,
        deletePolicy = GuestStorageDeletePolicy.NONE,
    ),
    depth = depth,
)

private fun emptyRow(parentId: String, depth: Int) = VisibleStorageRow(
    node = GuestStorageNode(
        id = "empty-$parentId",
        label = "—",
        subtitle = "",
        bytes = 0L,
        category = GuestStorageCategory.SYSTEM,
        deletePolicy = GuestStorageDeletePolicy.NONE,
        expandable = false,
    ),
    depth = depth,
)

@Composable
private fun StorageSummaryCard(
    totalBytes: Long,
    scanning: Boolean,
    statusLine: String?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Palette.Card)
            .padding(20.dp),
    ) {
        Text(
            stringResource(R.string.storage_analyzer_total),
            style = MaterialTheme.typography.labelLarge,
            color = Palette.TextFaint,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (totalBytes > 0L) formatBytes(totalBytes) else "—",
            style = MaterialTheme.typography.headlineMedium,
            color = Palette.Text,
        )
        if (scanning || statusLine != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                statusLine ?: stringResource(R.string.storage_analyzer_scanning),
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextDim,
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = Palette.Accent,
                trackColor = Palette.BorderSoft,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.storage_analyzer_drill_hint),
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextFaint,
        )
    }
}

@Composable
private fun StorageTreeRow(
    node: GuestStorageNode,
    depth: Int,
    totalBytes: Long,
    expanded: Boolean,
    childrenLoading: Boolean,
    onToggleExpand: () -> Unit,
    onDelete: () -> Unit,
) {
    if (node.id.startsWith("loading-") || node.label == "…") {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = (16 * depth).dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.5.dp, color = Palette.Accent)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.storage_analyzer_loading_children), color = Palette.TextFaint)
        }
        return
    }
    if (node.id.startsWith("empty-")) {
        Text(
            stringResource(R.string.storage_analyzer_no_children),
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextFaint,
            modifier = Modifier.padding(start = (28 * depth).dp, top = 4.dp, bottom = 8.dp),
        )
        return
    }
    val fraction = (node.bytes.toFloat() / totalBytes).coerceIn(0.01f, 1f)
    val canDelete = node.deletePolicy != GuestStorageDeletePolicy.NONE
    val expandInteraction = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (12 * depth).dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.Card)
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (node.expandable) {
                        Modifier.clickable(
                            interactionSource = expandInteraction,
                            indication = ripple(bounded = true),
                            onClick = onToggleExpand,
                        )
                    } else {
                        Modifier
                    },
                ),
            verticalAlignment = Alignment.Top,
        ) {
            if (node.expandable) {
                Icon(
                    imageVector = if (expanded) TermFoldIcons.ChevronDown else TermFoldIcons.ChevronRight,
                    contentDescription = null,
                    tint = Palette.TextFaint,
                    modifier = Modifier
                        .size(18.dp)
                        .padding(top = 2.dp),
                )
                Spacer(Modifier.width(8.dp))
            } else {
                Spacer(Modifier.width(26.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    node.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = Palette.Text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    node.subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = Mono),
                    color = Palette.TextFaint,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatBytes(node.bytes),
                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = Mono),
                    color = Palette.Text,
                )
                if (node.bytes > 0) {
                    Text(
                        stringResource(
                            R.string.storage_analyzer_percent,
                            (100f * node.bytes / totalBytes).coerceAtMost(999f),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.TextFaint,
                    )
                }
            }
        }
        if (node.bytes > 0) {
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = categoryColor(node.category),
                trackColor = Palette.BorderSoft,
            )
        }
        if (canDelete) {
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDelete) {
                    Text(stringResource(R.string.storage_analyzer_free_space), color = Palette.Pink)
                }
            }
        }
        if (childrenLoading && expanded) {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = Palette.Accent,
                trackColor = Palette.BorderSoft,
            )
        }
    }
}

@Composable
private fun categoryColor(category: GuestStorageCategory) = when (category) {
    GuestStorageCategory.PROJECT -> Palette.Accent
    GuestStorageCategory.CACHE -> Palette.Blue
    GuestStorageCategory.TOOLCHAIN -> Palette.Yellow
    GuestStorageCategory.SYSTEM -> Palette.TextFaint
    GuestStorageCategory.HOST -> Palette.Pink.copy(alpha = 0.85f)
}

@Composable
fun StorageAnalyzerSettingsEntry(onOpen: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Palette.Card)
            .clickable(onClick = onOpen)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.storage_analyzer_settings_title),
                style = MaterialTheme.typography.bodyLarge,
                color = Palette.Text,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(R.string.storage_analyzer_settings_desc),
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextDim,
            )
        }
        Icon(
            TermFoldIcons.ChevronRight,
            contentDescription = null,
            tint = Palette.TextFaint,
            modifier = Modifier.size(20.dp),
        )
    }
}
