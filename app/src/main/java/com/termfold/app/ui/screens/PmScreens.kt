package com.termfold.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.termfold.app.R
import com.termfold.app.core.Folder
import com.termfold.app.pm.PmMode
import com.termfold.app.pm.PmSession
import com.termfold.app.ui.components.BottomNavSpacer
import com.termfold.app.ui.components.LeadingTile
import com.termfold.app.ui.components.ListRow
import com.termfold.app.ui.components.PrimaryButton
import com.termfold.app.ui.components.RowSpacer
import com.termfold.app.ui.theme.Palette
import com.termfold.app.ui.theme.TermFoldIcons
import com.termfold.app.ui.theme.folderTint

@Composable
fun PmSessionListScreen(
    sessions: List<PmSession>,
    codingFolders: List<Folder>,
    pmError: String?,
    onBack: () -> Unit,
    onOpenSession: (String) -> Unit,
    onStartDiscovery: (folderId: String, name: String) -> Unit,
    onStartRelease: (folderId: String, branch: String, name: String, releaseLabel: String) -> Unit,
    useGrid: Boolean,
    modifier: Modifier = Modifier,
) {
    var showNew by remember { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxSize()) {
        WorkflowsSubscreenHeader(
            onBack = onBack,
            title = stringResource(R.string.workflows_tab_product_manager),
            onAdd = { showNew = true },
        )
        if (!pmError.isNullOrBlank()) {
            Text(
                pmError,
                color = Palette.Accent,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        when {
            sessions.isEmpty() -> EmptyState(
                title = stringResource(R.string.pm_empty_hint),
                hint = stringResource(R.string.pm_empty_subtitle),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                items(sessions, key = { it.id }) { session ->
                    val folderName = codingFolders.firstOrNull { it.id == session.folderId }?.name ?: session.folderId
                    ListRow(
                        title = session.name,
                        subtitle = {
                            Text(
                                "${session.mode.name} · $folderName",
                                color = Palette.TextDim,
                            )
                        },
                        leading = {
                            LeadingTile(tint = folderTint(session.tint), icon = TermFoldIcons.Sparkle)
                        },
                        trailing = {
                            Icon(
                                TermFoldIcons.ChevronRight,
                                contentDescription = null,
                                tint = Palette.TextFaint,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        onClick = { onOpenSession(session.id) },
                    )
                    RowSpacer()
                }
                item { BottomNavSpacer() }
            }
        }
    }
    if (showNew) {
        PmNewSessionDialog(
            codingFolders = codingFolders,
            onDismiss = { showNew = false },
            onDiscovery = { folderId, name ->
                showNew = false
                onStartDiscovery(folderId, name)
            },
            onRelease = { folderId, branch, name, label ->
                showNew = false
                onStartRelease(folderId, branch, name, label)
            },
        )
    }
}

@Composable
private fun PmNewSessionDialog(
    codingFolders: List<Folder>,
    onDismiss: () -> Unit,
    onDiscovery: (folderId: String, name: String) -> Unit,
    onRelease: (folderId: String, branch: String, name: String, releaseLabel: String) -> Unit,
) {
    var step by remember { mutableStateOf(0) }
    var mode by remember { mutableStateOf(PmMode.DISCOVERY) }
    var selectedFolder by remember { mutableStateOf<Folder?>(null) }
    var name by remember { mutableStateOf("") }
    var branch by remember { mutableStateOf("main") }
    var releaseLabel by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (step) {
                    0 -> stringResource(R.string.pm_new_session)
                    1 -> stringResource(R.string.pm_pick_folder)
                    else -> stringResource(R.string.pm_session_details)
                },
            )
        },
        text = {
            when (step) {
                0 -> Column {
                    TextButton(onClick = { mode = PmMode.DISCOVERY; step = 1 }) {
                        Text(stringResource(R.string.pm_mode_discovery))
                    }
                    TextButton(onClick = { mode = PmMode.RELEASE; step = 1 }) {
                        Text(stringResource(R.string.pm_mode_release))
                    }
                }
                1 -> LazyColumn {
                    items(codingFolders) { folder ->
                        TextButton(onClick = { selectedFolder = folder; step = 2 }) {
                            Text(folder.name)
                        }
                    }
                }
                else -> Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.pm_session_name)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Palette.Text,
                            unfocusedTextColor = Palette.Text,
                        ),
                    )
                    if (mode == PmMode.RELEASE) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = branch,
                            onValueChange = { branch = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.pm_release_branch)) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Palette.Text,
                                unfocusedTextColor = Palette.Text,
                            ),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = releaseLabel,
                            onValueChange = { releaseLabel = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.pm_release_label)) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Palette.Text,
                                unfocusedTextColor = Palette.Text,
                            ),
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (step == 2 && selectedFolder != null) {
                TextButton(
                    onClick = {
                        val folderId = selectedFolder!!.id
                        if (mode == PmMode.DISCOVERY) {
                            onDiscovery(folderId, name)
                        } else if (branch.trim().isBlank()) {
                            return@TextButton
                        } else {
                            onRelease(folderId, branch.trim(), name, releaseLabel)
                        }
                    },
                ) {
                    Text(stringResource(R.string.workflows_new))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
