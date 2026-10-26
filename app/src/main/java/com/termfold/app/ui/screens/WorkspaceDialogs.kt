package com.termfold.app.ui.screens



import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.foundation.layout.Arrangement

import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.layout.Row

import androidx.compose.foundation.layout.Spacer

import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.foundation.layout.height

import androidx.compose.foundation.layout.heightIn

import androidx.compose.foundation.layout.widthIn

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

import com.termfold.app.acp.BranchPickerOrder

import com.termfold.app.gitworkspace.BRANCH_LIST_ERROR_TIMEOUT
import com.termfold.app.gitworkspace.BranchListCache
import com.termfold.app.gitworkspace.BranchListLoadState
import com.termfold.app.gitworkspace.rememberBranchListLoad

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

    val branchCacheKey = remember(goldenGuest) { BranchListCache.cacheKey(goldenGuest, null) }
    val branchLoadState = rememberBranchListLoad(branchCacheKey, branchRefreshNonce) {
        GitWorkspace.listBranches(context, goldenGuest)
    }

    LaunchedEffect(folder.id) {
        instances = withContext(Dispatchers.IO) {
            WorkspaceStore.listComplete(context, folder.id).map { record ->
                val branch = GitWorkspace.currentBranch(context, record.guestPath) ?: record.lastKnownBranch
                WorkspaceInstanceRow(record.id, record.guestPath, branch)
            }
        }
    }

    val recentBranches = remember(goldenGuest) { BranchRecentsStore.load(context, goldenGuest) }

    val branchList = (branchLoadState as? BranchListLoadState.Ready)?.branches ?: emptyList()
    val orderedBranches = remember(branchList, recentBranches, query) {
        orderBranchesForPicker(branchList, recentBranches, query)
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
                    loadState = branchLoadState,
                    query = query,
                    onQueryChange = { query = it },
                    orderedBranches = orderedBranches,
                    onPickBranch = { branch ->
                        onCreateFromBranch(branch, ProjectWorkspaces.newSessionId())
                        onDismiss()
                    },
                    onRetry = { branchRefreshNonce++ },
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
private fun branchListErrorMessage(message: String): String =
    if (message == BRANCH_LIST_ERROR_TIMEOUT) {
        stringResource(R.string.workspace_branch_load_timeout)
    } else {
        stringResource(R.string.workspace_branch_load_error, message)
    }

@Composable
private fun BranchPickerListBody(
    loadState: BranchListLoadState,
    orderedBranches: List<String>,
    onPickBranch: (String) -> Unit,
    onRetry: () -> Unit,
    listEnabled: Boolean = true,
    branchRow: @Composable (String) -> Unit = { branch ->
        Text(
            branch,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = listEnabled) { onPickBranch(branch) }
                .padding(vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    },
) {
    val listModifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 120.dp, max = 320.dp)
    when (loadState) {
        BranchListLoadState.Loading -> {
            Box(
                modifier = listModifier,
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 2.dp,
                        color = Palette.Accent,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.workspace_branch_loading),
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.TextDim,
                    )
                }
            }
        }
        is BranchListLoadState.Error -> {
            Column(
                modifier = listModifier.padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    branchListErrorMessage(loadState.message),
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.Pink,
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.workspace_branch_refresh))
                }
            }
        }
        is BranchListLoadState.Ready -> {
            if (orderedBranches.isEmpty()) {
                Column(
                    modifier = listModifier.padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        stringResource(R.string.workspace_branch_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.TextDim,
                    )
                    TextButton(onClick = onRetry) {
                        Text(stringResource(R.string.workspace_branch_refresh))
                    }
                }
            } else {
                LazyColumn(modifier = listModifier) {
                    items(orderedBranches, key = { it }) { branch ->
                        branchRow(branch)
                    }
                }
            }
        }
    }
}

@Composable
private fun BranchSearchList(
    loadState: BranchListLoadState,
    query: String,
    onQueryChange: (String) -> Unit,
    orderedBranches: List<String>,
    onPickBranch: (String) -> Unit,
    onRetry: () -> Unit,
    sectionLabel: String? = null,
    onRefresh: (() -> Unit)? = null,
    searchEnabled: Boolean = true,
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
    val canSearch = searchEnabled && loadState is BranchListLoadState.Ready
    androidx.compose.material3.OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        placeholder = { Text(stringResource(R.string.workspace_search_branch)) },
        singleLine = true,
        enabled = canSearch,
    )
    BranchPickerListBody(
        loadState = loadState,
        orderedBranches = orderedBranches,
        onPickBranch = onPickBranch,
        onRetry = onRetry,
        listEnabled = canSearch,
    )
}

private fun orderBranchesForPicker(
    all: List<String>,
    recent: List<String>,
    query: String,
): List<String> = BranchPickerOrder.order(all, recent, query)

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

    var dirty by remember { mutableStateOf(false) }

    var pendingBranch by remember { mutableStateOf<String?>(null) }

    var checkoutError by remember { mutableStateOf<String?>(null) }

    var checkingOutBranch by remember { mutableStateOf<String?>(null) }

    var refreshNonce by remember { mutableIntStateOf(0) }

    val scope = rememberCoroutineScope()

    val cacheKey = remember(guestPath, branchCatalogGuestPath) {
        BranchListCache.cacheKey(guestPath, branchCatalogGuestPath)
    }

    val branchLoadState = rememberBranchListLoad(cacheKey, refreshNonce) {
        GitWorkspace.listBranchesForPicker(context, guestPath, branchCatalogGuestPath)
    }

    LaunchedEffect(guestPath) {
        dirty = withContext(Dispatchers.IO) { GitWorkspace.isDirty(context, guestPath) }
    }

    val recent = remember(guestPath) { BranchRecentsStore.load(context, guestPath) }

    val branchList = (branchLoadState as? BranchListLoadState.Ready)?.branches ?: emptyList()
    val ordered = remember(branchList, recent, query) {
        orderBranchesForPicker(branchList, recent, query)
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
                if (checkingOutBranch != null || branchLoadState is BranchListLoadState.Loading) {
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

                val canSearch = branchLoadState is BranchListLoadState.Ready && checkingOutBranch == null

                androidx.compose.material3.OutlinedTextField(

                    value = query,

                    onValueChange = { query = it },

                    modifier = Modifier.fillMaxWidth(),

                    placeholder = { Text(stringResource(R.string.workspace_search_branch)) },

                    singleLine = true,

                    enabled = canSearch,

                )

                BranchPickerListBody(
                    loadState = branchLoadState,
                    orderedBranches = ordered,
                    onPickBranch = { branch ->
                        if (dirty) pendingBranch = branch
                        else applyCheckout(branch, CheckoutMode.FAIL_IF_DIRTY)
                    },
                    onRetry = { refreshNonce++ },
                    listEnabled = checkingOutBranch == null,
                ) { branch ->
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
    uncommittedChanges: Boolean = false,
) {
    Row(
        modifier = modifier
            .then(if (truncate) Modifier.widthIn(min = 0.dp) else Modifier)
            .padding(end = 4.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Palette.Accent,
            maxLines = if (truncate) 1 else Int.MAX_VALUE,
            overflow = if (truncate) TextOverflow.Ellipsis else TextOverflow.Clip,
            modifier = if (truncate) Modifier.weight(1f, fill = true).widthIn(min = 0.dp) else Modifier,
        )
        if (uncommittedChanges) {
            Text(
                text = "*",
                style = MaterialTheme.typography.labelSmall,
                color = Palette.Accent,
            )
        }
    }
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
    val branchCacheKey = remember(goldenGuest) { BranchListCache.cacheKey(goldenGuest, null) }
    val branchLoadState = rememberBranchListLoad(branchCacheKey, refreshNonce) {
        GitWorkspace.listBranches(context, goldenGuest)
    }

    val recentBranches = remember(goldenGuest) { BranchRecentsStore.load(context, goldenGuest) }
    val branchList = (branchLoadState as? BranchListLoadState.Ready)?.branches ?: emptyList()
    val orderedBranches = remember(branchList, recentBranches, query) {
        orderBranchesForPicker(branchList, recentBranches, query)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.workspace_pick_branch),
                    modifier = Modifier.weight(1f),
                )
                if (branchLoadState is BranchListLoadState.Loading) {
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
                BranchSearchList(
                    loadState = branchLoadState,
                    query = query,
                    onQueryChange = { query = it },
                    orderedBranches = orderedBranches,
                    onPickBranch = { branch ->
                        onPickBranch(branch)
                        onDismiss()
                    },
                    onRetry = { refreshNonce++ },
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


