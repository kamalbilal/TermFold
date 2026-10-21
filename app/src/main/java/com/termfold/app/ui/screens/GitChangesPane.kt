package com.termfold.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.termfold.app.ui.theme.Motion
import com.termfold.app.R
import com.termfold.app.gitworkspace.GitChangeFile
import com.termfold.app.gitworkspace.GitChangeSummary
import com.termfold.app.gitworkspace.GitChanges
import com.termfold.app.gitworkspace.GitFileStatus
import com.termfold.app.gitworkspace.GitWorkspace
import com.termfold.app.gitworkspace.CachedDiffPresentation
import com.termfold.app.gitworkspace.DiffDisplayRow
import com.termfold.app.gitworkspace.MAX_CONCURRENT_DIFF_LOADS
import com.termfold.app.gitworkspace.applyContextFolding
import com.termfold.app.gitworkspace.prepareDiffPresentation
import com.termfold.app.gitworkspace.rowsForMode
import com.termfold.app.ui.components.BareIconButton
import com.termfold.app.ui.components.currentWindowWidth
import com.termfold.app.ui.components.isWide
import com.termfold.app.ui.theme.GitDiffColors
import com.termfold.app.ui.theme.Mono
import com.termfold.app.ui.theme.Palette
import com.termfold.app.ui.theme.TermFoldIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun fileKey(file: GitChangeFile): String = "${file.staged}:${file.path}"

private fun visibleDiffRows(
    presentation: CachedDiffPresentation,
    sideBySide: Boolean,
    fileKey: String,
    expandedContextFolds: Set<String>,
): List<DiffDisplayRow> {
    val base = rowsForMode(presentation, sideBySide)
    return applyContextFolding(base, "$fileKey:", expandedContextFolds)
}

private fun diffLazyItemCount(
    presentation: CachedDiffPresentation?,
    diff: String?,
    loading: Boolean,
    sideBySide: Boolean,
    fileKey: String,
    expandedContextFolds: Set<String>,
): Int {
    if (loading) return 1
    if (diff.isNullOrEmpty()) return 1
    if (presentation == null) return 1
    if (presentation.isBinary) return 1
    if (presentation.fallbackLines.isNotEmpty()) return presentation.fallbackLines.size
    val rows = visibleDiffRows(presentation, sideBySide, fileKey, expandedContextFolds)
    return if (rows.isEmpty()) 1 else rows.size
}

private fun lazyHeaderIndex(
    files: List<GitChangeFile>,
    targetKey: String,
    expanded: SnapshotStateMap<String, Boolean>,
    presentationCache: SnapshotStateMap<String, CachedDiffPresentation>,
    diffCache: SnapshotStateMap<String, String>,
    diffLoading: SnapshotStateMap<String, Boolean>,
    sideBySide: Boolean,
    expandedContextFolds: Set<String>,
): Int {
    if (files.isEmpty()) return -1
    var index = 0
    for (file in files) {
        val key = fileKey(file)
        if (key == targetKey) return index
        index++
        if (expanded[key] != false) {
            index += diffLazyItemCount(
                presentation = presentationCache[key],
                diff = diffCache[key],
                loading = diffLoading[key] == true,
                sideBySide = sideBySide,
                fileKey = key,
                expandedContextFolds = expandedContextFolds,
            )
            index++
        }
    }
    return -1
}

@Composable
fun GitChangesPane(
    state: GitChanges.State,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val guestPath = state.guestPath ?: return
    val wide = currentWindowWidth().isWide
    var summary by remember { mutableStateOf<GitChangeSummary?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val diffCache = remember { mutableStateMapOf<String, String>() }
    val presentationCache = remember { mutableStateMapOf<String, CachedDiffPresentation>() }
    val diffLoading = remember { mutableStateMapOf<String, Boolean>() }
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    var selectedKey by remember { mutableStateOf<String?>(null) }
    var mobileSidebarOpen by remember { mutableStateOf(false) }
    var diffLoadGeneration by remember { mutableIntStateOf(0) }

    LaunchedEffect(guestPath, state.nonce) {
        loading = true
        error = null
        diffCache.clear()
        presentationCache.clear()
        diffLoading.clear()
        expanded.clear()
        selectedKey = null
        mobileSidebarOpen = false
        diffLoadGeneration = 0
        summary = withContext(Dispatchers.IO) {
            runCatching { GitWorkspace.loadChangeSummary(context, guestPath) }.getOrElse {
                error = it.message
                null
            }
        }
        loading = false
        summary?.let { s ->
            val all = s.staged + s.unstaged
            all.forEach { f -> expanded[fileKey(f)] = true }
            selectedKey = all.firstOrNull()?.let { fileKey(it) }
        }
    }

    BackHandler(enabled = !wide && mobileSidebarOpen) { mobileSidebarOpen = false }

    Column(modifier.background(Palette.Bg)) {
        PaneHeader(
            onClose = onClose,
            showSidebarToggle = !wide,
            onToggleSidebar = { mobileSidebarOpen = !mobileSidebarOpen },
        )
        HorizontalDivider(color = Palette.BorderSoft)
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Palette.Accent, modifier = Modifier.size(28.dp))
            }
            return@Column
        }
        error?.let { msg ->
            Text(msg, color = Palette.Pink, modifier = Modifier.padding(16.dp))
        }
        summary?.let { s ->
            SyncLine(s)
            HorizontalDivider(color = Palette.BorderSoft)
            val allFiles = remember(s) { s.staged + s.unstaged }
            val onSidebarSelect: (GitChangeFile) -> Unit = { file ->
                val key = fileKey(file)
                selectedKey = key
                expanded[key] = true
                if (!wide) mobileSidebarOpen = false
            }
            Box(Modifier.fillMaxSize()) {
                if (wide) {
                    Row(Modifier.fillMaxSize()) {
                        ChangeSidebar(
                            summary = s,
                            selectedKey = selectedKey,
                            modifier = Modifier
                                .width(200.dp)
                                .fillMaxHeight()
                                .background(Palette.Card.copy(alpha = 0.35f)),
                            onSelect = onSidebarSelect,
                        )
                        Box(
                            Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(Palette.BorderSoft),
                        )
                        FileCardsColumn(
                            files = allFiles,
                            expanded = expanded,
                            selectedKey = selectedKey,
                            diffCache = diffCache,
                            presentationCache = presentationCache,
                            diffLoading = diffLoading,
                            sideBySide = true,
                            onToggle = { file ->
                                val key = fileKey(file)
                                val wasExpanded = expanded[key] != false
                                expanded[key] = !wasExpanded
                                if (!wasExpanded) diffLoadGeneration++
                            },
                            onSelect = { selectedKey = fileKey(it) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(8.dp),
                            guestPath = guestPath,
                            reloadNonce = state.nonce,
                            diffLoadGeneration = diffLoadGeneration,
                        )
                    }
                } else {
                    FileCardsColumn(
                        files = allFiles,
                        expanded = expanded,
                        selectedKey = selectedKey,
                        diffCache = diffCache,
                        presentationCache = presentationCache,
                        diffLoading = diffLoading,
                        sideBySide = false,
                        onToggle = { file ->
                            val key = fileKey(file)
                            val wasExpanded = expanded[key] != false
                            expanded[key] = !wasExpanded
                            if (!wasExpanded) diffLoadGeneration++
                        },
                        onSelect = { selectedKey = fileKey(it) },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        guestPath = guestPath,
                        reloadNonce = state.nonce,
                        diffLoadGeneration = diffLoadGeneration,
                    )
                    GitChangesDrawerOverlay(
                        visible = mobileSidebarOpen,
                        onDismiss = { mobileSidebarOpen = false },
                    ) { panelMod ->
                        ChangeSidebar(
                            summary = s,
                            selectedKey = selectedKey,
                            modifier = panelMod,
                            onSelect = onSidebarSelect,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GitChangesDrawerOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    panel: @Composable (Modifier) -> Unit,
) {
    AnimatedVisibility(visible = visible, enter = fadeIn(Motion.standard()), exit = fadeOut(Motion.quick())) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x99000000))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )
    }
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(Motion.slow()) { -it },
        exit = slideOutHorizontally(Motion.standard()) { -it },
    ) {
        BoxWithConstraints {
            panel(
                Modifier
                    .width(minOf(maxWidth * 0.86f, 320.dp))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp))
                    .background(Palette.Card)
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Top + WindowInsetsSides.Bottom + WindowInsetsSides.Start,
                        ),
                    ),
            )
        }
    }
}

@Composable
private fun GitDiffLoadCoordinator(
    files: List<GitChangeFile>,
    guestPath: String,
    reloadNonce: Int,
    diffLoadGeneration: Int,
    expanded: SnapshotStateMap<String, Boolean>,
    diffCache: SnapshotStateMap<String, String>,
    presentationCache: SnapshotStateMap<String, CachedDiffPresentation>,
    diffLoading: SnapshotStateMap<String, Boolean>,
) {
    val context = LocalContext.current
    LaunchedEffect(guestPath, reloadNonce, diffLoadGeneration, files) {
        val pending = files.filter { expanded[fileKey(it)] != false }
            .filter { !diffCache.containsKey(fileKey(it)) }
        if (pending.isEmpty()) return@LaunchedEffect
        pending.chunked(MAX_CONCURRENT_DIFF_LOADS).forEach { batch ->
            coroutineScope {
                batch.map { file ->
                    async(Dispatchers.IO) {
                        val key = fileKey(file)
                        if (diffCache.containsKey(key)) return@async
                        diffLoading[key] = true
                        try {
                            val text = GitWorkspace.loadUnifiedDiff(
                                context,
                                guestPath,
                                file.path,
                                staged = file.staged,
                                untracked = file.status == GitFileStatus.UNTRACKED,
                            ) ?: ""
                            diffCache[key] = text
                            presentationCache[key] = prepareDiffPresentation(text)
                        } finally {
                            diffLoading[key] = false
                        }
                    }
                }.awaitAll()
            }
        }
    }
}

@Composable
private fun PaneHeader(
    onClose: () -> Unit,
    showSidebarToggle: Boolean = false,
    onToggleSidebar: () -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BareIconButton(
            icon = TermFoldIcons.Close,
            contentDescription = stringResource(R.string.action_close),
            onClick = onClose,
            tint = Palette.TextDim,
            size = 40,
        )
        if (showSidebarToggle) {
            BareIconButton(
                icon = TermFoldIcons.ListView,
                contentDescription = stringResource(R.string.git_changes_toggle_files),
                onClick = onToggleSidebar,
                tint = Palette.TextDim,
                size = 40,
            )
        }
        Text(
            stringResource(R.string.git_changes_title),
            style = MaterialTheme.typography.titleMedium,
            color = Palette.Text,
            modifier = Modifier.weight(1f),
        )
        BareIconButton(
            icon = TermFoldIcons.Refresh,
            contentDescription = stringResource(R.string.git_changes_refresh),
            onClick = { GitChanges.refresh() },
            tint = Palette.TextDim,
            size = 40,
        )
    }
}

@Composable
private fun FileCardsColumn(
    files: List<GitChangeFile>,
    expanded: SnapshotStateMap<String, Boolean>,
    selectedKey: String?,
    diffCache: SnapshotStateMap<String, String>,
    presentationCache: SnapshotStateMap<String, CachedDiffPresentation>,
    diffLoading: SnapshotStateMap<String, Boolean>,
    sideBySide: Boolean,
    onToggle: (GitChangeFile) -> Unit,
    onSelect: (GitChangeFile) -> Unit,
    modifier: Modifier = Modifier,
    guestPath: String,
    reloadNonce: Int,
    diffLoadGeneration: Int,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val diffTextStyle = gitDiffLineTextStyle()
    var expandedContextFolds by remember(reloadNonce) { mutableStateOf(setOf<String>()) }
    GitDiffLoadCoordinator(
        files = files,
        guestPath = guestPath,
        reloadNonce = reloadNonce,
        diffLoadGeneration = diffLoadGeneration,
        expanded = expanded,
        diffCache = diffCache,
        presentationCache = presentationCache,
        diffLoading = diffLoading,
    )
    LaunchedEffect(selectedKey, expandedContextFolds) {
        val key = selectedKey ?: return@LaunchedEffect
        val idx = lazyHeaderIndex(
            files = files,
            targetKey = key,
            expanded = expanded,
            presentationCache = presentationCache,
            diffCache = diffCache,
            diffLoading = diffLoading,
            sideBySide = sideBySide,
            expandedContextFolds = expandedContextFolds,
        )
        if (idx >= 0) scope.launch { listState.scrollToItem(idx) }
    }
    LazyColumn(state = listState, modifier = modifier) {
        if (files.isEmpty()) {
            item(key = "clean") {
                Text(
                    stringResource(R.string.git_changes_clean),
                    color = Palette.TextFaint,
                    modifier = Modifier.padding(20.dp),
                )
            }
        }
        files.forEach { file ->
            val key = fileKey(file)
            val isExpanded = expanded[key] != false
            item(key = "hdr-$key") {
                FileCardHeader(
                    file = file,
                    expanded = isExpanded,
                    selected = key == selectedKey,
                    onHeaderClick = {
                        onSelect(file)
                        onToggle(file)
                    },
                )
            }
            if (isExpanded) {
                val loading = diffLoading[key] == true
                val diff = diffCache[key]
                when {
                    loading -> {
                        item(key = "load-$key") {
                            Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Palette.Accent, modifier = Modifier.size(22.dp))
                            }
                        }
                    }
                    diff.isNullOrEmpty() -> {
                        item(key = "empty-$key") {
                            Text(
                                stringResource(R.string.git_changes_no_diff),
                                color = Palette.TextFaint,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                    }
                    else -> {
                        val presentation = presentationCache[key]
                        if (presentation?.isBinary == true) {
                            item(key = "bin-$key") {
                                Text(
                                    "Binary file (no text diff)",
                                    color = Palette.TextFaint,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                )
                            }
                        } else if (presentation != null && presentation.fallbackLines.isNotEmpty()) {
                            val lines = presentation.fallbackLines
                            items(
                                count = lines.size,
                                key = { lineIdx -> "fb-$key-$lineIdx" },
                            ) { lineIdx ->
                                GitDiffFallbackLine(lines[lineIdx], diffTextStyle)
                            }
                        } else if (presentation != null) {
                            val displayRows = visibleDiffRows(
                                presentation,
                                sideBySide,
                                key,
                                expandedContextFolds,
                            )
                            items(
                                count = displayRows.size,
                                key = { rowIdx -> "dr-$key-$rowIdx" },
                            ) { rowIdx ->
                                GitDiffDisplayRow(
                                    row = displayRows[rowIdx],
                                    textStyle = diffTextStyle,
                                    onExpandContextFold = { foldKey ->
                                        expandedContextFolds = expandedContextFolds + foldKey
                                    },
                                )
                            }
                        } else {
                            item(key = "prep-$key") {
                                Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = Palette.Accent, modifier = Modifier.size(22.dp))
                                }
                            }
                        }
                    }
                }
                item(key = "gap-$key") {
                    SpacerBetweenFiles()
                }
            }
        }
    }
}

@Composable
private fun SpacerBetweenFiles() {
    Box(Modifier.fillMaxWidth().padding(bottom = 8.dp))
}

@Composable
private fun FileCardHeader(
    file: GitChangeFile,
    expanded: Boolean,
    selected: Boolean,
    onHeaderClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    val borderColor = if (selected) Palette.Accent.copy(alpha = 0.5f) else Palette.BorderSoft
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(shape)
            .border(1.dp, borderColor, shape)
            .background(Palette.Card.copy(alpha = 0.4f)),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onHeaderClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (expanded) "▼" else "▶",
                style = MaterialTheme.typography.labelSmall,
                color = Palette.TextDim,
                modifier = Modifier.width(16.dp),
            )
            Text(
                statusLetter(file.status),
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = Mono),
                color = statusColor(file.status),
                modifier = Modifier.width(18.dp),
            )
            Text(
                file.path,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono),
                color = Palette.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            FileStats(file)
        }
        if (expanded) {
            HorizontalDivider(color = Palette.BorderSoft)
        }
    }
}

@Composable
private fun FileStats(file: GitChangeFile) {
    val add = file.additions
    val del = file.deletions
    if (add == null && del == null) return
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (add != null && add > 0) {
            Text("+$add", style = MaterialTheme.typography.labelSmall.copy(fontFamily = Mono), color = GitDiffColors.addFg)
        }
        if (del != null && del > 0) {
            Text("-$del", style = MaterialTheme.typography.labelSmall.copy(fontFamily = Mono), color = GitDiffColors.removeFg)
        }
    }
}

@Composable
private fun ChangeSidebar(
    summary: GitChangeSummary,
    selectedKey: String?,
    modifier: Modifier = Modifier,
    onSelect: (GitChangeFile) -> Unit,
) {
    LazyColumn(modifier = modifier) {
        if (summary.staged.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.git_changes_staged)) }
            items(summary.staged, key = { "s-${it.path}" }) { file ->
                SidebarRow(file, selected = fileKey(file) == selectedKey, onSelect)
            }
        }
        if (summary.unstaged.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.git_changes_unstaged)) }
            items(summary.unstaged, key = { "u-${it.path}" }) { file ->
                SidebarRow(file, selected = fileKey(file) == selectedKey, onSelect)
            }
        }
        if (summary.staged.isEmpty() && summary.unstaged.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.git_changes_clean),
                    color = Palette.TextFaint,
                    modifier = Modifier.padding(20.dp),
                )
            }
        }
    }
}

@Composable
private fun SidebarRow(
    file: GitChangeFile,
    selected: Boolean,
    onSelect: (GitChangeFile) -> Unit,
) {
    val bg = if (selected) Palette.Accent.copy(alpha = 0.12f) else Palette.Bg
    Row(
        Modifier
            .fillMaxWidth()
            .background(bg)
            .clickable { onSelect(file) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            statusLetter(file.status),
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = Mono),
            color = statusColor(file.status),
            modifier = Modifier.width(20.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                file.path.substringAfterLast('/'),
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val parent = file.path.substringBeforeLast('/', "")
            if (parent.isNotEmpty()) {
                Text(
                    parent,
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SyncLine(summary: GitChangeSummary) {
    val upstream = summary.upstream
    if (upstream == null) return
    val out = summary.outgoingCommits ?: 0
    val inc = summary.incomingCommits ?: 0
    if (out == 0 && inc == 0) return
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (out > 0) {
            Text(
                stringResource(R.string.git_changes_outgoing, out),
                style = MaterialTheme.typography.labelSmall,
                color = Palette.TextDim,
            )
        }
        if (inc > 0) {
            Text(
                stringResource(R.string.git_changes_incoming, inc),
                style = MaterialTheme.typography.labelSmall,
                color = Palette.TextDim,
            )
        }
        Text(
            summary.branch,
            style = MaterialTheme.typography.labelSmall,
            color = Palette.TextFaint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = Palette.TextFaint,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

private fun statusLetter(status: GitFileStatus): String = when (status) {
    GitFileStatus.ADDED, GitFileStatus.UNTRACKED -> "A"
    GitFileStatus.MODIFIED -> "M"
    GitFileStatus.DELETED -> "D"
    GitFileStatus.RENAMED -> "R"
    GitFileStatus.COPIED -> "C"
    GitFileStatus.UNMERGED -> "U"
}

private fun statusColor(status: GitFileStatus) = when (status) {
    GitFileStatus.ADDED, GitFileStatus.UNTRACKED -> GitDiffColors.addFg
    GitFileStatus.DELETED -> GitDiffColors.removeFg
    GitFileStatus.MODIFIED -> GitDiffColors.statusModified
    GitFileStatus.RENAMED, GitFileStatus.COPIED -> Palette.TextDim
    GitFileStatus.UNMERGED -> Palette.Pink
}
