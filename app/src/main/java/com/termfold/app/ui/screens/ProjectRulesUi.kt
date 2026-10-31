package com.termfold.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.termfold.app.R
import com.termfold.app.core.AppViewModel
import com.termfold.app.core.Folder
import com.termfold.app.projectrules.ProjectRule
import com.termfold.app.projectrules.ProjectRuleProfile
import com.termfold.app.projectrules.ProjectRuleSheetRow
import com.termfold.app.projectrules.ProjectRulesContextResolver
import com.termfold.app.projectrules.ProjectRuleProfileKind
import com.termfold.app.projectrules.ProjectRulesStore
import com.termfold.app.projectrules.RemoteOriginKey
import com.termfold.app.ui.components.BareIconButton
import com.termfold.app.ui.components.LeadingTile
import com.termfold.app.ui.components.ListRow
import com.termfold.app.ui.components.PrimaryButton
import com.termfold.app.ui.theme.Palette
import com.termfold.app.ui.theme.TermFoldIcons
import com.termfold.app.ui.theme.folderTint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@Composable
private fun ProjectRulesFullScreenDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(Palette.Bg)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
            color = Palette.Bg,
        ) {
            content()
        }
    }
}

@Composable
fun ProjectRulesSheet(
    sessionKey: String,
    folder: Folder,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { ProjectRulesStore.get(context) }
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf(store.read()) }
    var originKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(folder.id) {
        originKey = ProjectRulesContextResolver.folderNetworkOrigin(context, folder)
        data = store.read()
    }
    val rows = remember(data, originKey) {
        ProjectRulesContextResolver.sheetRows(data, sessionKey, folder, originKey)
    }
    val listLines = remember(rows) {
        buildList {
            var last: String? = null
            rows.forEach { row ->
                if (row.profile.displayName != last) {
                    last = row.profile.displayName
                    add(SessionListLine.Header(last!!, row.profile.isGlobal))
                }
                add(SessionListLine.Rule(row))
            }
        }
    }

    ProjectRulesFullScreenDialog(onDismiss) {
        Column(Modifier.fillMaxSize()) {
            ProjectRulesTopBar(
                title = stringResource(R.string.project_rules_sheet_title),
                subtitle = folder.name,
                onClose = onDismiss,
            )
            if (rows.isEmpty()) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        stringResource(R.string.project_rules_sheet_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Palette.TextDim,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(
                        listLines.size,
                        key = { i ->
                            when (val line = listLines[i]) {
                                is SessionListLine.Header -> "h-${line.title}-${line.isGlobal}"
                                is SessionListLine.Rule -> line.row.rule.id
                            }
                        },
                    ) { i ->
                        when (val line = listLines[i]) {
                            is SessionListLine.Header -> {
                                Text(
                                    if (line.isGlobal) {
                                        stringResource(R.string.project_rules_global_profile_header, line.title)
                                    } else {
                                        line.title
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Palette.TextFaint,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp, start = 4.dp),
                                )
                            }
                            is SessionListLine.Rule -> {
                                val row = line.row
                                ListRow(
                                    title = row.rule.title.ifBlank {
                                        stringResource(R.string.project_rules_untitled_rule)
                                    },
                                    leading = {
                                        LeadingTile(
                                            tint = folderTint(folder.tint),
                                            icon = TermFoldIcons.FileText,
                                        )
                                    },
                                    subtitle = when {
                                        !row.eligible -> {
                                            {
                                                Text(
                                                    stringResource(R.string.project_rules_not_eligible_hint),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Palette.TextFaint,
                                                )
                                            }
                                        }
                                        row.profile.isGlobal && !row.enabled -> {
                                            {
                                                Text(
                                                    stringResource(R.string.project_rules_global_off_hint),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Palette.TextFaint,
                                                )
                                            }
                                        }
                                        else -> null
                                    },
                                    trailing = {
                                        Switch(
                                            checked = row.enabled,
                                            onCheckedChange = { enabled ->
                                                scope.launch {
                                                    withContext(Dispatchers.IO) {
                                                        store.setRuleEnabled(
                                                            sessionKey,
                                                            row.rule.id,
                                                            enabled,
                                                            folder,
                                                            originKey,
                                                        )
                                                        data = store.read()
                                                    }
                                                }
                                            },
                                        )
                                    },
                                    onClick = {
                                        scope.launch {
                                            withContext(Dispatchers.IO) {
                                                store.setRuleEnabled(
                                                    sessionKey,
                                                    row.rule.id,
                                                    !row.enabled,
                                                    folder,
                                                    originKey,
                                                )
                                                data = store.read()
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProjectRulesSettingsEntry(viewModel: AppViewModel) {
    var open by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Palette.Card)
            .clickable { open = true }
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.project_rules_settings_title),
                style = MaterialTheme.typography.bodyLarge,
                color = Palette.Text,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(R.string.project_rules_settings_desc),
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextDim,
            )
        }
        Icon(TermFoldIcons.ChevronRight, contentDescription = null, tint = Palette.TextFaint)
    }
    if (open) {
        ProjectRulesManagerDialog(viewModel = viewModel, onDismiss = { open = false })
    }
}

@Composable
private fun ProjectRulesManagerDialog(viewModel: AppViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val store = remember { ProjectRulesStore.get(context) }
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf(store.read()) }
    var editingId by remember { mutableStateOf<String?>(null) }
    val folders by viewModel.folders.collectAsStateWithLifecycle(initialValue = com.termfold.app.core.AppData())
    val editing = editingId?.let { id -> data.profiles.find { it.id == id } }

    ProjectRulesFullScreenDialog(onDismiss) {
        Column(Modifier.fillMaxSize()) {
            if (editing == null) {
                ProjectRulesTopBar(
                    title = stringResource(R.string.project_rules_settings_title),
                    subtitle = stringResource(R.string.project_rules_manager_subtitle),
                    onClose = onDismiss,
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PrimaryButton(
                        icon = TermFoldIcons.Globe,
                        label = stringResource(R.string.project_rules_add_global_profile),
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                val p = store.createGlobalProfile(
                                    context.getString(R.string.project_rules_new_global_profile_default_name),
                                )
                                data = store.read()
                                editingId = p.id
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PrimaryButton(
                        icon = TermFoldIcons.Plus,
                        label = stringResource(R.string.project_rules_add_profile),
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                val p = store.createProfile(
                                    context.getString(R.string.project_rules_new_profile_default_name),
                                )
                                data = store.read()
                                editingId = p.id
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (data.profiles.isEmpty()) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            stringResource(R.string.project_rules_list_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = Palette.TextDim,
                        )
                    }
                } else {
                    val globalProfiles = data.profiles.filter { it.isGlobal }
                    val projectProfiles = data.profiles.filter { !it.isGlobal }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (globalProfiles.isNotEmpty()) {
                            item {
                                Text(
                                    stringResource(R.string.project_rules_section_global),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Palette.TextFaint,
                                    modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 2.dp),
                                )
                            }
                            items(globalProfiles, key = { it.id }) { profile ->
                                ProfileListRow(context, profile, folders.folders, onClick = { editingId = profile.id })
                            }
                        }
                        if (projectProfiles.isNotEmpty()) {
                            item {
                                Text(
                                    stringResource(R.string.project_rules_section_project),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Palette.TextFaint,
                                    modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 2.dp),
                                )
                            }
                            items(projectProfiles, key = { it.id }) { profile ->
                                ProfileListRow(context, profile, folders.folders, onClick = { editingId = profile.id })
                            }
                        }
                    }
                }
            } else {
                ProfileEditorScreen(
                    profile = editing,
                    folders = folders.folders,
                    onBack = { editingId = null },
                    onSave = { updated ->
                        scope.launch(Dispatchers.IO) {
                            store.saveProfile(updated)
                            data = store.read()
                            editingId = updated.id
                        }
                    },
                    onDelete = {
                        scope.launch(Dispatchers.IO) {
                            store.deleteProfile(editing.id)
                            data = store.read()
                            editingId = null
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ProjectRulesTopBar(
    title: String,
    subtitle: String,
    onClose: () -> Unit,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BareIconButton(
            icon = TermFoldIcons.Close,
            contentDescription = stringResource(R.string.action_close),
            onClick = onClose,
        )
        Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Palette.Text)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint)
        }
        action?.invoke()
    }
    HorizontalDivider(color = Palette.BorderSoft)
}

@Composable
private fun ProfileEditorScreen(
    profile: ProjectRuleProfile,
    folders: List<Folder>,
    onBack: () -> Unit,
    onSave: (ProjectRuleProfile) -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember(profile.id) { mutableStateOf(profile.displayName) }
    var origin by remember(profile.id) { mutableStateOf(profile.originKey ?: "") }
    var rules by remember(profile.id) { mutableStateOf(profile.rules) }
    var selectedFolderIds by remember(profile.id) { mutableStateOf(profile.folderIds.toSet()) }
    LaunchedEffect(profile) {
        name = profile.displayName
        origin = profile.originKey ?: ""
        rules = profile.rules
        selectedFolderIds = profile.folderIds.toSet()
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BareIconButton(
                icon = TermFoldIcons.Back,
                contentDescription = stringResource(R.string.cd_back),
                onClick = onBack,
            )
            Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                Text(
                    stringResource(R.string.project_rules_edit_profile),
                    style = MaterialTheme.typography.titleLarge,
                    color = Palette.Text,
                )
                Text(
                    if (profile.isGlobal) {
                        stringResource(R.string.project_rules_edit_global_subtitle)
                    } else {
                        stringResource(R.string.project_rules_edit_subtitle)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextFaint,
                )
            }
            PrimaryButton(
                icon = TermFoldIcons.Save,
                label = stringResource(R.string.action_save),
                compact = true,
                onClick = {
                    val normalized = origin.trim().let { RemoteOriginKey.normalizeOriginKeyForStorage(it) }
                    val cleanedRules = rules
                        .filter { it.title.isNotBlank() || it.body.isNotBlank() }
                        .mapIndexed { i, r -> r.copy(sortOrder = i) }
                    onSave(
                        profile.copy(
                            displayName = name.trim().ifBlank { profile.displayName },
                            originKey = if (profile.isGlobal) null else normalized,
                            folderIds = if (profile.isGlobal) emptyList() else selectedFolderIds.toList(),
                            rules = cleanedRules,
                        ),
                    )
                },
            )
        }
        HorizontalDivider(color = Palette.BorderSoft)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            EditorSection(title = stringResource(R.string.project_rules_section_profile)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.project_rules_profile_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = outlinedFieldColors(),
                )
                if (profile.isGlobal) {
                    Text(
                        stringResource(R.string.project_rules_global_editor_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.TextDim,
                    )
                } else {
                    OutlinedTextField(
                        value = origin,
                        onValueChange = { origin = it },
                        label = { Text(stringResource(R.string.project_rules_origin_hint)) },
                        placeholder = { Text(stringResource(R.string.project_rules_origin_placeholder)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = outlinedFieldColors(),
                    )
                }
            }

            if (!profile.isGlobal && folders.isNotEmpty()) {
                EditorSection(title = stringResource(R.string.project_rules_copy_origin)) {
                    Text(
                        stringResource(R.string.project_rules_copy_origin_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.TextDim,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        folders.forEach { folder ->
                            FilterChip(
                                selected = folder.id in selectedFolderIds,
                                onClick = {
                                    val next = if (folder.id in selectedFolderIds) {
                                        selectedFolderIds - folder.id
                                    } else {
                                        selectedFolderIds + folder.id
                                    }
                                    selectedFolderIds = next
                                    if (next.size == 1) {
                                        val only = folders.first { it.id == next.single() }
                                        scope.launch {
                                            val key = ProjectRulesContextResolver.folderNetworkOrigin(context, only)
                                            if (key != null) origin = key
                                        }
                                    }
                                },
                                label = { Text(folder.name) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Palette.Accent.copy(alpha = 0.25f),
                                    labelColor = Palette.Text,
                                ),
                            )
                        }
                    }
                }
            }

            EditorSection(
                title = stringResource(R.string.project_rules_section_rules),
                action = {
                    TextButton(onClick = {
                        rules = rules + ProjectRule(UUID.randomUUID().toString(), "", "", rules.size)
                    }) {
                        Text(stringResource(R.string.project_rules_add_rule), color = Palette.Accent)
                    }
                },
            ) {
                if (rules.isEmpty()) {
                    Text(
                        stringResource(R.string.project_rules_no_rules_yet),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.TextDim,
                    )
                } else {
                    rules.forEachIndexed { index, rule ->
                        RuleCard(
                            rule = rule,
                            index = index,
                            onChange = { updated ->
                                rules = rules.toMutableList().also { it[index] = updated }
                            },
                            onDelete = {
                                rules = rules.filterIndexed { i, _ -> i != index }
                            },
                        )
                        if (index < rules.lastIndex) Spacer(Modifier.height(12.dp))
                    }
                }
            }

            TextButton(onClick = onDelete, modifier = Modifier.padding(top = 8.dp)) {
                Icon(TermFoldIcons.Trash, contentDescription = null, tint = Palette.TextFaint)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.project_rules_delete_profile), color = Palette.TextFaint)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun EditorSection(
    title: String,
    action: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Palette.Card)
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Palette.Text)
            action?.invoke()
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun RuleCard(
    rule: ProjectRule,
    index: Int,
    onChange: (ProjectRule) -> Unit,
    onDelete: () -> Unit,
) {
    var title by remember(rule.id) { mutableStateOf(rule.title) }
    var body by remember(rule.id) { mutableStateOf(rule.body) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Palette.Bg)
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.project_rules_rule_number, index + 1),
                style = MaterialTheme.typography.labelMedium,
                color = Palette.TextFaint,
            )
            BareIconButton(
                icon = TermFoldIcons.Trash,
                contentDescription = stringResource(R.string.files_delete),
                onClick = onDelete,
                tint = Palette.TextFaint,
                size = 36,
            )
        }
        OutlinedTextField(
            value = title,
            onValueChange = { title = it; onChange(rule.copy(title = it, body = body)) },
            label = { Text(stringResource(R.string.project_rules_rule_title)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = outlinedFieldColors(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = body,
            onValueChange = { body = it; onChange(rule.copy(title = title, body = it)) },
            label = { Text(stringResource(R.string.project_rules_rule_body)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            colors = outlinedFieldColors(),
        )
    }
}

@Composable
private fun outlinedFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Palette.Text,
    unfocusedTextColor = Palette.Text,
    focusedBorderColor = Palette.BorderSoft,
    unfocusedBorderColor = Palette.BorderSoft,
    focusedContainerColor = Palette.Bg,
    unfocusedContainerColor = Palette.Bg,
)

private sealed interface SessionListLine {
    data class Header(val title: String, val isGlobal: Boolean = false) : SessionListLine
    data class Rule(val row: ProjectRuleSheetRow) : SessionListLine
}

@Composable
private fun ProfileListRow(
    context: android.content.Context,
    profile: ProjectRuleProfile,
    folders: List<Folder>,
    onClick: () -> Unit,
) {
    ListRow(
        title = profile.displayName,
        leading = {
            LeadingTile(
                tint = if (profile.isGlobal) Palette.Blue.copy(alpha = 0.35f) else Palette.Accent.copy(alpha = 0.35f),
                icon = if (profile.isGlobal) TermFoldIcons.Globe else TermFoldIcons.Sliders,
            )
        },
        subtitle = {
            val sub = buildString {
                append(
                    context.resources.getQuantityString(
                        R.plurals.project_rules_rule_count,
                        profile.rules.size,
                        profile.rules.size,
                    ),
                )
                if (profile.isGlobal) {
                    append(" · ")
                    append(context.getString(R.string.project_rules_global_list_hint))
                } else if (profile.folderIds.isNotEmpty()) {
                    val names = profile.folderIds.mapNotNull { id ->
                        folders.find { it.id == id }?.name
                    }
                    if (names.isNotEmpty()) {
                        append(" · ")
                        append(names.joinToString(", "))
                    }
                } else {
                    profile.originKey?.let { key ->
                        append(" · ")
                        append(key)
                    }
                }
            }
            Text(sub, style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
        },
        trailing = {
            Icon(TermFoldIcons.ChevronRight, contentDescription = null, tint = Palette.TextFaint)
        },
        onClick = onClick,
    )
}
