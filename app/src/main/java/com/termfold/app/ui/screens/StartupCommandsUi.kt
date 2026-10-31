package com.termfold.app.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.termfold.app.R
import com.termfold.app.core.Folder
import com.termfold.app.startup.StartupCommand
import com.termfold.app.startup.StartupCommandGroup
import com.termfold.app.startup.StartupCommandsData
import com.termfold.app.ui.components.BareIconButton
import com.termfold.app.ui.components.BottomNavSpacer
import com.termfold.app.ui.components.PrimaryButton
import com.termfold.app.ui.theme.Mono
import com.termfold.app.ui.theme.Palette
import com.termfold.app.ui.theme.TermFoldIcons

@Composable
fun StartupCommandsDialog(
    folders: List<Folder>,
    data: StartupCommandsData,
    runningIds: Set<String>,
    logs: Map<String, String>,
    onDismiss: () -> Unit,
    onAdd: (name: String, folderId: String, command: String) -> Unit,
    onAddGroup: (name: String, commandIds: List<String>) -> Unit,
    onStart: (String) -> Unit,
    onStop: (String) -> Unit,
    onStartGroup: (String) -> Unit,
    onStopGroup: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDeleteGroup: (String) -> Unit,
    onUpdate: (id: String, name: String, folderId: String, command: String) -> Unit,
    onUpdateGroup: (id: String, name: String, commandIds: List<String>) -> Unit,
) {
    val commands = data.commands
    val groups = data.groups
    val ungrouped = remember(commands, groups) { data.ungroupedCommands() }
    var adding by remember { mutableStateOf(false) }
    var addingGroup by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<StartupCommand?>(null) }
    var editingGroup by remember { mutableStateOf<StartupCommandGroup?>(null) }
    var expandedIds by remember { mutableStateOf(setOf<String>()) }
    var expandedGroupIds by remember { mutableStateOf(setOf<String>()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
            color = Palette.Bg,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BareIconButton(
                        icon = TermFoldIcons.Back,
                        contentDescription = stringResource(R.string.cd_back),
                        onClick = onDismiss,
                    )
                    Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                        Text(
                            text = stringResource(R.string.startup_commands_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = Palette.Text,
                        )
                        Text(
                            text = stringResource(
                                R.string.startup_commands_subtitle,
                                runningIds.size,
                                commands.size,
                                groups.size,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = Palette.TextFaint,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PrimaryButton(
                            icon = TermFoldIcons.ListView,
                            label = stringResource(R.string.startup_command_group_add),
                            compact = true,
                            onClick = {
                                if (commands.isNotEmpty()) addingGroup = true
                            },
                        )
                        PrimaryButton(
                            icon = TermFoldIcons.Plus,
                            label = stringResource(R.string.startup_command_add),
                            compact = true,
                            onClick = { adding = true },
                        )
                    }
                }
                HorizontalDivider(color = Palette.BorderSoft)

                if (commands.isEmpty()) {
                    EmptyState(
                        title = stringResource(R.string.startup_command_empty),
                        hint = stringResource(R.string.startup_command_empty_hint),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(groups, key = { it.id }) { group ->
                            val members = remember(group, commands) { data.commandsInGroup(group.id) }
                            val runningCount = group.commandIds.count { it in runningIds }
                            val anyRunning = runningCount > 0
                            val expanded = group.id in expandedGroupIds
                            LaunchedEffect(anyRunning) {
                                if (anyRunning) expandedGroupIds = expandedGroupIds + group.id
                            }
                            StartupCommandGroupCard(
                                group = group,
                                members = members,
                                folders = folders,
                                runningIds = runningIds,
                                runningCount = runningCount,
                                logs = logs,
                                expanded = expanded,
                                expandedCommandIds = expandedIds,
                                onToggleExpand = {
                                    expandedGroupIds = if (expanded) {
                                        expandedGroupIds - group.id
                                    } else {
                                        expandedGroupIds + group.id
                                    }
                                },
                                onToggleCommandExpand = { cmdId ->
                                    expandedIds = if (cmdId in expandedIds) {
                                        expandedIds - cmdId
                                    } else {
                                        expandedIds + cmdId
                                    }
                                },
                                onStartGroup = { onStartGroup(group.id) },
                                onStopGroup = { onStopGroup(group.id) },
                                onEditGroup = { editingGroup = group },
                                onDeleteGroup = { onDeleteGroup(group.id) },
                                onStartCommand = onStart,
                                onStopCommand = onStop,
                                onEditCommand = { editing = it },
                                onDeleteCommand = onDelete,
                            )
                        }
                        if (ungrouped.isNotEmpty() && groups.isNotEmpty()) {
                            item(key = "ungrouped-label") {
                                Text(
                                    text = stringResource(R.string.startup_command_other).uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Palette.TextFaint,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                                )
                            }
                        }
                        items(ungrouped, key = { it.id }) { entry ->
                            val folder = folders.firstOrNull { it.id == entry.folderId }
                            val running = entry.id in runningIds
                            val log = logs[entry.id].orEmpty()
                            val expanded = entry.id in expandedIds
                            LaunchedEffect(running) {
                                if (running) expandedIds = expandedIds + entry.id
                            }
                            StartupCommandCard(
                                entry = entry,
                                projectName = folder?.name
                                    ?: stringResource(R.string.startup_command_missing_project),
                                running = running,
                                expanded = expanded,
                                logText = log,
                                onToggleExpand = {
                                    expandedIds = if (expanded) expandedIds - entry.id else expandedIds + entry.id
                                },
                                onStart = { onStart(entry.id) },
                                onStop = { onStop(entry.id) },
                                onDelete = { onDelete(entry.id) },
                                onEdit = { editing = entry },
                            )
                        }
                        item { BottomNavSpacer() }
                    }
                }
            }
        }
    }

    if (addingGroup) {
        StartupCommandGroupFormDialog(
            allCommands = commands,
            existing = null,
            onDismiss = { addingGroup = false },
            onConfirm = { name, ids ->
                onAddGroup(name, ids)
                addingGroup = false
            },
        )
    }
    editingGroup?.let { group ->
        StartupCommandGroupFormDialog(
            allCommands = commands,
            existing = group,
            onDismiss = { editingGroup = null },
            onConfirm = { name, ids ->
                onUpdateGroup(group.id, name, ids)
                editingGroup = null
            },
        )
    }
    if (adding) {
        StartupCommandFormDialog(
            folders = folders,
            existing = null,
            onDismiss = { adding = false },
            onConfirm = { name, folderId, command ->
                onAdd(name, folderId, command)
                adding = false
            },
        )
    }
    editing?.let { entry ->
        StartupCommandFormDialog(
            folders = folders,
            existing = entry,
            onDismiss = { editing = null },
            onConfirm = { name, folderId, command ->
                onUpdate(entry.id, name, folderId, command)
                editing = null
            },
        )
    }
}

@Composable
private fun StartupCommandGroupCard(
    group: StartupCommandGroup,
    members: List<StartupCommand>,
    folders: List<Folder>,
    runningIds: Set<String>,
    runningCount: Int,
    logs: Map<String, String>,
    expanded: Boolean,
    expandedCommandIds: Set<String>,
    onToggleExpand: () -> Unit,
    onToggleCommandExpand: (String) -> Unit,
    onStartGroup: () -> Unit,
    onStopGroup: () -> Unit,
    onEditGroup: () -> Unit,
    onDeleteGroup: () -> Unit,
    onStartCommand: (String) -> Unit,
    onStopCommand: (String) -> Unit,
    onEditCommand: (StartupCommand) -> Unit,
    onDeleteCommand: (String) -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val anyRunning = runningCount > 0
    val total = group.commandIds.size
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Palette.Card)
            .border(
                1.dp,
                if (anyRunning) Palette.Accent.copy(alpha = 0.35f) else Palette.BorderSoft,
                shape,
            )
            .animateContentSize(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpand)
                .padding(start = 14.dp, end = 8.dp, top = 14.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (anyRunning) Palette.AccentSoft else Palette.Field),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = TermFoldIcons.ListView,
                    contentDescription = null,
                    tint = if (anyRunning) Palette.Accent else Palette.TextDim,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = Palette.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(8.dp))
                    StatusPill(running = anyRunning)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (anyRunning && total > 0) {
                        stringResource(R.string.startup_command_group_running, runningCount, total)
                    } else {
                        stringResource(R.string.startup_command_group_count, total)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BareIconButton(
                icon = if (expanded) TermFoldIcons.ChevronUp else TermFoldIcons.ChevronDown,
                contentDescription = stringResource(
                    if (expanded) R.string.startup_command_collapse_output
                    else R.string.startup_command_expand_output,
                ),
                onClick = onToggleExpand,
                tint = Palette.TextFaint,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 4.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onEditGroup) {
                Icon(
                    TermFoldIcons.Pencil,
                    contentDescription = stringResource(R.string.startup_command_group_edit_title),
                    tint = Palette.TextDim,
                    modifier = Modifier.size(18.dp),
                )
            }
            TextButton(onClick = onDeleteGroup) {
                Icon(
                    TermFoldIcons.Trash,
                    contentDescription = stringResource(R.string.action_remove),
                    tint = Palette.TextFaint,
                    modifier = Modifier.size(18.dp),
                )
            }
            if (anyRunning) {
                TextButton(onClick = onStopGroup) {
                    Text(
                        text = stringResource(R.string.startup_command_group_stop),
                        color = Palette.Text,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            } else {
                TextButton(
                    onClick = onStartGroup,
                    enabled = total > 0,
                ) {
                    Text(
                        text = stringResource(R.string.startup_command_group_start),
                        color = Palette.Accent,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        if (expanded) {
            HorizontalDivider(color = Palette.BorderSoft)
            Column(
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                members.forEach { entry ->
                    val folder = folders.firstOrNull { it.id == entry.folderId }
                    val running = entry.id in runningIds
                    val log = logs[entry.id].orEmpty()
                    val cmdExpanded = entry.id in expandedCommandIds
                    StartupCommandCard(
                        entry = entry,
                        projectName = folder?.name
                            ?: stringResource(R.string.startup_command_missing_project),
                        running = running,
                        expanded = cmdExpanded,
                        logText = log,
                        onToggleExpand = { onToggleCommandExpand(entry.id) },
                        onStart = { onStartCommand(entry.id) },
                        onStop = { onStopCommand(entry.id) },
                        onDelete = { onDeleteCommand(entry.id) },
                        onEdit = { onEditCommand(entry) },
                        nested = true,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StartupCommandGroupFormDialog(
    allCommands: List<StartupCommand>,
    existing: StartupCommandGroup?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, commandIds: List<String>) -> Unit,
) {
    val isEdit = existing != null
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var selected by remember(existing?.id) {
        mutableStateOf(existing?.commandIds?.toSet() ?: emptySet())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Card,
        titleContentColor = Palette.Text,
        textContentColor = Palette.TextDim,
        title = {
            Text(
                stringResource(
                    if (isEdit) R.string.startup_command_group_edit_title
                    else R.string.startup_command_group_add,
                ),
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StartupTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = stringResource(R.string.startup_command_group_name),
                )
                Text(
                    text = stringResource(R.string.startup_command_group_members),
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.TextDim,
                )
                Text(
                    text = stringResource(R.string.startup_command_group_members_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextFaint,
                )
                if (allCommands.isEmpty()) {
                    Text(
                        text = stringResource(R.string.startup_command_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.TextFaint,
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        allCommands.forEach { cmd ->
                            val picked = cmd.id in selected
                            FilterChip(
                                selected = picked,
                                onClick = {
                                    selected = if (picked) selected - cmd.id else selected + cmd.id
                                },
                                label = {
                                    Text(cmd.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Palette.Accent.copy(alpha = 0.22f),
                                    containerColor = Palette.Field,
                                    selectedLabelColor = Palette.Text,
                                    labelColor = Palette.TextDim,
                                ),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), selected.toList()) },
                enabled = name.isNotBlank() && selected.isNotEmpty(),
            ) {
                Text(
                    stringResource(if (isEdit) R.string.action_save else R.string.action_add),
                    color = Palette.Accent,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), color = Palette.TextDim)
            }
        },
    )
}

@Composable
private fun StartupCommandCard(
    entry: StartupCommand,
    projectName: String,
    running: Boolean,
    expanded: Boolean,
    logText: String,
    onToggleExpand: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    nested: Boolean = false,
) {
    val shape = RoundedCornerShape(if (nested) 12.dp else 16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (nested) Palette.Field else Palette.Card)
            .border(
                1.dp,
                if (running) Palette.Accent.copy(alpha = 0.35f) else Palette.BorderSoft,
                shape,
            )
            .animateContentSize(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpand)
                .padding(start = 14.dp, end = 8.dp, top = 14.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (running) Palette.AccentSoft else Palette.Field),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = TermFoldIcons.Terminal,
                    contentDescription = null,
                    tint = if (running) Palette.Accent else Palette.TextDim,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = Palette.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(8.dp))
                    StatusPill(running = running)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = projectName,
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BareIconButton(
                icon = if (expanded) TermFoldIcons.ChevronUp else TermFoldIcons.ChevronDown,
                contentDescription = stringResource(
                    if (expanded) R.string.startup_command_collapse_output
                    else R.string.startup_command_expand_output,
                ),
                onClick = onToggleExpand,
                tint = Palette.TextFaint,
            )
        }

        Box(
            modifier = Modifier
                .padding(horizontal = 14.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Palette.Field)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = entry.command,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = Mono, lineHeight = 18.sp),
                color = Palette.TextDim,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onEdit) {
                Icon(
                    TermFoldIcons.Pencil,
                    contentDescription = stringResource(R.string.startup_command_edit),
                    tint = Palette.TextDim,
                    modifier = Modifier.size(18.dp),
                )
            }
            TextButton(onClick = onDelete) {
                Icon(
                    TermFoldIcons.Trash,
                    contentDescription = stringResource(R.string.action_remove),
                    tint = Palette.TextFaint,
                    modifier = Modifier.size(18.dp),
                )
            }
            if (running) {
                TextButton(onClick = onStop) {
                    Text(
                        text = stringResource(R.string.acp_stop),
                        color = Palette.Text,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            } else {
                TextButton(onClick = onStart) {
                    Text(
                        text = stringResource(R.string.startup_command_start),
                        color = Palette.Accent,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        if (expanded) {
            HorizontalDivider(color = Palette.BorderSoft)
            StartupTerminalPane(
                logText = logText,
                running = running,
                modifier = Modifier.padding(14.dp),
            )
        }
    }
}

@Composable
private fun StatusPill(running: Boolean) {
    val bg = if (running) Palette.Green.copy(alpha = 0.15f) else Palette.Field
    val fg = if (running) Palette.Green else Palette.TextFaint
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(fg),
        )
        Text(
            text = stringResource(
                if (running) R.string.startup_command_status_running
                else R.string.startup_command_status_stopped,
            ),
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun StartupTerminalPane(
    logText: String,
    running: Boolean,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    LaunchedEffect(logText) {
        scroll.scrollTo(scroll.maxValue)
    }
    Column(modifier) {
        Text(
            text = stringResource(R.string.startup_command_output),
            style = MaterialTheme.typography.labelMedium,
            color = Palette.TextFaint,
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp, max = 280.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Palette.Bg)
                .border(1.dp, Palette.Border, RoundedCornerShape(12.dp))
                .padding(12.dp)
                .verticalScroll(scroll),
        ) {
            val display = logText.ifBlank {
                stringResource(
                    if (running) R.string.startup_command_output_waiting
                    else R.string.startup_command_output_empty,
                )
            }
            Text(
                text = display,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = Mono,
                    lineHeight = 17.sp,
                    fontSize = 12.sp,
                ),
                color = if (logText.isBlank()) Palette.TextFaint else Palette.TermOut,
            )
        }
    }
}

@Composable
private fun StartupCommandFormDialog(
    folders: List<Folder>,
    existing: StartupCommand?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, folderId: String, command: String) -> Unit,
) {
    val isEdit = existing != null
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var command by remember(existing?.id) { mutableStateOf(existing?.command.orEmpty()) }
    var folderId by remember(existing?.id) {
        mutableStateOf(existing?.folderId ?: folders.firstOrNull()?.id.orEmpty())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Card,
        titleContentColor = Palette.Text,
        textContentColor = Palette.TextDim,
        title = {
            Text(
                stringResource(
                    if (isEdit) R.string.startup_command_edit_title
                    else R.string.startup_command_add,
                ),
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StartupTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = stringResource(R.string.startup_command_name),
                )
                Text(
                    text = stringResource(R.string.startup_command_project),
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.TextDim,
                )
                if (folders.isEmpty()) {
                    Text(
                        text = stringResource(R.string.pm_pick_folder_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.TextFaint,
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        folders.forEach { folder ->
                            val selected = folder.id == folderId
                            FilterChip(
                                selected = selected,
                                onClick = { folderId = folder.id },
                                label = {
                                    Text(
                                        text = folder.name,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Palette.Accent.copy(alpha = 0.22f),
                                    containerColor = Palette.Field,
                                    selectedLabelColor = Palette.Text,
                                    labelColor = Palette.TextDim,
                                ),
                            )
                        }
                    }
                }
                StartupTextField(
                    value = command,
                    onValueChange = { command = it },
                    label = stringResource(R.string.startup_command_shell),
                    singleLine = false,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), folderId, command.trim()) },
                enabled = name.isNotBlank() && command.isNotBlank() && folderId.isNotBlank(),
            ) {
                Text(
                    stringResource(if (isEdit) R.string.action_save else R.string.action_add),
                    color = Palette.Accent,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), color = Palette.TextDim)
            }
        },
    )
}

@Composable
private fun StartupTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        maxLines = if (singleLine) 1 else 4,
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Palette.Text,
            unfocusedTextColor = Palette.Text,
            focusedBorderColor = Palette.Accent,
            unfocusedBorderColor = Palette.BorderSoft,
            focusedLabelColor = Palette.TextDim,
            unfocusedLabelColor = Palette.TextFaint,
            cursorColor = Palette.Accent,
        ),
    )
}
