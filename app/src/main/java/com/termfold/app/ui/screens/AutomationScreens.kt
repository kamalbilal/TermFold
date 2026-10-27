package com.termfold.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.annotation.StringRes
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.termfold.app.R
import com.termfold.app.automation.AutomationProject
import com.termfold.app.core.Folder
import com.termfold.app.ui.components.BareIconButton
import com.termfold.app.ui.components.BottomNavSpacer
import com.termfold.app.ui.components.CountChevron
import com.termfold.app.ui.components.LeadingTile
import com.termfold.app.ui.components.ListRow
import com.termfold.app.ui.components.GroupLabel
import com.termfold.app.ui.components.PrimaryButton
import com.termfold.app.ui.components.RowSpacer
import com.termfold.app.ui.components.SectionTitle
import com.termfold.app.ui.theme.Palette
import com.termfold.app.ui.theme.TermFoldIcons
import com.termfold.app.ui.theme.folderTint

enum class WorkflowsSection(@param:StringRes val labelRes: Int) {
    AUTOMATION(R.string.workflows_tab_automation),
    PR_REVIEWER(R.string.workflows_tab_pr_reviewer),
    PRODUCT_MANAGER(R.string.workflows_tab_product_manager),
    MORE(R.string.workflows_tab_more),
}

/** Workflows bottom-nav home: a list of sections; each opens its own screen. */
@Composable
fun WorkflowsScreen(
    onOpenSection: (WorkflowsSection) -> Unit,
    useGrid: Boolean,
    modifier: Modifier = Modifier,
) {
    val edge = if (useGrid) 30.dp else 22.dp

    Column(modifier = modifier.fillMaxSize()) {
        SectionTitle(
            text = stringResource(R.string.workflows_title),
            modifier = Modifier.padding(start = edge, end = 16.dp, top = 6.dp),
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            items(WorkflowsSection.entries, key = { it.name }) { section ->
                ListRow(
                    title = stringResource(section.labelRes),
                    leading = {
                        LeadingTile(
                            tint = folderTint(
                                when (section) {
                                    WorkflowsSection.AUTOMATION -> 0
                                    WorkflowsSection.PR_REVIEWER -> 1
                                    WorkflowsSection.PRODUCT_MANAGER -> 2
                                    WorkflowsSection.MORE -> 3
                                },
                            ),
                            icon = when (section) {
                                WorkflowsSection.AUTOMATION -> TermFoldIcons.Sparkle
                                WorkflowsSection.PR_REVIEWER -> TermFoldIcons.GitBranch
                                WorkflowsSection.PRODUCT_MANAGER -> TermFoldIcons.Sparkle
                                WorkflowsSection.MORE -> TermFoldIcons.More
                            },
                        )
                    },
                    trailing = {
                        Icon(
                            TermFoldIcons.ChevronRight,
                            contentDescription = null,
                            tint = Palette.TextFaint,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    onClick = { onOpenSection(section) },
                )
                RowSpacer()
            }
            item { BottomNavSpacer() }
        }
    }
}

@Composable
fun WorkflowsSectionScreen(
    section: WorkflowsSection,
    projects: List<AutomationProject>,
    codingFolders: List<Folder>,
    prReviews: List<com.termfold.app.prreview.PrReviewSession> = emptyList(),
    prReviewError: String? = null,
    pmSessions: List<com.termfold.app.pm.PmSession> = emptyList(),
    pmError: String? = null,
    onBack: () -> Unit,
    onOpenProject: (String) -> Unit,
    onOpenProjectOptions: (String) -> Unit,
    onAddProject: () -> Unit,
    onOpenPrReview: (String) -> Unit,
    onOpenPrReviewOptions: (String) -> Unit = {},
    onStartPrReview: (String) -> Unit,
    onOpenPmSession: (String) -> Unit = {},
    onOpenPmSessionOptions: (String) -> Unit = {},
    onStartPmDiscovery: (String, String) -> Unit = { _, _ -> },
    onStartPmRelease: (String, String, String, String) -> Unit = { _, _, _, _ -> },
    useGrid: Boolean,
    modifier: Modifier = Modifier,
) {
    when (section) {
        WorkflowsSection.AUTOMATION -> WorkflowsAutomationListScreen(
            projects = projects,
            codingFolders = codingFolders,
            onBack = onBack,
            onOpenProject = onOpenProject,
            onOpenProjectOptions = onOpenProjectOptions,
            onAddProject = onAddProject,
            useGrid = useGrid,
            modifier = modifier,
        )
        WorkflowsSection.PR_REVIEWER -> PrReviewerListScreen(
            sessions = prReviews,
            error = prReviewError,
            onBack = onBack,
            onOpenSession = onOpenPrReview,
            onOpenSessionOptions = onOpenPrReviewOptions,
            onStart = onStartPrReview,
            useGrid = useGrid,
            modifier = modifier,
        )
        WorkflowsSection.PRODUCT_MANAGER -> PmSessionListScreen(
            sessions = pmSessions,
            codingFolders = codingFolders,
            pmError = pmError,
            onBack = onBack,
            onOpenSession = onOpenPmSession,
            onOpenSessionOptions = onOpenPmSessionOptions,
            onStartDiscovery = onStartPmDiscovery,
            onStartRelease = onStartPmRelease,
            useGrid = useGrid,
            modifier = modifier,
        )
        WorkflowsSection.MORE -> WorkflowsMoreScreen(
            onBack = onBack,
            useGrid = useGrid,
            modifier = modifier,
        )
    }
}

@Composable
private fun WorkflowsMoreScreen(
    onBack: () -> Unit,
    useGrid: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        WorkflowsSubscreenHeader(
            onBack = onBack,
            title = stringResource(R.string.workflows_tab_more),
        )
        EmptyState(
            title = stringResource(R.string.workflows_future_title),
            hint = stringResource(R.string.workflows_future_subtitle),
        )
    }
}

@Composable
private fun WorkflowsAutomationListScreen(
    projects: List<AutomationProject>,
    codingFolders: List<Folder>,
    onBack: () -> Unit,
    onOpenProject: (String) -> Unit,
    onOpenProjectOptions: (String) -> Unit,
    onAddProject: () -> Unit,
    useGrid: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        WorkflowsSubscreenHeader(
            onBack = onBack,
            title = stringResource(R.string.workflows_tab_automation),
            onAdd = onAddProject,
        )
        Spacer(Modifier.height(8.dp))
        when {
            projects.isEmpty() -> EmptyState(
                title = stringResource(R.string.workflows_empty_hint),
                hint = stringResource(R.string.workflows_empty_subtitle),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                items(projects, key = { it.id }) { project ->
                    val linkName = codingFolders.firstOrNull { it.id == project.linkedFolderId }?.name
                    ListRow(
                        title = project.name,
                        subtitle = linkName?.let { name ->
                            {
                                Text(
                                    stringResource(R.string.automation_linked_to, name),
                                    color = Palette.TextDim,
                                )
                            }
                        },
                        leading = {
                            LeadingTile(tint = folderTint(project.tint), icon = TermFoldIcons.Sparkle)
                        },
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CountChevron(project.sessions.size)
                                BareIconButton(
                                    icon = TermFoldIcons.More,
                                    contentDescription = stringResource(R.string.cd_more),
                                    onClick = { onOpenProjectOptions(project.id) },
                                    size = 34,
                                    tint = Palette.TextFaint,
                                )
                            }
                        },
                        onClick = { onOpenProject(project.id) },
                    )
                    RowSpacer()
                }
                item { BottomNavSpacer() }
            }
        }
    }
}

@Composable
fun AutomationProjectDetailScreen(
    project: AutomationProject,
    codingFolders: List<Folder>,
    onBack: () -> Unit,
    onOpenSession: (String) -> Unit,
    onOpenSessionOptions: (String) -> Unit,
    onOpenProjectOptions: () -> Unit,
    onAddSession: () -> Unit,
    onLinkFolder: (String?) -> Unit,
    modifier: Modifier = Modifier,
    wide: Boolean = false,
) {
    var linkPicker by remember { mutableStateOf(false) }
    val linked = codingFolders.firstOrNull { it.id == project.linkedFolderId }
    val brokenLink = project.linkedFolderId != null && linked == null
    val edge = if (wide) 30.dp else 16.dp

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BareIconButton(
                icon = TermFoldIcons.Back,
                contentDescription = stringResource(R.string.cd_back),
                onClick = onBack,
            )
            Spacer(Modifier.weight(1f))
            PrimaryButton(
                icon = TermFoldIcons.Plus,
                label = stringResource(R.string.automation_new_session),
                onClick = onAddSession,
                compact = true,
            )
            BareIconButton(
                icon = TermFoldIcons.More,
                contentDescription = stringResource(R.string.cd_more),
                onClick = onOpenProjectOptions,
            )
        }
        SectionTitle(
            text = project.name,
            modifier = Modifier.padding(horizontal = edge, vertical = 4.dp),
        )
        Column(
            modifier = Modifier
                .padding(horizontal = edge)
                .clip(RoundedCornerShape(12.dp))
                .background(Palette.Card)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(
                text = when {
                    brokenLink -> stringResource(R.string.automation_link_broken)
                    linked != null -> stringResource(R.string.automation_linked_to, linked.name)
                    else -> stringResource(R.string.automation_not_linked)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (brokenLink) Palette.Accent else Palette.TextDim,
            )
            TextButton(onClick = { linkPicker = true }) {
                Text(stringResource(R.string.automation_change_link))
            }
            if (brokenLink) {
                TextButton(onClick = { onLinkFolder(null) }) {
                    Text(stringResource(R.string.automation_unlink))
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        GroupLabel(
            stringResource(R.string.workflows_section_automation),
            modifier = Modifier.padding(horizontal = edge),
        )
        Spacer(Modifier.height(8.dp))
        if (project.sessions.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.automation_no_sessions),
                hint = stringResource(R.string.automation_no_sessions_hint),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = edge),
            ) {
                items(project.sessions, key = { it.id }) { session ->
                    ListRow(
                        title = session.name,
                        leading = {
                            LeadingTile(tint = folderTint(session.tint), icon = TermFoldIcons.Terminal)
                        },
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                BareIconButton(
                                    icon = TermFoldIcons.More,
                                    contentDescription = stringResource(R.string.cd_more),
                                    onClick = { onOpenSessionOptions(session.id) },
                                    size = 34,
                                    tint = Palette.TextFaint,
                                )
                                Icon(
                                    TermFoldIcons.ChevronRight,
                                    contentDescription = null,
                                    tint = Palette.TextFaint,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        },
                        onClick = { onOpenSession(session.id) },
                    )
                    RowSpacer()
                }
                item { BottomNavSpacer() }
            }
        }
    }
    if (linkPicker) {
        AlertDialog(
            onDismissRequest = { linkPicker = false },
            title = { Text(stringResource(R.string.automation_pick_project)) },
            text = {
                LazyColumn {
                    item {
                        TextButton(onClick = { onLinkFolder(null); linkPicker = false }) {
                            Text(stringResource(R.string.automation_unlink))
                        }
                    }
                    items(codingFolders) { folder ->
                        TextButton(onClick = { onLinkFolder(folder.id); linkPicker = false }) {
                            Text(folder.name)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { linkPicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
internal fun WorkflowsSubscreenHeader(
    onBack: () -> Unit,
    title: String,
    onAdd: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 16.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BareIconButton(
            icon = TermFoldIcons.Back,
            contentDescription = stringResource(R.string.cd_back),
            onClick = onBack,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = Palette.Text,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp),
        )
        if (onAdd != null) {
            PrimaryButton(
                icon = TermFoldIcons.Plus,
                label = stringResource(R.string.workflows_new),
                onClick = onAdd,
                compact = true,
            )
        }
    }
}

@Composable
fun PrReviewerListScreen(
    sessions: List<com.termfold.app.prreview.PrReviewSession>,
    error: String?,
    onBack: () -> Unit,
    onOpenSession: (String) -> Unit,
    onOpenSessionOptions: (String) -> Unit,
    onStart: (String) -> Unit,
    useGrid: Boolean,
    modifier: Modifier = Modifier,
) {
    var url by remember { mutableStateOf("") }
    Column(modifier = modifier.fillMaxSize()) {
        WorkflowsSubscreenHeader(
            onBack = onBack,
            title = stringResource(R.string.workflows_tab_pr_reviewer),
        )
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.pr_review_url_hint), color = Palette.TextFaint) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Palette.Text,
                    unfocusedTextColor = Palette.Text,
                    cursorColor = Palette.Accent,
                    focusedBorderColor = Palette.Accent,
                    unfocusedBorderColor = Palette.Border,
                ),
            )
            Spacer(Modifier.height(8.dp))
            PrimaryButton(
                icon = TermFoldIcons.GitBranch,
                label = stringResource(R.string.pr_review_start),
                onClick = { if (url.isNotBlank()) onStart(url.trim()) },
                compact = true,
            )
            if (!error.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(error, color = Palette.Accent, style = MaterialTheme.typography.bodySmall)
            }
        }
        when {
            sessions.isEmpty() -> EmptyState(
                title = stringResource(R.string.pr_review_empty_hint),
                hint = stringResource(R.string.pr_review_empty_subtitle),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                items(sessions, key = { it.id }) { session ->
                    ListRow(
                        title = session.name,
                        subtitle = {
                            Text(
                                "${session.owner}/${session.repo}#${session.number}",
                                color = Palette.TextDim,
                            )
                        },
                        leading = {
                            LeadingTile(tint = folderTint(session.tint), icon = TermFoldIcons.GitBranch)
                        },
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                BareIconButton(
                                    icon = TermFoldIcons.More,
                                    contentDescription = stringResource(R.string.cd_more),
                                    onClick = { onOpenSessionOptions(session.id) },
                                    size = 34,
                                    tint = Palette.TextFaint,
                                )
                                Icon(
                                    TermFoldIcons.ChevronRight,
                                    contentDescription = null,
                                    tint = Palette.TextFaint,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        },
                        onClick = { onOpenSession(session.id) },
                    )
                    RowSpacer()
                }
                item { BottomNavSpacer() }
            }
        }
    }
}
