package com.termfold.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termfold.app.R
import com.termfold.app.core.Folder
import com.termfold.app.tasks.TaskDay
import com.termfold.app.tasks.TaskItem
import com.termfold.app.tasks.TaskStatus
import com.termfold.app.ui.components.BareIconButton
import com.termfold.app.ui.components.BottomNavSpacer
import com.termfold.app.ui.components.GroupLabel
import com.termfold.app.ui.components.PrimaryButton
import com.termfold.app.ui.components.SectionTitle
import com.termfold.app.ui.theme.Mono
import com.termfold.app.ui.theme.Palette
import com.termfold.app.ui.theme.TermFoldIcons
import com.termfold.app.ui.theme.folderTint
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun TasksScreen(
    folders: List<Folder>,
    tasks: List<TaskItem>,
    wide: Boolean,
    onCreateTask: (
        dayKey: String,
        folderId: String,
        title: String,
        description: String,
        status: TaskStatus,
        attachmentUris: List<Uri>,
    ) -> Unit,
    onUpdateTask: (
        id: String,
        dayKey: String,
        folderId: String,
        title: String,
        description: String,
        status: TaskStatus,
        attachmentUris: List<Uri>,
    ) -> Unit,
    onDeleteTask: (String) -> Unit,
    onSetStatus: (String, TaskStatus) -> Unit,
    modifier: Modifier = Modifier,
) {
    val edge = if (wide) 30.dp else 22.dp
    var dayKey by rememberSaveable { mutableStateOf(TaskDay.todayKey()) }
    val selectedDay = remember(dayKey) { TaskDay.parse(dayKey) ?: LocalDate.now() }
    val todayKey = TaskDay.todayKey()
    val dayTasks = remember(tasks, dayKey) { tasks.filter { it.dayKey == dayKey } }
    val collapsed = remember { mutableStateMapOf<TaskStatus, Boolean>() }
    var showForm by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<TaskItem?>(null) }
    var pendingDelete by remember { mutableStateOf<TaskItem?>(null) }
    var showCalendar by remember { mutableStateOf(false) }
    val canAdd = folders.isNotEmpty()

    if (showForm) {
        TaskFormScreen(
            folders = folders,
            dayKey = dayKey,
            existing = editing,
            onBack = {
                showForm = false
                editing = null
            },
            onConfirm = { folderId, title, description, status, uris ->
                if (editing == null) {
                    onCreateTask(dayKey, folderId, title, description, status, uris)
                } else {
                    onUpdateTask(editing!!.id, dayKey, folderId, title, description, status, uris)
                }
                showForm = false
                editing = null
            },
            modifier = modifier,
        )
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = edge, end = 16.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionTitle(text = stringResource(R.string.tasks_title), modifier = Modifier.weight(1f))
            BareIconButton(
                icon = TermFoldIcons.Plus,
                contentDescription = stringResource(R.string.task_add),
                onClick = {
                    if (!canAdd) return@BareIconButton
                    editing = null
                    showForm = true
                },
                tint = if (canAdd) Palette.Accent else Palette.TextFaint,
            )
        }
        TaskDaySelector(
            day = selectedDay,
            isToday = dayKey == todayKey,
            onPrevious = { dayKey = TaskDay.key(selectedDay.minusDays(1)) },
            onNext = { dayKey = TaskDay.key(selectedDay.plusDays(1)) },
            onJumpToday = { dayKey = todayKey },
            onOpenCalendar = { showCalendar = true },
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(12.dp))
        if (dayTasks.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.tasks_day_empty_title),
                hint = if (canAdd) {
                    stringResource(R.string.tasks_day_empty_hint)
                } else {
                    stringResource(R.string.tasks_no_projects_hint)
                },
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 8.dp),
            ) {
                TaskStatus.displayOrder.forEach { status ->
                    val sectionTasks = dayTasks.filter { it.status == status }
                    if (sectionTasks.isEmpty()) return@forEach
                    val expanded = collapsed[status] != false
                    item(key = "header-${status.name}") {
                        TaskStatusSectionHeader(
                            status = status,
                            count = sectionTasks.size,
                            expanded = expanded,
                            onToggle = { collapsed[status] = !expanded },
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    if (expanded) {
                        items(sectionTasks, key = { it.id }) { task ->
                            val tintIndex = folders.indexOfFirst { it.id == task.folderId }.takeIf { it >= 0 }
                                ?: (task.folderId.hashCode().and(0x7FFFFFFF) % 6)
                            TaskCard(
                                task = task,
                                projectName = folders.firstOrNull { it.id == task.folderId }?.name
                                    ?: stringResource(R.string.task_project_unknown),
                                projectTintIndex = tintIndex,
                                onClick = {
                                    editing = task
                                    showForm = true
                                },
                                onDelete = { pendingDelete = task },
                                onQuickStatus = { onSetStatus(task.id, it) },
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    item(key = "gap-${status.name}") { Spacer(Modifier.height(4.dp)) }
                }
                item { BottomNavSpacer() }
            }
        }
        if (dayTasks.isEmpty()) {
            BottomNavSpacer()
        }
    }

    if (showCalendar) {
        TaskDayCalendarDialog(
            initialDay = selectedDay,
            onDismiss = { showCalendar = false },
            onConfirm = { picked ->
                dayKey = TaskDay.key(picked)
                showCalendar = false
            },
        )
    }

    pendingDelete?.let { task ->
        ConfirmDialog(
            title = stringResource(R.string.task_delete_confirm_title),
            body = stringResource(R.string.task_delete_confirm_body, task.title),
            confirmLabel = stringResource(R.string.task_delete),
            onConfirm = { onDeleteTask(task.id) },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun TaskDaySelector(
    day: LocalDate,
    isToday: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onJumpToday: () -> Unit,
    onOpenCalendar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    val longLabel = remember(day) {
        day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.getDefault()))
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Palette.Card)
            .border(1.dp, Palette.BorderSoft, shape)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BareIconButton(
            icon = TermFoldIcons.Back,
            contentDescription = stringResource(R.string.task_day_previous),
            onClick = onPrevious,
            tint = Palette.TextDim,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onOpenCalendar)
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (isToday) stringResource(R.string.task_day_today) else longLabel,
                style = MaterialTheme.typography.titleSmall,
                color = Palette.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (isToday) {
                Text(
                    text = longLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        BareIconButton(
            icon = TermFoldIcons.Forward,
            contentDescription = stringResource(R.string.task_day_next),
            onClick = onNext,
            tint = Palette.TextDim,
        )
        if (!isToday) {
            TextButton(onClick = onJumpToday) {
                Text(stringResource(R.string.task_day_today), color = Palette.Accent)
            }
        }
    }
}

@Composable
private fun TaskStatusSectionHeader(
    status: TaskStatus,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GroupLabel(text = stringResource(status.labelRes).uppercase())
        Spacer(Modifier.width(8.dp))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = Mono),
            color = Palette.TextFaint,
        )
        Spacer(Modifier.weight(1f))
        Icon(
            imageVector = if (expanded) TermFoldIcons.ChevronUp else TermFoldIcons.ChevronDown,
            contentDescription = null,
            tint = Palette.TextFaint,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun TaskCard(
    task: TaskItem,
    projectName: String,
    projectTintIndex: Int,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onQuickStatus: (TaskStatus) -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Palette.Card)
            .border(1.dp, Palette.BorderSoft, shape)
            .clickable(onClick = onClick)
            .animateContentSize()
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(folderTint(projectTintIndex).copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    TermFoldIcons.FileText,
                    contentDescription = null,
                    tint = folderTint(projectTintIndex),
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Palette.Text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = projectName,
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (task.description.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = task.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.TextFaint,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (task.attachments.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.task_attachments_count, task.attachments.size),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = Mono),
                        color = Palette.TextFaint,
                    )
                }
            }
            BareIconButton(
                icon = TermFoldIcons.Trash,
                contentDescription = stringResource(R.string.task_delete),
                onClick = onDelete,
                tint = Palette.TextFaint,
                modifier = Modifier,
            )
        }
        Spacer(Modifier.height(10.dp))
        TaskQuickStatusRow(current = task.status, onSelect = onQuickStatus)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskQuickStatusRow(current: TaskStatus, onSelect: (TaskStatus) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TaskStatus.displayOrder.forEach { status ->
            FilterChip(
                selected = status == current,
                onClick = { onSelect(status) },
                label = { Text(stringResource(status.labelRes), maxLines = 1) },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskDayCalendarDialog(
    initialDay: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDay.toPickerMillis(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        onConfirm(millis.toLocalDate())
                    }
                },
            ) {
                Text(stringResource(R.string.action_save), color = Palette.Accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), color = Palette.TextDim)
            }
        },
        colors = DatePickerDefaults.colors(
            containerColor = Palette.Card,
            titleContentColor = Palette.Text,
            headlineContentColor = Palette.Text,
            weekdayContentColor = Palette.TextDim,
            subheadContentColor = Palette.TextDim,
            yearContentColor = Palette.TextDim,
            currentYearContentColor = Palette.Accent,
            selectedYearContainerColor = Palette.Accent.copy(alpha = 0.35f),
            selectedYearContentColor = Palette.Text,
            dayContentColor = Palette.Text,
            selectedDayContainerColor = Palette.Accent,
            selectedDayContentColor = Palette.Bg,
            todayContentColor = Palette.Accent,
            todayDateBorderColor = Palette.Accent,
        ),
    ) {
        DatePicker(
            state = pickerState,
            title = {
                Text(
                    text = stringResource(R.string.task_day_picker_title),
                    modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp),
                )
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskFormScreen(
    folders: List<Folder>,
    dayKey: String,
    existing: TaskItem?,
    onBack: () -> Unit,
    onConfirm: (folderId: String, title: String, description: String, status: TaskStatus, uris: List<Uri>) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    val isEdit = existing != null
    var title by remember(existing?.id) { mutableStateOf(existing?.title.orEmpty()) }
    var description by remember(existing?.id) { mutableStateOf(existing?.description.orEmpty()) }
    var status by remember(existing?.id) { mutableStateOf(existing?.status ?: TaskStatus.TODO) }
    var folderId by remember(existing?.id) {
        mutableStateOf(existing?.folderId ?: folders.firstOrNull()?.id.orEmpty())
    }
    var pendingUris by remember(existing?.id) { mutableStateOf<List<Uri>>(emptyList()) }
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        uris.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
        pendingUris = pendingUris + uris
    }

    val titleRes = if (isEdit) R.string.task_edit_title else R.string.task_add
    val canSave = title.isNotBlank() && folderId.isNotBlank()

    Column(modifier = modifier.fillMaxSize()) {
        WorkflowsSubscreenHeader(
            onBack = onBack,
            title = stringResource(titleRes),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.task_day_label, dayKey),
                style = MaterialTheme.typography.labelMedium,
                color = Palette.TextFaint,
            )
            TaskTextField(
                value = title,
                onValueChange = { title = it },
                label = stringResource(R.string.task_field_title),
                singleLine = true,
            )
            TaskTextField(
                value = description,
                onValueChange = { description = it },
                label = stringResource(R.string.task_field_description),
                singleLine = false,
                minLines = 3,
            )
            Text(
                text = stringResource(R.string.task_field_status),
                style = MaterialTheme.typography.labelMedium,
                color = Palette.TextDim,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TaskStatus.displayOrder.forEach { option ->
                    FilterChip(
                        selected = status == option,
                        onClick = { status = option },
                        label = { Text(stringResource(option.labelRes)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Palette.Accent.copy(alpha = 0.22f),
                            containerColor = Palette.Field,
                            selectedLabelColor = Palette.Text,
                            labelColor = Palette.TextDim,
                        ),
                    )
                }
            }
            Text(
                text = stringResource(R.string.task_field_project),
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
                        FilterChip(
                            selected = folder.id == folderId,
                            onClick = { folderId = folder.id },
                            label = {
                                Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            Text(
                text = stringResource(R.string.task_field_attachments),
                style = MaterialTheme.typography.labelMedium,
                color = Palette.TextDim,
            )
            if (existing?.attachments?.isNotEmpty() == true) {
                existing.attachments.forEach { att ->
                    Text(
                        text = att.displayName,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = Mono, fontSize = 12.sp),
                        color = Palette.TextFaint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            pendingUris.forEach { uri ->
                Text(
                    text = uri.lastPathSegment ?: uri.toString(),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = Mono, fontSize = 12.sp),
                    color = Palette.Accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            PrimaryButton(
                icon = TermFoldIcons.Upload,
                label = stringResource(R.string.task_add_attachments),
                onClick = { picker.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.action_cancel), color = Palette.TextDim)
            }
            Spacer(Modifier.width(8.dp))
            TextButton(
                onClick = { onConfirm(folderId, title, description, status, pendingUris) },
                enabled = canSave,
            ) {
                Text(
                    stringResource(R.string.action_save),
                    color = if (canSave) Palette.Accent else Palette.TextFaint,
                )
            }
        }
        BottomNavSpacer()
    }
}

private fun LocalDate.toPickerMillis(): Long =
    atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

private fun Long.toLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

@Composable
private fun TaskTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    singleLine: Boolean,
    minLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = singleLine,
        minLines = minLines,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Palette.Accent,
            unfocusedBorderColor = Palette.BorderSoft,
            focusedLabelColor = Palette.TextDim,
            unfocusedLabelColor = Palette.TextFaint,
            focusedTextColor = Palette.Text,
            unfocusedTextColor = Palette.Text,
            cursorColor = Palette.Accent,
        ),
    )
}
