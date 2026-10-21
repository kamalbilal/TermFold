package com.termfold.app.ui.screens



import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.foundation.layout.Arrangement

import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.layout.Row

import androidx.compose.foundation.layout.Spacer

import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.foundation.layout.height

import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.layout.size

import androidx.compose.foundation.layout.width

import androidx.compose.material3.CircularProgressIndicator

import androidx.compose.material3.Icon

import androidx.compose.ui.Alignment

import androidx.compose.foundation.lazy.LazyColumn

import androidx.compose.foundation.lazy.items

import androidx.compose.material3.AlertDialog

import androidx.compose.material3.LinearProgressIndicator

import androidx.compose.material3.MaterialTheme

import androidx.compose.material3.Text

import androidx.compose.material3.TextButton

import androidx.compose.runtime.Composable

import androidx.compose.runtime.LaunchedEffect

import androidx.compose.runtime.getValue

import androidx.compose.runtime.mutableIntStateOf

import androidx.compose.runtime.mutableStateOf

import androidx.compose.runtime.remember

import androidx.compose.runtime.rememberCoroutineScope

import androidx.compose.runtime.setValue

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow

import androidx.compose.ui.res.stringResource

import androidx.compose.ui.unit.dp

import com.termfold.app.R

import com.termfold.app.core.Folder

import com.termfold.app.acp.ComposerFuzzyMatch

import com.termfold.app.gitworkspace.BranchListCache

import com.termfold.app.gitworkspace.BranchNames
import com.termfold.app.gitworkspace.BranchRecentsStore

import com.termfold.app.gitworkspace.CheckoutMode

import com.termfold.app.gitworkspace.GitWorkspace

import com.termfold.app.gitworkspace.ProjectWorkspaces

import com.termfold.app.gitworkspace.WorkspaceIds

import com.termfold.app.gitworkspace.WorkspaceRecord

import com.termfold.app.gitworkspace.WorkspaceStore

import com.termfold.app.ui.components.BareIconButton

import com.termfold.app.ui.theme.Palette

import com.termfold.app.ui.theme.TermFoldIcons

import kotlinx.coroutines.CancellationException

import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.withContext

import kotlinx.coroutines.Job

import kotlinx.coroutines.launch

import kotlin.coroutines.coroutineContext



@Composable

fun WorkspaceProvisionDialog(

    folder: Folder,

    branch: String,

    sessionWorkspaceId: String,

    onDismiss: () -> Unit,

    onComplete: (workspaceId: String) -> Unit,

    onCancelled: () -> Unit,

) {

    val context = androidx.compose.ui.platform.LocalContext.current

    var percent by remember { mutableIntStateOf(0) }

    var failed by remember { mutableStateOf<String?>(null) }

    var attempt by remember { mutableIntStateOf(0) }

    var provisionJob by remember { mutableStateOf<Job?>(null) }

    val goldenGuest = remember(folder.id) { WorkspaceStore.goldenGuestPath(context, folder) }

    val branchRef = remember(branch) { BranchNames.canonicalLocalName(branch) }

    LaunchedEffect(sessionWorkspaceId, branchRef, attempt) {

        provisionJob?.cancel()

        failed = null

        percent = 0

        provisionJob = coroutineContext[Job]

        val record = WorkspaceRecord(

            id = sessionWorkspaceId,

            goldenProjectId = folder.id,

            guestPath = ProjectWorkspaces.sessionGuestPath(goldenGuest, sessionWorkspaceId),

            createdAt = System.currentTimeMillis(),

            sourceBranch = branchRef,

            lastKnownBranch = branchRef,

            complete = false,

        )

        WorkspaceStore.insert(context, record)

        try {

            GitWorkspace.provisionWorkspace(

                context = context,

                goldenGuestPath = goldenGuest,

                branch = branchRef,

                sessionWorkspaceId = sessionWorkspaceId,

                onProgress = { percent = it.percent },

            )

            WorkspaceStore.insert(context, record.copy(complete = true))

            BranchRecentsStore.remember(context, record.guestPath, branchRef)

            onComplete(sessionWorkspaceId)

        } catch (e: CancellationException) {

            GitWorkspace.deletePartialWorkspace(context, goldenGuest, record.guestPath)

            WorkspaceStore.remove(context, folder.id, sessionWorkspaceId)

            onCancelled()

        } catch (e: Throwable) {

            GitWorkspace.deletePartialWorkspace(context, goldenGuest, record.guestPath)

            WorkspaceStore.remove(context, folder.id, sessionWorkspaceId)

            failed = e.message ?: e.toString()

        }

    }



    AlertDialog(

        onDismissRequest = {},

        title = { Text(stringResource(R.string.workspace_provision_title)) },

        text = {

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                if (failed != null) {

                    Text(failed!!, color = Palette.Pink, style = MaterialTheme.typography.bodySmall)

                } else {

                    Text(

                        stringResource(R.string.workspace_provision_percent, percent),

                        style = MaterialTheme.typography.bodyMedium,

                    )

                    LinearProgressIndicator(

                        progress = { percent / 100f },

                        modifier = Modifier.fillMaxWidth(),

                    )

                }

            }

        },

        confirmButton = {

            if (failed != null) {

                TextButton(onClick = { attempt++ }) {

                    Text(stringResource(R.string.action_retry))

                }

            }

        },

        dismissButton = {

            if (failed != null) {

                TextButton(onClick = onDismiss) {

                    Text(stringResource(R.string.action_close))

                }

            } else {

                TextButton(onClick = { provisionJob?.cancel() }) {

                    Text(stringResource(R.string.action_cancel))

                }

            }

        },

    )

}



@Composable

fun NewWorkspaceDialog(

    folder: Folder,

    onDismiss: () -> Unit,

    onOpenWorkspace: (workspaceId: String) -> Unit,

    onCreateFromBranch: (branch: String, newSessionId: String) -> Unit,

) {

    val context = androidx.compose.ui.platform.LocalContext.current

    var query by remember { mutableStateOf("") }

    var branchRefreshNonce by remember { mutableIntStateOf(0) }

    var instances by remember { mutableStateOf<List<WorkspaceInstanceRow>>(emptyList()) }

    val goldenGuest = remember(folder.id) { WorkspaceStore.goldenGuestPath(context, folder) }

    val branches = rememberLoadedGoldenBranches(goldenGuest, branchRefreshNonce)

    LaunchedEffect(folder.id) {
        instances = withContext(Dispatchers.IO) {
            WorkspaceStore.listComplete(context, folder.id).map { record ->
                val branch = GitWorkspace.currentBranch(context, record.guestPath) ?: record.lastKnownBranch
                WorkspaceInstanceRow(record.id, record.guestPath, branch)
            }
        }
    }

    val recentBranches = remember(goldenGuest) { BranchRecentsStore.load(context, goldenGuest) }

    val orderedBranches = remember(branches, recentBranches, query) {
        orderBranchesForPicker(branches ?: emptyList(), recentBranches, query)
    }



    AlertDialog(

        onDismissRequest = onDismiss,

        title = { Text(stringResource(R.string.workspace_new_title)) },

        text = {

            Column {

                if (instances.isNotEmpty()) {

                    Text(

                        stringResource(R.string.workspace_recent_instances),

                        style = MaterialTheme.typography.labelMedium,

                        color = Palette.TextDim,

                    )

                    instances.forEach { row ->

                        Row(

                            modifier = Modifier

                                .fillMaxWidth()

                                .clickable {

                                    onOpenWorkspace(row.workspaceId)

                                    onDismiss()

                                }

                                .padding(vertical = 8.dp),

                            horizontalArrangement = Arrangement.SpaceBetween,

                        ) {

                            Text(

                                row.guestPath.substringAfterLast('/'),

                                style = MaterialTheme.typography.bodyMedium,

                                modifier = Modifier.weight(1f),

                            )

                            Text(row.branch, style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)

                        }

                    }

                }

                BranchSearchList(
                    query = query,
                    onQueryChange = { query = it },
                    orderedBranches = orderedBranches,
                    onPickBranch = { branch ->
                        onCreateFromBranch(branch, ProjectWorkspaces.newSessionId())
                        onDismiss()
                    },
                    sectionLabel = stringResource(R.string.workspace_pick_branch),
                    onRefresh = { branchRefreshNonce++ },
                )

            }

        },

        confirmButton = {},

        dismissButton = {

            TextButton(onClick = onDismiss) {

                Text(stringResource(R.string.action_cancel))

            }

        },

    )

}



@Composable
private fun rememberLoadedGoldenBranches(goldenGuest: String, refreshNonce: Int): List<String>? {
    val context = androidx.compose.ui.platform.LocalContext.current
    var branches by remember(goldenGuest) { mutableStateOf<List<String>?>(null) }
    val cacheKey = remember(goldenGuest) { BranchListCache.cacheKey(goldenGuest, null) }
    LaunchedEffect(goldenGuest, refreshNonce) {
        val force = refreshNonce > 0
        if (!force) {
            BranchListCache.get(cacheKey)?.let { cached -> branches = cached }
        }
        if (branches == null || force) {
            val loaded = withContext(Dispatchers.IO) {
                GitWorkspace.listBranches(context, goldenGuest)
            }
            BranchListCache.put(cacheKey, loaded)
            branches = loaded
        }
    }
    return branches
}

@Composable
private fun BranchPickerRefreshTitle(
    title: String,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f))
        BareIconButton(
            icon = TermFoldIcons.Refresh,
            contentDescription = stringResource(R.string.workspace_branch_refresh),
            onClick = onRefresh,
            tint = Palette.TextDim,
            size = 36,
        )
    }
}

@Composable
private fun BranchSearchList(
    query: String,
    onQueryChange: (String) -> Unit,
    orderedBranches: List<String>,
    onPickBranch: (String) -> Unit,
    sectionLabel: String? = null,
    onRefresh: (() -> Unit)? = null,
) {
    if (sectionLabel != null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                sectionLabel,
                style = MaterialTheme.typography.labelMedium,
                color = Palette.TextDim,
                modifier = Modifier.weight(1f),
            )
            if (onRefresh != null) {
                BareIconButton(
                    icon = TermFoldIcons.Refresh,
                    contentDescription = stringResource(R.string.workspace_branch_refresh),
                    onClick = onRefresh,
                    tint = Palette.TextDim,
                    size = 36,
                )
            }
        }
    }
    androidx.compose.material3.OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        placeholder = { Text(stringResource(R.string.workspace_search_branch)) },
        singleLine = true,
    )
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        items(orderedBranches, key = { it }) { branch ->
            Text(
                branch,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPickBranch(branch) }
                    .padding(vertical = 10.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private fun orderBranchesForPicker(
    all: List<String>,
    recent: List<String>,
    query: String,
): List<String> {
    if (query.isBlank()) {
        val recentHead = recent.filter { it in all }
        return recentHead + all.filter { it !in recentHead }
    }
    val recentSet = recent.toSet()
    return all
        .map { name ->
            val score = ComposerFuzzyMatch.score(query, name) +
                if (name in recentSet) 12 else 0
            name to score
        }
        .filter { it.second >= ComposerFuzzyMatch.MIN_SCORE }
        .sortedWith(
            compareByDescending<Pair<String, Int>> { it.second }
                .thenBy { it.first.lowercase() },
        )
        .map { it.first }
}

@Composable

fun BranchPickerDialog(

    guestPath: String,

    goldenProjectId: String,

    workspaceId: String,

    branchCatalogGuestPath: String? = null,

    onDismiss: () -> Unit,

    onSwitched: (String) -> Unit,

) {

    val context = androidx.compose.ui.platform.LocalContext.current

    var query by remember { mutableStateOf("") }

    var branches by remember { mutableStateOf<List<String>?>(null) }

    var dirty by remember { mutableStateOf(false) }

    var pendingBranch by remember { mutableStateOf<String?>(null) }

    var checkoutError by remember { mutableStateOf<String?>(null) }

    var checkingOutBranch by remember { mutableStateOf<String?>(null) }

    var refreshNonce by remember { mutableIntStateOf(0) }

    val scope = rememberCoroutineScope()

    val cacheKey = remember(guestPath, branchCatalogGuestPath) {
        BranchListCache.cacheKey(guestPath, branchCatalogGuestPath)
    }



    LaunchedEffect(guestPath, branchCatalogGuestPath, refreshNonce) {
        val force = refreshNonce > 0
        if (!force) {
            BranchListCache.get(cacheKey)?.let { cached ->
                branches = cached
            }
        }
        if (branches == null || force) {
            val loaded = withContext(Dispatchers.IO) {
                GitWorkspace.listBranchesForPicker(context, guestPath, branchCatalogGuestPath)
            }
            BranchListCache.put(cacheKey, loaded)
            branches = loaded
        }
        dirty = withContext(Dispatchers.IO) { GitWorkspace.isDirty(context, guestPath) }
    }



    val recent = remember(guestPath) { BranchRecentsStore.load(context, guestPath) }

    val ordered = remember(branches, recent, query) {
        orderBranchesForPicker(branches ?: emptyList(), recent, query)
    }



    fun applyCheckout(branch: String, mode: CheckoutMode) {

        if (checkingOutBranch != null) return

        scope.launch {

            checkoutError = null

            checkingOutBranch = branch

            val result = withContext(Dispatchers.IO) {
                GitWorkspace.checkoutBranch(context, guestPath, branch, mode)
            }

            checkingOutBranch = null

            if (result.isSuccess) {

                BranchRecentsStore.remember(context, guestPath, branch)

                BranchListCache.invalidate(cacheKey)

                if (workspaceId != WorkspaceIds.GOLDEN) {

                    WorkspaceStore.updateBranch(context, goldenProjectId, workspaceId, branch)

                }

                onSwitched(branch)

                onDismiss()

            } else {

                checkoutError = result.exceptionOrNull()?.message

            }

        }

    }



    if (pendingBranch != null && dirty) {

        AlertDialog(

            onDismissRequest = { pendingBranch = null },

            title = { Text(stringResource(R.string.workspace_dirty_title)) },

            text = { Text(stringResource(R.string.workspace_dirty_body)) },

            confirmButton = {

                TextButton(onClick = { applyCheckout(pendingBranch!!, CheckoutMode.STASH_THEN_SWITCH); pendingBranch = null }) {

                    Text(stringResource(R.string.workspace_stash_switch))

                }

            },

            dismissButton = {

                TextButton(onClick = { applyCheckout(pendingBranch!!, CheckoutMode.FORCE); pendingBranch = null }) {

                    Text(stringResource(R.string.workspace_force_switch))

                }

            },

        )

        return

    }



    AlertDialog(

        onDismissRequest = onDismiss,

        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.workspace_branch_title),
                    modifier = Modifier.weight(1f),
                )
                if (checkingOutBranch != null) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = Palette.Accent,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                BareIconButton(
                    icon = TermFoldIcons.Refresh,
                    contentDescription = stringResource(R.string.workspace_branch_refresh),
                    onClick = { refreshNonce++ },
                    tint = Palette.TextDim,
                    size = 36,
                )
            }
        },

        text = {

            Column {

                if (checkoutError != null) {

                    Text(checkoutError!!, color = Palette.Pink, style = MaterialTheme.typography.bodySmall)

                }

                androidx.compose.material3.OutlinedTextField(

                    value = query,

                    onValueChange = { query = it },

                    modifier = Modifier.fillMaxWidth(),

                    placeholder = { Text(stringResource(R.string.workspace_search_branch)) },

                    singleLine = true,

                    enabled = checkingOutBranch == null,

                )

                LazyColumn {

                    items(ordered, key = { it }) { branch ->

                        val switching = checkingOutBranch == branch

                        Text(

                            if (switching) "$branch …" else branch,

                            color = if (switching) Palette.TextDim else Palette.Text,

                            modifier = Modifier

                                .fillMaxWidth()

                                .clickable(enabled = checkingOutBranch == null) {

                                    if (dirty) pendingBranch = branch

                                    else applyCheckout(branch, CheckoutMode.FAIL_IF_DIRTY)

                                }

                                .padding(vertical = 10.dp),

                        )

                    }

                }

            }

        },

        confirmButton = {},

        dismissButton = {

            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }

        },

    )

}



private data class WorkspaceInstanceRow(

    val workspaceId: String,

    val guestPath: String,

    val branch: String,

)



@Composable

fun BranchChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    truncate: Boolean = false,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = Palette.Accent,
        maxLines = if (truncate) 1 else Int.MAX_VALUE,
        overflow = if (truncate) TextOverflow.Ellipsis else TextOverflow.Clip,
        modifier = modifier
            .padding(end = 4.dp)
            .clickable(onClick = onClick),
    )
}

/** Read-only branch label for session list rows (worktree copies). */
@Composable
fun SessionBranchChip(label: String, modifier: Modifier = Modifier) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = Palette.Accent,
        modifier = modifier
            .padding(end = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Palette.CardPressed)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/** Shown when adding a Cursor session from the project list. */
@Composable
fun CursorAddModeDialog(
    onDismiss: () -> Unit,
    onSharedProject: () -> Unit,
    onBranchCopy: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cursor_add_mode_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.cursor_add_mode_body))
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onSharedProject, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.cursor_add_mode_shared))
                }
                TextButton(onClick = onBranchCopy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.cursor_add_mode_branch_copy))
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

/** Pick a branch to provision an isolated workspace copy (folder + flow). */
@Composable
fun WorkspaceBranchPickDialog(
    folder: Folder,
    onDismiss: () -> Unit,
    onPickBranch: (String) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var query by remember { mutableStateOf("") }
    var refreshNonce by remember { mutableIntStateOf(0) }
    val goldenGuest = remember(folder.id) { WorkspaceStore.goldenGuestPath(context, folder) }
    val branches = rememberLoadedGoldenBranches(goldenGuest, refreshNonce)

    val recentBranches = remember(goldenGuest) { BranchRecentsStore.load(context, goldenGuest) }
    val orderedBranches = remember(branches, recentBranches, query) {
        orderBranchesForPicker(branches ?: emptyList(), recentBranches, query)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            BranchPickerRefreshTitle(
                title = stringResource(R.string.workspace_pick_branch),
                onRefresh = { refreshNonce++ },
            )
        },
        text = {
            Column {
                BranchSearchList(
                    query = query,
                    onQueryChange = { query = it },
                    orderedBranches = orderedBranches,
                    onPickBranch = { branch ->
                        onPickBranch(branch)
                        onDismiss()
                    },
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}


