package com.termfold.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.termfold.app.R
import com.termfold.app.automation.memory.AutomationMemoryRepository
import com.termfold.app.pm.memory.PmMemoryRepository
import com.termfold.app.automation.memory.MemoryChunkEntity
import com.termfold.app.automation.memory.SessionEventEntity
import com.termfold.app.ui.components.BareIconButton
import com.termfold.app.ui.components.LeadingTile
import com.termfold.app.ui.components.ListRow
import com.termfold.app.ui.components.PrimaryButton
import com.termfold.app.ui.components.RowSpacer
import com.termfold.app.ui.theme.Palette
import com.termfold.app.ui.theme.TermFoldIcons
import com.termfold.app.ui.theme.folderTint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class AutomationMemoryManagerMode {
    data object GlobalNav : AutomationMemoryManagerMode()
    data class SessionPrivate(val sessionKey: String) : AutomationMemoryManagerMode()
    data class PmSession(val sessionId: String) : AutomationMemoryManagerMode()
}

@Composable
fun AutomationMemoryManagerDialog(
    mode: AutomationMemoryManagerMode,
    onDismiss: () -> Unit,
) {
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
            AutomationMemoryManagerContent(mode = mode, onDismiss = onDismiss)
        }
    }
}

@Composable
private fun AutomationMemoryManagerContent(
    mode: AutomationMemoryManagerMode,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { AutomationMemoryRepository(context) }
    val pmSessionId = (mode as? AutomationMemoryManagerMode.PmSession)?.sessionId
    val pmRepo = remember(pmSessionId) {
        pmSessionId?.let { PmMemoryRepository(context, it) }
    }
    val scope = rememberCoroutineScope()
    val global = mode is AutomationMemoryManagerMode.GlobalNav
    val sessionKey = (mode as? AutomationMemoryManagerMode.SessionPrivate)?.sessionKey
    val isPm = pmRepo != null

    var search by remember { mutableStateOf("") }
    var siteFilter by remember { mutableStateOf("") }
    var kindFilter by remember { mutableStateOf("") }
    var semantic by remember { mutableStateOf(false) }
    var chunks by remember { mutableStateOf<List<MemoryChunkEntity>>(emptyList()) }
    var events by remember { mutableStateOf<List<SessionEventEntity>>(emptyList()) }
    var tab by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<MemoryChunkEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loadingChunks by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var eventToDelete by remember { mutableStateOf<SessionEventEntity?>(null) }

    val title = when (mode) {
        AutomationMemoryManagerMode.GlobalNav ->
            stringResource(R.string.automation_memory_global_title)
        is AutomationMemoryManagerMode.SessionPrivate ->
            stringResource(R.string.automation_memory_session_title)
        is AutomationMemoryManagerMode.PmSession ->
            stringResource(R.string.pm_session_memory_title)
    }

    suspend fun queryChunks(): List<MemoryChunkEntity> {
        if (pmRepo != null) {
            return if (semantic && search.isNotBlank()) {
                pmRepo.search(search, limit = 50).let { rows ->
                    if (kindFilter.isBlank()) rows
                    else rows.filter { it.kind == kindFilter }
                }
            } else {
                pmRepo.listMemoryChunks(kindFilter = kindFilter, textFilter = search)
            }
        }
        val loaded = if (semantic && search.isNotBlank()) {
            val raw = if (global) {
                repo.searchGlobalNav(
                    siteKey = siteFilter.ifBlank { "web" },
                    query = search,
                    limit = 50,
                )
            } else if (sessionKey != null) {
                repo.searchSessionMemory(sessionKey, search, limit = 50)
            } else {
                emptyList()
            }
            repo.applySiteKindFilters(raw, siteFilter, kindFilter)
        } else {
            repo.listMemoryChunks(
                global = global,
                sessionKey = sessionKey,
                siteKeyFilter = siteFilter,
                kindFilter = kindFilter,
                textFilter = search,
            )
        }
        return loaded
    }

    LaunchedEffect(mode, search, siteFilter, kindFilter, semantic) {
        loadingChunks = true
        val loaded = withContext(Dispatchers.IO) { queryChunks() }
        chunks = loaded
        loadingChunks = false
    }

    LaunchedEffect(sessionKey, tab, search) {
        if (isPm || global || tab != 1 || sessionKey == null) return@LaunchedEffect
        while (isActive) {
            val loaded = withContext(Dispatchers.IO) {
                repo.listSessionEvents(sessionKey, textFilter = search)
            }
            events = loaded
            delay(2_000)
        }
    }

    fun reloadChunks() {
        scope.launch {
            loadingChunks = true
            val loaded = withContext(Dispatchers.IO) { queryChunks() }
            chunks = loaded
            loadingChunks = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BareIconButton(
                icon = TermFoldIcons.Close,
                contentDescription = stringResource(R.string.action_close),
                onClick = onDismiss,
            )
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                Text(
                    stringResource(
                        if (global) R.string.automation_memory_global_subtitle
                        else R.string.automation_memory_session_subtitle,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextFaint,
                )
            }
            if (global || tab == 0) {
                PrimaryButton(
                    icon = TermFoldIcons.Plus,
                    label = stringResource(R.string.automation_memory_add),
                    onClick = {
                        creating = true
                        editing = MemoryChunkEntity(
                            automationSessionId = when {
                                isPm -> pmSessionId.orEmpty()
                                global -> ""
                                else -> sessionKey.orEmpty()
                            },
                            siteKey = if (isPm) "pm" else siteFilter.ifBlank { "example.com" },
                            kind = "note",
                        )
                        error = null
                    },
                    compact = true,
                )
            }
        }

        if (!global && !isPm) {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = { Text(stringResource(R.string.automation_memory_tab_chunks)) },
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text(stringResource(R.string.automation_run_log)) },
                )
            }
        }

        if (tab == 0 || global) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.automation_memory_search)) },
                    singleLine = true,
                    colors = fieldColors(),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = siteFilter,
                        onValueChange = { siteFilter = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(stringResource(R.string.automation_memory_filter_site)) },
                        singleLine = true,
                        colors = fieldColors(),
                    )
                    OutlinedTextField(
                        value = kindFilter,
                        onValueChange = { kindFilter = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(stringResource(R.string.automation_memory_filter_kind)) },
                        singleLine = true,
                        colors = fieldColors(),
                    )
                }
                Spacer(Modifier.height(8.dp))
                FilterChip(
                    selected = semantic,
                    onClick = { semantic = !semantic },
                    label = { Text(stringResource(R.string.automation_memory_semantic)) },
                )
            }
        } else {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.automation_run_log_filter)) },
                singleLine = true,
                colors = fieldColors(),
            )
        }

        error?.let { msg ->
            Text(
                text = msg,
                color = Palette.Accent,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        if (saving) {
            Text(
                text = stringResource(R.string.automation_memory_saving),
                color = Palette.TextDim,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
        }

        when {
            !global && tab == 1 -> {
                if (events.isEmpty()) {
                    MemoryEmptyHint(stringResource(R.string.automation_memory_empty_events))
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                    ) {
                        items(events, key = { it.id }) { event ->
                            ListRow(
                                title = "${event.type} · ${event.outcome}",
                                subtitle = {
                                    Column {
                                        if (event.url.isNotBlank()) {
                                            Text(
                                                event.url,
                                                color = Palette.TextDim,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        Text(
                                            event.summary,
                                            color = if (event.outcome == "err") Palette.Accent else Palette.TextFaint,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                },
                                leading = {
                                    LeadingTile(tint = folderTint(2), icon = TermFoldIcons.ListView)
                                },
                                trailing = {
                                    BareIconButton(
                                        icon = TermFoldIcons.Trash,
                                        contentDescription = stringResource(R.string.action_remove),
                                        onClick = { eventToDelete = event },
                                        tint = Palette.TextFaint,
                                    )
                                },
                            )
                            RowSpacer()
                        }
                    }
                }
            }
            loadingChunks -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Palette.Accent, strokeWidth = 2.dp)
                }
            }
            chunks.isEmpty() -> {
                MemoryEmptyHint(stringResource(R.string.automation_memory_empty_chunks))
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    items(chunks, key = { it.id }) { chunk ->
                        ListRow(
                            title = chunk.title.ifBlank { chunk.kind },
                            subtitle = {
                                Column {
                                    Text(
                                        "${chunk.siteKey} · ${chunk.kind}",
                                        color = Palette.TextDim,
                                        maxLines = 1,
                                    )
                                    Text(
                                        chunk.body,
                                        color = Palette.TextFaint,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            },
                            leading = {
                                LeadingTile(tint = folderTint(4), icon = TermFoldIcons.Sparkle)
                            },
                            onClick = {
                                editing = chunk
                                creating = false
                                error = null
                            },
                        )
                        RowSpacer()
                    }
                }
            }
        }
    }

    eventToDelete?.let { event ->
        AlertDialog(
            onDismissRequest = { eventToDelete = null },
            title = { Text(stringResource(R.string.automation_run_log_delete_title)) },
            text = { Text(stringResource(R.string.automation_run_log_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = event.id
                        eventToDelete = null
                        scope.launch {
                            withContext(Dispatchers.IO) { repo.deleteSessionEvent(id) }
                            if (sessionKey != null) {
                                events = withContext(Dispatchers.IO) {
                                    repo.listSessionEvents(sessionKey, textFilter = search)
                                }
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.action_remove), color = Palette.Accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { eventToDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    editing?.let { formChunk ->
        ChunkEditorDialog(
            chunk = formChunk,
            isNew = creating,
            global = global,
            saving = saving,
            onDismiss = { if (!saving) { editing = null; creating = false } },
            onSave = { updated ->
                scope.launch {
                    saving = true
                    val result = withContext(Dispatchers.IO) {
                        pmRepo?.saveMemoryChunk(updated) ?: repo.saveMemoryChunk(updated)
                    }
                    saving = false
                    result.fold(
                        onSuccess = {
                            editing = null
                            creating = false
                            error = null
                            reloadChunks()
                        },
                        onFailure = { error = it.message },
                    )
                }
            },
            onDelete = if (!creating && formChunk.id != 0L) {
                {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            if (pmRepo != null) pmRepo.deleteMemoryChunk(formChunk.id)
                            else repo.deleteMemoryChunk(formChunk.id)
                        }
                        editing = null
                        creating = false
                        reloadChunks()
                    }
                }
            } else {
                null
            },
        )
    }
}

@Composable
private fun MemoryEmptyHint(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.TextDim,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ChunkEditorDialog(
    chunk: MemoryChunkEntity,
    isNew: Boolean,
    global: Boolean,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (MemoryChunkEntity) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var siteKey by remember(chunk.id) { mutableStateOf(chunk.siteKey) }
    var kind by remember(chunk.id) { mutableStateOf(chunk.kind) }
    var title by remember(chunk.id) { mutableStateOf(chunk.title) }
    var searchText by remember(chunk.id) { mutableStateOf(chunk.searchText) }
    var body by remember(chunk.id) { mutableStateOf(chunk.body) }
    val scroll = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (isNew) stringResource(R.string.automation_memory_add)
                else stringResource(R.string.automation_memory_edit),
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(scroll)
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = siteKey,
                    onValueChange = { siteKey = it },
                    label = { Text(stringResource(R.string.automation_memory_field_site)) },
                    singleLine = true,
                    enabled = !saving,
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = kind,
                    onValueChange = { kind = it },
                    label = { Text(stringResource(R.string.automation_memory_field_kind)) },
                    singleLine = true,
                    enabled = !saving,
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.automation_memory_field_title)) },
                    singleLine = true,
                    enabled = !saving,
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    label = { Text(stringResource(R.string.automation_memory_field_search)) },
                    singleLine = true,
                    enabled = !saving,
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text(stringResource(R.string.automation_memory_field_body)) },
                    minLines = 4,
                    enabled = !saving,
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (global) {
                    Text(
                        stringResource(R.string.automation_memory_global_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.TextFaint,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving && siteKey.isNotBlank() && kind.isNotBlank(),
                onClick = {
                    onSave(
                        chunk.copy(
                            siteKey = siteKey.trim(),
                            kind = kind.trim(),
                            title = title.trim(),
                            searchText = searchText.trim(),
                            body = body.trim(),
                        ),
                    )
                },
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(enabled = !saving, onClick = onDelete) {
                        Text(stringResource(R.string.action_remove), color = Palette.Accent)
                    }
                }
                TextButton(enabled = !saving, onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        },
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Palette.Text,
    unfocusedTextColor = Palette.Text,
    focusedBorderColor = Palette.Accent,
    unfocusedBorderColor = Palette.BorderSoft,
)
