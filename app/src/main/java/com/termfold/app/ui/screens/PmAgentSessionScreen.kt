package com.termfold.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.termfold.app.R
import com.termfold.app.acp.AcpItem
import com.termfold.app.core.Folder
import com.termfold.app.core.Session
import com.termfold.app.gitworkspace.WorkspaceIds
import com.termfold.app.gitworkspace.WorkspaceStore
import com.termfold.app.pm.forStringResourceArg
import com.termfold.app.pm.PmBriefFiles
import com.termfold.app.pm.PmCompetitor
import com.termfold.app.pm.PmDecisionMemoParser
import com.termfold.app.pm.PmKeys
import com.termfold.app.pm.PmMode
import com.termfold.app.pm.PmPaths
import com.termfold.app.pm.PmPrompt
import com.termfold.app.pm.PmSession
import com.termfold.app.pm.PmSessionContext
import com.termfold.app.pm.memory.PmMemoryRepository
import com.termfold.app.prreview.DoneKind
import com.termfold.app.preview.BrowserBridge
import com.termfold.app.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val STUB_WORKSPACE_ID = "pm-stub"

@Composable
fun PmAgentSessionScreen(
    session: PmSession,
    codingFolder: Folder?,
    onBack: () -> Unit,
    onUpdateSession: (PmSession) -> Unit,
    onDoneKind: suspend (String) -> DoneKind,
    onApplyDone: (String) -> Unit,
    onRefreshResearch: (sessionId: String, showProgress: Boolean) -> Unit,
    onSetViewBranch: (sessionId: String, branch: String) -> Unit,
    modifier: Modifier = Modifier,
    filesOpen: Boolean = false,
    onToggleFiles: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmDone by remember { mutableStateOf(false) }
    var showViewBranchPicker by remember { mutableStateOf(false) }
    var showMemory by remember { mutableStateOf(false) }
    var researchExpanded by remember { mutableStateOf(false) }
    var competitorName by remember { mutableStateOf("") }
    var competitorUrl by remember { mutableStateOf("") }
    var agentWasBusy by remember(session.id) { mutableStateOf(false) }
    var firstUserMessageId by remember(session.id) { mutableStateOf<Long?>(null) }
    var lastUserMessageId by remember(session.id) { mutableStateOf<Long?>(null) }
    var memosByUserMessageId by remember(session.id) { mutableStateOf(mapOf<Long, String>()) }

    val goldenGuest = codingFolder?.let { WorkspaceStore.goldenGuestPath(context, it) }
    val copyGuest = if (session.mode == PmMode.RELEASE && codingFolder != null && session.workspaceId != null) {
        WorkspaceStore.guestPath(context, codingFolder, session.workspaceId)
    } else {
        null
    }
    val guestDir = copyGuest ?: goldenGuest ?: PmPaths.sessionGuest(session.id)

    DisposableEffect(session.id) {
        PmSessionContext.activeSessionId = session.id
        BrowserBridge.pmSessionId = session.id
        BrowserBridge.writeBrowserConfig(context.applicationContext)
        onDispose {
            if (PmSessionContext.activeSessionId == session.id) {
                PmSessionContext.activeSessionId = null
            }
            if (BrowserBridge.pmSessionId == session.id) {
                BrowserBridge.pmSessionId = null
            }
            BrowserBridge.writeBrowserConfig(context.applicationContext)
        }
    }

    LaunchedEffect(session.id) {
        PmPaths.ensureStub(context, session.id)
        if (session.researchPackCache.isNullOrBlank()) {
            onRefreshResearch(session.id, false)
        }
    }

    val discoveryWorkspaceId = if (session.mode == PmMode.DISCOVERY) WorkspaceIds.GOLDEN else null
    val branchChip = when (session.mode) {
        PmMode.DISCOVERY -> stringResource(
            R.string.pm_viewing_branch,
            session.viewBranch.ifBlank { "HEAD" }.forStringResourceArg(),
        )
        PmMode.RELEASE -> session.branch.ifBlank { session.viewBranch }
    }

    val folder = Folder(
        id = "pm-${session.id}",
        name = session.name,
        path = PmPaths.hostSessionDir(context, session.id).absolutePath,
        tint = session.tint,
        sessions = listOf(
            Session(
                id = session.id,
                name = session.name,
                command = "",
                tint = session.tint,
                acpAgentId = session.acpAgentId,
                workspaceId = session.workspaceId
                    ?: discoveryWorkspaceId
                    ?: STUB_WORKSPACE_ID,
            ),
        ),
    )

    val readOnlyGolden = if (session.mode == PmMode.DISCOVERY) goldenGuest else null

    CompositionLocalProvider(
        LocalAutomationReadOnlyGuestRoot provides readOnlyGolden,
        LocalPmViewBranchLabel provides if (session.mode == PmMode.DISCOVERY) session.viewBranch else null,
    ) {
        AgentSessionScreen(
            folder = folder,
            sessionId = session.id,
            workspaceId = session.workspaceId ?: discoveryWorkspaceId ?: STUB_WORKSPACE_ID,
            agentId = session.acpAgentId,
            onBack = onBack,
            modifier = modifier,
            filesOpen = filesOpen,
            onToggleFiles = onToggleFiles,
            sessionKeyOverride = PmKeys.acpSessionKey(session.id),
            guestDirOverride = guestDir,
            hideBranchPicker = true,
            branchChipOverride = if (session.mode == PmMode.DISCOVERY) branchChip else null,
            onBranchChipClick = if (session.mode == PmMode.DISCOVERY && goldenGuest != null) {
                { showViewBranchPicker = true }
            } else {
                null
            },
            automationReadOnlyGuestRoot = readOnlyGolden,
            pmMode = true,
            promptPrefix = { query ->
                PmPrompt.prefix(context, session, userQuery = query)
            },
            onPmSessionMemory = { showMemory = true },
            turnFooter = { userMessageId, turnItems ->
                Column(Modifier.fillMaxWidth()) {
                    if (userMessageId == firstUserMessageId) {
                        PmSessionChrome(
                            session = session,
                            researchExpanded = researchExpanded,
                            onToggleResearchLog = { researchExpanded = !researchExpanded },
                            includeCompetitive = session.includeCompetitiveResearch,
                            onIncludeCompetitiveChange = { enabled ->
                                onUpdateSession(session.copy(includeCompetitiveResearch = enabled))
                            },
                            competitorName = competitorName,
                            competitorUrl = competitorUrl,
                            onCompetitorNameChange = { competitorName = it },
                            onCompetitorUrlChange = { competitorUrl = it },
                            onAddCompetitor = {
                                if (competitorName.isBlank()) return@PmSessionChrome
                                val next = session.copy(
                                    competitors = session.competitors + PmCompetitor(
                                        name = competitorName.trim(),
                                        homepageUrl = competitorUrl.trim(),
                                    ),
                                )
                                PmBriefFiles.syncCompetitorsMarkdown(context, next)
                                onUpdateSession(next)
                                competitorName = ""
                                competitorUrl = ""
                            },
                            onRefreshResearch = { onRefreshResearch(session.id, true) },
                        )
                    }
                    val memoJson = pmMemoJsonForTurn(
                        userMessageId = userMessageId,
                        turnItems = turnItems,
                        memosByUserMessageId = memosByUserMessageId,
                        lastUserMessageId = lastUserMessageId,
                        sessionLastDecisionJson = session.lastDecisionJson,
                    )
                    PmDecisionChrome(
                        memo = memoJson?.let {
                            runCatching { PmDecisionMemoParser.fromJson(org.json.JSONObject(it)) }.getOrNull()
                        },
                    )
                }
            },
            rewriteAgentText = PmDecisionMemoParser::stripFences,
            onAcpSnapshot = { items: List<AcpItem>, busy ->
                val users = items.filterIsInstance<AcpItem.UserMessage>()
                firstUserMessageId = users.firstOrNull()?.id
                val lastUserId = users.lastOrNull()?.id
                lastUserMessageId = lastUserId
                if (agentWasBusy && !busy) {
                    val turnUserId = lastUserId
                    val turnItems = turnUserId?.let { agentChatItemsForUserTurn(items, it) } ?: emptyList()
                    val text = turnItems.filterIsInstance<AcpItem.AgentText>().map { it.text }.joinToString("\n")
                    PmDecisionMemoParser.parseFromAgentText(text)?.let { memo ->
                        val json = PmDecisionMemoParser.toJson(memo)
                        if (turnUserId != null) {
                            memosByUserMessageId = memosByUserMessageId + (turnUserId to json)
                        }
                        onUpdateSession(session.copy(lastDecisionJson = json))
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                PmMemoryRepository(context, session.id).upsertChunk(
                                    kind = "decision",
                                    title = memo.recommendation.take(120).ifBlank { "Decision memo" },
                                    searchText = memo.recommendation,
                                    body = text.take(12_000),
                                )
                            }
                        }
                    }
                }
                agentWasBusy = busy
            },
            onReviewDone = if (session.mode == PmMode.RELEASE && session.workspaceId != null) {
                {
                    scope.launch {
                        when (onDoneKind(session.id)) {
                            DoneKind.AlreadyGone, DoneKind.DeleteNow -> onApplyDone(session.id)
                            DoneKind.ConfirmDirtyOrUnpushed -> confirmDone = true
                        }
                    }
                }
            } else {
                null
            },
        )
    }

    if (showViewBranchPicker && codingFolder != null && goldenGuest != null) {
        BranchPickerDialog(
            guestPath = goldenGuest,
            goldenProjectId = codingFolder.id,
            workspaceId = WorkspaceIds.GOLDEN,
            readOnly = true,
            onDismiss = { showViewBranchPicker = false },
            onSwitched = { },
            onViewBranch = { branch ->
                showViewBranchPicker = false
                onSetViewBranch(session.id, branch)
            },
        )
    }

    if (showMemory) {
        AutomationMemoryManagerDialog(
            mode = AutomationMemoryManagerMode.PmSession(session.id),
            onDismiss = { showMemory = false },
        )
    }

    if (confirmDone) {
        AlertDialog(
            onDismissRequest = { confirmDone = false },
            title = { Text(stringResource(R.string.workspace_dirty_title)) },
            text = { Text(stringResource(R.string.workspace_dirty_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDone = false
                    onApplyDone(session.id)
                }) {
                    Text(stringResource(R.string.pr_review_done))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDone = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun PmSessionChrome(
    session: PmSession,
    researchExpanded: Boolean,
    onToggleResearchLog: () -> Unit,
    includeCompetitive: Boolean,
    onIncludeCompetitiveChange: (Boolean) -> Unit,
    competitorName: String,
    competitorUrl: String,
    onCompetitorNameChange: (String) -> Unit,
    onCompetitorUrlChange: (String) -> Unit,
    onAddCompetitor: () -> Unit,
    onRefreshResearch: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            when (session.mode) {
                PmMode.DISCOVERY -> stringResource(R.string.pm_mode_discovery)
                PmMode.RELEASE -> stringResource(R.string.pm_mode_release)
            },
            style = MaterialTheme.typography.labelMedium,
            color = Palette.TextDim,
        )
        Spacer(Modifier.height(8.dp))
        FilterChip(
            selected = includeCompetitive,
            onClick = { onIncludeCompetitiveChange(!includeCompetitive) },
            label = { Text(stringResource(R.string.pm_include_competitive)) },
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = competitorName,
            onValueChange = onCompetitorNameChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.pm_competitor_name)) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Palette.Text,
                unfocusedTextColor = Palette.Text,
            ),
        )
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = competitorUrl,
            onValueChange = onCompetitorUrlChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.pm_competitor_url)) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Palette.Text,
                unfocusedTextColor = Palette.Text,
            ),
        )
        TextButton(onClick = onAddCompetitor) {
            Text(stringResource(R.string.pm_add_competitor))
        }
        TextButton(onClick = onRefreshResearch) {
            Text(stringResource(R.string.pm_refresh_research))
        }
        TextButton(onClick = onToggleResearchLog) {
            Text(
                if (researchExpanded) {
                    stringResource(R.string.pm_hide_research_log)
                } else {
                    stringResource(R.string.pm_show_research_log)
                },
            )
        }
        if (researchExpanded) {
            session.researchLog.takeLast(8).forEach { entry ->
                Text(
                    "${entry.summary} (${entry.outcome})",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextFaint,
                )
            }
        }
    }
}

private fun pmDecisionJsonFromTurnItems(turnItems: List<AcpItem>): String? {
    val text = turnItems.filterIsInstance<AcpItem.AgentText>().map { it.text }.joinToString("\n")
    val memo = PmDecisionMemoParser.parseFromAgentText(text) ?: return null
    return PmDecisionMemoParser.toJson(memo)
}

private fun pmMemoJsonForTurn(
    userMessageId: Long,
    turnItems: List<AcpItem>,
    memosByUserMessageId: Map<Long, String>,
    lastUserMessageId: Long?,
    sessionLastDecisionJson: String?,
): String? {
    memosByUserMessageId[userMessageId]?.let { return it }
    pmDecisionJsonFromTurnItems(turnItems)?.let { return it }
    if (
        userMessageId == lastUserMessageId &&
        agentChatTurnHasAgentReply(turnItems) &&
        !sessionLastDecisionJson.isNullOrBlank()
    ) {
        return sessionLastDecisionJson
    }
    return null
}
