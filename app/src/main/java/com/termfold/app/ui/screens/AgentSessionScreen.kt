package com.termfold.app.ui.screens

import android.content.ClipData
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.content.MediaType
import androidx.compose.foundation.content.consume
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.content.hasMediaType
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.termfold.app.R
import com.termfold.app.ui.components.LaunchedWhileVisible
import com.termfold.app.acp.AcpAgent
import com.termfold.app.acp.AcpDrafts
import com.termfold.app.acp.AcpBlock
import com.termfold.app.acp.AcpImageBitmaps
import com.termfold.app.acp.AcpClient
import com.termfold.app.acp.CursorAcpPrefs
import com.termfold.app.acp.AcpCommand
import com.termfold.app.acp.AcpSetting
import com.termfold.app.acp.AcpItem
import com.termfold.app.acp.AcpPendingPermission
import com.termfold.app.acp.AcpPhase
import com.termfold.app.acp.AcpRegistry
import com.termfold.app.acp.AcpSessions
import com.termfold.app.acp.AcpUiState
import com.termfold.app.acp.checkpoints.CheckpointBusy
import com.termfold.app.acp.PastedImages
import com.termfold.app.core.Folder
import com.termfold.app.preview.Preview
import com.termfold.app.shell.ShellConfig
import com.termfold.app.shell.TerminalHost
import com.termfold.app.ui.components.AgentIconImage
import com.termfold.app.ui.components.BareIconButton
import com.termfold.app.ui.components.Markdown
import com.termfold.app.ui.theme.Mono
import com.termfold.app.ui.theme.Palette
import com.termfold.app.ui.theme.TermFoldIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.termfold.app.ui.theme.Motion

// A comfortable reading width: on a tablet the chat stays a column, not a banner.
private val ContentMaxWidth = 680.dp

/** A paste this long (or this many lines) becomes a text-file attachment instead of input. */
private const val LongPasteChars = 1_200
private const val LongPasteLines = 25

/** The user's side of the conversation: a quiet raised surface, not a coloured bubble. */
private val UserBubble = Color(0xFF1A1A1F)

/**
 * The native agent interface for ACP sessions: the agent runs inside the bundled Debian
 * environment, and everything it says, thinks, and does renders as regular UI instead of
 * terminal output.
 */
@Composable
fun AgentSessionScreen(
    folder: Folder,
    sessionId: String,
    workspaceId: String = com.termfold.app.gitworkspace.WorkspaceIds.GOLDEN,
    agentId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    filesOpen: Boolean = false,
    onToggleFiles: () -> Unit = {},
    automationPromptPrefix: (() -> String)? = null,
    promptPrefix: (() -> String)? = null,
    previewProjectDirOverride: String? = null,
    automationReadOnlyGuestRoot: String? = null,
    automationSessionKey: String? = null,
    automationGuardrails: (() -> com.termfold.app.automation.AutomationGuardrailContext?)? = null,
    sessionKeyOverride: String? = null,
    guestDirOverride: String? = null,
    hideBranchPicker: Boolean = false,
    onReviewDone: (() -> Unit)? = null,
    onReviewAnother: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val key = sessionKeyOverride
        ?: com.termfold.app.gitworkspace.SessionKeys.compose(folder.id, workspaceId, sessionId)
    val guestDir = guestDirOverride
        ?: com.termfold.app.gitworkspace.WorkspaceStore.guestPath(context, folder, workspaceId)
    if (guestDir == null) {
        Column(modifier = modifier.fillMaxSize()) {
            SessionHeader(title = agentId, subtitle = folder.name, iconUrl = "", onBack = onBack)
            CenterPanel {
                PanelTitle(stringResource(R.string.workspace_unavailable_title))
                PanelBody(stringResource(R.string.workspace_unavailable_body))
                Spacer(Modifier.height(16.dp))
                PillButton(stringResource(R.string.cd_back), TermFoldIcons.Back, onClick = onBack)
            }
        }
        return
    }
    var branchLabel by remember(workspaceId, guestDir) { mutableStateOf<String?>(null) }
    var branchDirty by remember(guestDir) { mutableStateOf(false) }
    var isGitWorkspace by remember(guestDir) { mutableStateOf(false) }
    var showBranchPicker by remember { mutableStateOf(false) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(guestDir) {
        val git = com.termfold.app.gitworkspace.GitWorkspace
        branchLabel = git.readBranchFromHost(context, guestDir) ?: git.currentBranch(context, guestDir)
        isGitWorkspace = git.isGitRepository(context, guestDir)
        if (isGitWorkspace) {
            git.ensureSessionCopyGitUpstream(context, guestDir)
            syncWorkspaceGit(
                context,
                guestDir,
                onBranch = { branchLabel = it },
                onDirty = { branchDirty = it },
            )
        } else {
            branchDirty = false
        }
    }

    // The registry supplies the launch details and icon; its on-disk cache makes this instant
    // after the first load. Offline with no cache there is nothing to launch, so say so.
    var agent by remember { mutableStateOf<AcpAgent?>(null) }
    var registryAttempt by remember { mutableIntStateOf(0) }
    var registryFailed by remember { mutableStateOf(false) }
    LaunchedEffect(agentId, registryAttempt) {
        registryFailed = false
        agent = AcpRegistry.all(context, forceRefresh = registryAttempt > 0).firstOrNull { it.id == agentId }
        registryFailed = agent == null
    }

    val resolved = agent
    if (resolved == null) {
        Column(modifier = modifier.fillMaxSize()) {
            SessionHeader(title = agentId, subtitle = folder.name, iconUrl = "", onBack = onBack)
            CenterPanel {
                if (agentId in com.termfold.app.acp.AcpRegistry.UNSUPPORTED) {
                    PanelTitle(stringResource(R.string.acp_agent_unsupported_title))
                    PanelBody(stringResource(R.string.acp_antigravity_unsupported))
                } else if (registryFailed) {
                    PanelTitle(stringResource(R.string.acp_agent_unavailable))
                    PanelBody(stringResource(R.string.acp_registry_offline))
                    Spacer(Modifier.height(16.dp))
                    PillButton(stringResource(R.string.action_retry), TermFoldIcons.Refresh) { registryAttempt++ }
                } else {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Palette.Accent)
                }
            }
        }
        return
    }

    val client = remember(key, guestDir) {
        val existing = AcpSessions.client(key)
        if (existing != null && existing.workspaceGuestDir == guestDir) {
            existing
        } else {
            if (existing != null) AcpSessions.close(key)
            AcpSessions.put(
                key,
                AcpClient(
                    context = context.applicationContext,
                    sessionKey = key,
                    agent = resolved,
                    workspaceGuestDir = guestDir,
                    automationPromptPrefix = automationPromptPrefix,
                    extraPromptPrefix = promptPrefix,
                    automationReadOnlyGuestRoot = automationReadOnlyGuestRoot,
                    automationGuardrails = automationGuardrails,
                ),
            )
        }
    }

    LaunchedEffect(key, guestDir) {
        client.start()
        com.termfold.app.notify.SessionWatcher.onSessionStarted(context.applicationContext)
    }

    val state by client.state.collectAsStateWithLifecycle()
    var agentWasBusy by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showAutomationSessionMemory by remember { mutableStateOf(false) }
    var showAutomationNavMemory by remember { mutableStateOf(false) }
    var showMcp by remember { mutableStateOf(false) }
    var showAuxTerminals by remember { mutableStateOf(false) }
    var auxTerminalInitialTabId by remember { mutableStateOf<String?>(null) }
    var pendingAuxTabCloseId by remember { mutableStateOf<String?>(null) }
    val auxTerminalParentKey = remember(folder.id, workspaceId, sessionId) {
        AuxTerminalTabsStore.parentKey(folder.id, workspaceId, sessionId)
    }
    val auxTerminalTabs = rememberAuxTerminalTabs(auxTerminalParentKey)
    var showQuickOpen by remember { mutableStateOf(false) }
    var editingMessage by remember { mutableStateOf<AcpItem.UserMessage?>(null) }
    var editFileChoice by remember { mutableStateOf(false) }
    var pendingEdit by remember {
        mutableStateOf<Triple<Long, String, Pair<List<AcpBlock.Image>, List<AcpBlock.TextFile>>>?>(null)
    }
    val openWorkspaceFile = LocalWorkspaceFileOpener.current
    val isCursor = resolved.id == "cursor"

    LaunchedEffect(state.agentBusy, guestDir, isCursor, isGitWorkspace) {
        if (!isCursor) {
            agentWasBusy = state.agentBusy
            return@LaunchedEffect
        }
        if (agentWasBusy && !state.agentBusy && isGitWorkspace) {
            syncWorkspaceGit(
                context,
                guestDir,
                onBranch = { branchLabel = it },
                onDirty = { branchDirty = it },
            )
        }
        agentWasBusy = state.agentBusy
    }
    LaunchedEffect(guestDir, isCursor, isGitWorkspace) {
        if (!isCursor || !isGitWorkspace) return@LaunchedEffect
        while (true) {
            delay(3_000)
            syncWorkspaceGit(
                context,
                guestDir,
                onBranch = { branchLabel = it },
                onDirty = { branchDirty = it },
            )
        }
    }
    androidx.compose.runtime.DisposableEffect(lifecycleOwner, guestDir, isCursor, isGitWorkspace) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME && isCursor && isGitWorkspace) {
                scope.launch {
                    syncWorkspaceGit(
                context,
                guestDir,
                onBranch = { branchLabel = it },
                onDirty = { branchDirty = it },
            )
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var stickyUserMessagesEnabled by remember {
        mutableStateOf(com.termfold.app.acp.AcpChatPrefs.stickyUserMessages(context))
    }
    val lifecycleOwnerForSticky = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwnerForSticky) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                stickyUserMessagesEnabled = com.termfold.app.acp.AcpChatPrefs.stickyUserMessages(context)
            }
        }
        lifecycleOwnerForSticky.lifecycle.addObserver(observer)
        onDispose { lifecycleOwnerForSticky.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        SessionHeader(
            title = resolved.name,
            subtitle = folder.name + " · " + phaseLabel(state),
            iconUrl = resolved.iconUrl,
            onBack = onBack,
            live = state.agentBusy,
            stickyUserMessagesEnabled = stickyUserMessagesEnabled,
            onStickyUserMessagesEnabledChange = { enabled ->
                stickyUserMessagesEnabled = enabled
                com.termfold.app.acp.AcpChatPrefs.setStickyUserMessages(context, enabled)
            },
            previewDir = previewProjectDirOverride ?: guestDir,
            onOpenGitChanges = if (isGitWorkspace) {
                { com.termfold.app.gitworkspace.GitChanges.open(guestDir) }
            } else {
                null
            },
            filesOpen = filesOpen,
            onToggleFiles = onToggleFiles,
            onReviewDone = onReviewDone,
            onReviewAnother = onReviewAnother,
            onHistory = if (state.canResumeSessions && state.phase == AcpPhase.READY) {
                { showHistory = true }
            } else {
                null
            },
            onRestart = if (state.phase == AcpPhase.READY || state.phase == AcpPhase.ERROR) {
                { client.restart() }
            } else {
                null
            },
            onMcp = if (isCursor && state.phase == AcpPhase.READY) {
                { showMcp = true }
            } else {
                null
            },
            onAutomationSessionMemory = if (automationSessionKey != null) {
                { showAutomationSessionMemory = true }
            } else {
                null
            },
            onAutomationNavMemory = if (automationSessionKey != null) {
                { showAutomationNavMemory = true }
            } else {
                null
            },
            auxTerminalTabs = if (isCursor) auxTerminalTabs.toList() else emptyList(),
            onOpenTerminal = if (isCursor) {
                { tabId ->
                    auxTerminalInitialTabId = tabId
                    showAuxTerminals = true
                }
            } else {
                null
            },
            onRequestCloseAuxTerminalTab = if (isCursor) {
                { tabId -> pendingAuxTabCloseId = tabId }
            } else {
                null
            },
            onQuickOpen = { showQuickOpen = true },
        )

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            val hasConversation = state.items.isNotEmpty() || state.restoring
            when {
                // Once there is a conversation it stays on screen; a restart or failure shows as a
                // slim banner above it rather than replacing what the user was reading.
                hasConversation -> Column(Modifier.widthIn(max = ContentMaxWidth).fillMaxSize()) {
                    PhaseBanner(
                        state,
                        onRetry = { client.start() },
                        onReinstallCursor = if (isCursor) ({ client.reinstallCursor() }) else null,
                    )
                    Timeline(
                        state = state,
                        workspaceDir = guestDir,
                        client = client,
                        onEditMessage = { editingMessage = it },
                        stickyUserMessagesEnabled = stickyUserMessagesEnabled,
                        modifier = Modifier.weight(1f),
                    )
                }

                state.phase == AcpPhase.INSTALLING || state.phase == AcpPhase.CONNECTING ->
                    CenterPanel {
                        AgentIconImage(iconUrl = resolved.iconUrl, name = resolved.name, size = 52.dp, cornerRadius = 16.dp)
                        Spacer(Modifier.height(18.dp))
                        PanelTitle(stringResource(R.string.acp_setting_up, resolved.name))
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp, color = Palette.Accent)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = state.statusText.ifBlank { stringResource(R.string.acp_status_connecting) },
                                style = MaterialTheme.typography.bodySmall,
                                color = Palette.TextDim,
                            )
                        }
                        Spacer(Modifier.height(18.dp))
                        PanelBody(stringResource(R.string.acp_first_run_note))
                    }

                state.phase == AcpPhase.AUTH_REQUIRED -> CenterPanel {
                    AgentIconImage(iconUrl = resolved.iconUrl, name = resolved.name, size = 52.dp, cornerRadius = 16.dp)
                    Spacer(Modifier.height(18.dp))
                    PanelTitle(stringResource(R.string.acp_auth_required))
                    Spacer(Modifier.height(8.dp))
                    PanelBody(
                        stringResource(
                            when {
                                isCursor && CursorAcpPrefs.hasApiKey(context) ->
                                    R.string.acp_auth_body_cursor_api_key
                                isCursor -> R.string.acp_auth_body_cursor
                                else -> R.string.acp_auth_body
                            },
                        ),
                    )
                    if (state.authMethods.isNotEmpty() && !(isCursor && CursorAcpPrefs.hasApiKey(context))) {
                        Spacer(Modifier.height(10.dp))
                        PanelBody(stringResource(R.string.acp_auth_methods, state.authMethods.joinToString(", ")))
                    }
                    Spacer(Modifier.height(18.dp))
                    PillButton(stringResource(R.string.action_retry), TermFoldIcons.Refresh) { client.start() }
                }

                state.phase == AcpPhase.ERROR -> CenterPanel {
                    PanelTitle(stringResource(R.string.acp_error_title), color = Palette.Pink)
                    Spacer(Modifier.height(10.dp))
                    SelectionContainer {
                        Text(
                            text = state.errorDetail,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = Mono, fontSize = 12.sp),
                            color = Palette.TextDim,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Palette.Card)
                                .padding(12.dp)
                                .heightIn(max = 180.dp)
                                .verticalScroll(rememberScrollState()),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    PillButton(stringResource(R.string.action_retry), TermFoldIcons.Refresh) { client.start() }
                    if (isCursor) {
                        Spacer(Modifier.height(10.dp))
                        val cursorBusy = state.phase == AcpPhase.INSTALLING || state.phase == AcpPhase.CONNECTING
                        PillButton(
                            stringResource(R.string.acp_reinstall_cursor),
                            TermFoldIcons.Refresh,
                            enabled = !cursorBusy,
                        ) {
                            client.reinstallCursor()
                        }
                    }
                }

                else -> CenterPanel {
                    AgentIconImage(iconUrl = resolved.iconUrl, name = resolved.name, size = 52.dp, cornerRadius = 16.dp)
                    Spacer(Modifier.height(18.dp))
                    PanelTitle(stringResource(R.string.acp_ready_title, resolved.name))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = guestDir,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = Mono, fontSize = 12.sp),
                        color = Palette.TextFaint,
                    )
                }
            }
        }

        if (state.phase == AcpPhase.READY) {
            Composer(
                draftKey = key,
                guestWorkspace = guestDir,
                agentId = resolved.id,
                agentName = resolved.name,
                agentBusy = state.agentBusy || state.checkpoints.wireResyncing || editFileChoice,
                commands = state.commands,
                canResume = state.canResumeSessions,
                onResume = { showHistory = true },
                onNewSession = { client.startNewSession() },
                settings = state.settings,
                onChangeSetting = { setting, value -> client.changeSetting(setting, value) },
                onSend = { text, images, files -> client.send(text, images, files) },
                onQueue = { text, images, files -> client.enqueue(text, images, files) },
                queued = state.queued,
                onSteer = { client.steer(it) },
                onRemoveQueued = { client.removeQueued(it) },
                onCancel = { client.cancel() },
                branchName = if (isCursor && !hideBranchPicker) branchLabel else null,
                branchUncommitted = isCursor && !hideBranchPicker && isGitWorkspace && branchDirty,
                onBranchClick = if (isCursor && !hideBranchPicker) { { showBranchPicker = true } } else null,
                editMessage = editingMessage,
                onCancelEdit = { editingMessage = null },
                onSubmitEdit = { text, images, files ->
                    val target = editingMessage ?: return@Composer
                    pendingEdit = Triple(target.id, text, images to files)
                    editFileChoice = true
                },
                automationComposerCommands = automationPromptPrefix != null,
            )
        }
    }

    pendingEdit?.let { (targetId, _, _) ->
        if (editFileChoice) {
            AlertDialog(
                onDismissRequest = {
                    editFileChoice = false
                    pendingEdit = null
                },
                containerColor = Palette.Card,
                shape = RoundedCornerShape(20.dp),
                title = {
                    Text(
                        stringResource(R.string.acp_checkpoint_edit_files_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = Palette.Text,
                    )
                },
                text = {
                    Text(
                        stringResource(R.string.acp_checkpoint_edit_files_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.TextDim,
                    )
                },
                confirmButton = {
                    Text(
                        stringResource(R.string.acp_checkpoint_revert_files),
                        style = MaterialTheme.typography.labelLarge,
                        color = Palette.Accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                val (id, text, attachments) = pendingEdit ?: return@clickable
                                client.editAndResend(id, text, attachments.first, attachments.second, revertFiles = true)
                                editingMessage = null
                                editFileChoice = false
                                pendingEdit = null
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                },
                dismissButton = {
                    Text(
                        stringResource(R.string.acp_checkpoint_keep_files),
                        style = MaterialTheme.typography.labelLarge,
                        color = Palette.TextDim,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                val (id, text, attachments) = pendingEdit ?: return@clickable
                                client.editAndResend(id, text, attachments.first, attachments.second, revertFiles = false)
                                editingMessage = null
                                editFileChoice = false
                                pendingEdit = null
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                },
            )
        }
    }

    if (showBranchPicker) {
        BranchPickerDialog(
            guestPath = guestDir,
            goldenProjectId = folder.id,
            workspaceId = workspaceId,
            branchCatalogGuestPath = if (workspaceId == com.termfold.app.gitworkspace.WorkspaceIds.GOLDEN) {
                null
            } else {
                com.termfold.app.gitworkspace.WorkspaceStore.goldenGuestPath(context, folder)
            },
            onDismiss = { showBranchPicker = false },
            onSwitched = { branch ->
                branchLabel = branch
                scope.launch {
                    syncWorkspaceGit(
                context,
                guestDir,
                onBranch = { branchLabel = it },
                onDirty = { branchDirty = it },
            )
                }
            },
        )
    }

    if (showHistory) {
        PastSessionsDialog(client = client, currentId = client.listedSessionId, onDismiss = { showHistory = false })
    }

    if (showAutomationSessionMemory && automationSessionKey != null) {
        AutomationMemoryManagerDialog(
            mode = AutomationMemoryManagerMode.SessionPrivate(automationSessionKey),
            onDismiss = { showAutomationSessionMemory = false },
        )
    }
    if (showAutomationNavMemory) {
        AutomationMemoryManagerDialog(
            mode = AutomationMemoryManagerMode.GlobalNav,
            onDismiss = { showAutomationNavMemory = false },
        )
    }

    if (showMcp) {
        McpServersDialog(
            workspaceGuestDir = guestDir,
            cursorAgent = isCursor,
            onReloadSession = { client.reloadMcpServers() },
            onNewSession = { client.startNewSession() },
            onDismiss = { showMcp = false },
        )
    }

    if (showQuickOpen) {
        WorkspaceQuickOpenDialog(
            guestWorkspace = guestDir,
            onDismiss = { showQuickOpen = false },
            onOpenFile = openWorkspaceFile,
        )
    }

    pendingAuxTabCloseId?.let { tabId ->
        AuxTerminalCloseConfirmDialog(
            onDismiss = { pendingAuxTabCloseId = null },
            onConfirm = {
                AuxTerminalTabsStore.closeTab(folder.id, workspaceId, sessionId, tabId)
                pendingAuxTabCloseId = null
            },
        )
    }

    if (showAuxTerminals) {
        AgentAuxTerminalDialog(
            folderId = folder.id,
            workspaceId = workspaceId,
            acpSessionId = sessionId,
            guestWorkspace = guestDir,
            folderLabel = folder.name,
            initialTabId = auxTerminalInitialTabId,
            onDismiss = {
                showAuxTerminals = false
                auxTerminalInitialTabId = null
            },
        )
    }

    state.pendingPermission?.let { pending ->
        PermissionDialog(
            pending = pending,
            toolCall = state.items.lastOrNull {
                it is AcpItem.ToolCall && it.toolCallId == pending.toolCallId
            } as? AcpItem.ToolCall,
            onPick = { optionId -> client.respondPermission(pending.requestId, optionId) },
            onDeny = { client.respondPermission(pending.requestId, null) },
        )
    }
}

@Composable
private fun phaseLabel(state: AcpUiState): String = when {
    state.agentBusy -> stringResource(R.string.acp_working)
    state.phase == AcpPhase.READY -> stringResource(R.string.acp_phase_ready)
    state.phase == AcpPhase.AUTH_REQUIRED -> stringResource(R.string.acp_phase_signin)
    state.phase == AcpPhase.ERROR -> stringResource(R.string.acp_phase_stopped)
    state.phase == AcpPhase.CLOSED -> stringResource(R.string.acp_phase_stopped)
    else -> stringResource(R.string.acp_phase_starting)
}

@Composable
private fun SessionHeader(
    title: String,
    subtitle: String,
    iconUrl: String,
    onBack: () -> Unit,
    live: Boolean = false,
    filesOpen: Boolean = false,
    onToggleFiles: (() -> Unit)? = null,
    onReviewDone: (() -> Unit)? = null,
    onReviewAnother: (() -> Unit)? = null,
    onHistory: (() -> Unit)? = null,
    onRestart: (() -> Unit)? = null,
    onMcp: (() -> Unit)? = null,
    onAutomationSessionMemory: (() -> Unit)? = null,
    onAutomationNavMemory: (() -> Unit)? = null,
    onOpenTerminal: ((tabId: String?) -> Unit)? = null,
    auxTerminalTabs: List<AuxTerminalTab> = emptyList(),
    onRequestCloseAuxTerminalTab: ((tabId: String) -> Unit)? = null,
    onQuickOpen: (() -> Unit)? = null,
    previewDir: String? = null,
    onOpenGitChanges: (() -> Unit)? = null,
    stickyUserMessagesEnabled: Boolean = true,
    onStickyUserMessagesEnabledChange: (Boolean) -> Unit = {},
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BareIconButton(
                icon = TermFoldIcons.Back,
                contentDescription = stringResource(R.string.cd_back),
                onClick = onBack,
            )
            Spacer(Modifier.width(4.dp))
            Box {
                AgentIconImage(iconUrl = iconUrl, name = title, size = 32.dp, cornerRadius = 9.dp)
                if (live) {
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Palette.Bg)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(Palette.Accent),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Palette.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (onQuickOpen != null) {
                BareIconButton(
                    icon = TermFoldIcons.Search,
                    contentDescription = stringResource(R.string.cd_go_to_file),
                    onClick = onQuickOpen,
                    tint = Palette.TextDim,
                    size = 40,
                )
            }
            if (onToggleFiles != null) {
                FilesButton(open = filesOpen, onClick = onToggleFiles)
            }
            if (onReviewAnother != null) {
                TextButton(onClick = onReviewAnother) {
                    Text(stringResource(R.string.pr_review_paste_another), color = Palette.TextDim)
                }
            }
            if (onReviewDone != null) {
                TextButton(onClick = onReviewDone) {
                    Text(stringResource(R.string.pr_review_done), color = Palette.Accent)
                }
            }
            SessionHeaderOverflowMenu(
                previewDir = previewDir,
                onOpenGitChanges = onOpenGitChanges,
                onOpenTerminal = onOpenTerminal,
                auxTerminalTabs = auxTerminalTabs,
                onRequestCloseAuxTerminalTab = onRequestCloseAuxTerminalTab,
                onHistory = onHistory,
                onMcp = onMcp,
                onRestart = onRestart,
                onAutomationSessionMemory = onAutomationSessionMemory,
                onAutomationNavMemory = onAutomationNavMemory,
                stickyUserMessagesEnabled = stickyUserMessagesEnabled,
                onStickyUserMessagesEnabledChange = onStickyUserMessagesEnabledChange,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Palette.BorderSoft),
        )
    }
}

@Composable
private fun SessionHeaderOverflowMenu(
    previewDir: String?,
    onOpenGitChanges: (() -> Unit)?,
    onOpenTerminal: ((tabId: String?) -> Unit)?,
    auxTerminalTabs: List<AuxTerminalTab>,
    onRequestCloseAuxTerminalTab: ((tabId: String) -> Unit)?,
    onHistory: (() -> Unit)?,
    onMcp: (() -> Unit)?,
    onRestart: (() -> Unit)?,
    onAutomationSessionMemory: (() -> Unit)?,
    onAutomationNavMemory: (() -> Unit)?,
    stickyUserMessagesEnabled: Boolean = true,
    onStickyUserMessagesEnabledChange: (Boolean) -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }
    val previewState by Preview.state.collectAsStateWithLifecycle()
    Box {
        BareIconButton(
            icon = TermFoldIcons.More,
            contentDescription = stringResource(R.string.cd_more),
            onClick = { expanded = true },
            tint = Palette.TextDim,
            size = 40,
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = Palette.Card,
            shape = RoundedCornerShape(16.dp),
        ) {
            DropdownMenuItem(
                text = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            stringResource(R.string.acp_sticky_user_message),
                            color = Palette.Text,
                            modifier = Modifier.weight(1f).padding(end = 12.dp),
                        )
                        androidx.compose.material3.Switch(
                            checked = stickyUserMessagesEnabled,
                            onCheckedChange = null,
                        )
                    }
                },
                onClick = {
                    onStickyUserMessagesEnabledChange(!stickyUserMessagesEnabled)
                },
            )
            if (onOpenTerminal != null) {
                val canCloseTabs = auxTerminalTabs.size > 1
                if (auxTerminalTabs.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.agent_aux_terminal), color = Palette.Text) },
                        leadingIcon = {
                            Icon(
                                TermFoldIcons.Terminal,
                                contentDescription = null,
                                tint = Palette.TextDim,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        onClick = {
                            expanded = false
                            onOpenTerminal(null)
                        },
                    )
                } else {
                    auxTerminalTabs.forEach { tab ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        stringResource(R.string.agent_aux_terminal_tab, tab.title),
                                        color = Palette.Text,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (canCloseTabs && onRequestCloseAuxTerminalTab != null) {
                                        BareIconButton(
                                            icon = TermFoldIcons.Close,
                                            contentDescription = stringResource(R.string.agent_aux_terminal_close_tab),
                                            onClick = {
                                                expanded = false
                                                onRequestCloseAuxTerminalTab(tab.id)
                                            },
                                            tint = Palette.TextFaint,
                                            size = 32,
                                        )
                                    }
                                }
                            },
                            leadingIcon = {
                                Icon(
                                    TermFoldIcons.Terminal,
                                    contentDescription = null,
                                    tint = Palette.TextDim,
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                            onClick = {
                                expanded = false
                                onOpenTerminal(tab.id)
                            },
                        )
                    }
                }
            }
            if (previewDir != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.preview_title), color = Palette.Text) },
                    leadingIcon = {
                        Icon(
                            TermFoldIcons.Globe,
                            contentDescription = null,
                            tint = Palette.TextDim,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    onClick = {
                        expanded = false
                        when {
                            previewState.open && !previewState.minimized -> Preview.close()
                            previewState.open -> Preview.restore()
                            else -> Preview.open(projectDir = previewDir)
                        }
                    },
                )
            }
            if (onOpenGitChanges != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.git_changes_title), color = Palette.Text) },
                    leadingIcon = {
                        Icon(
                            TermFoldIcons.GitBranch,
                            contentDescription = null,
                            tint = Palette.TextDim,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    onClick = {
                        expanded = false
                        onOpenGitChanges()
                    },
                )
            }
            if (onAutomationSessionMemory != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.automation_memory_menu_session), color = Palette.Text) },
                    leadingIcon = {
                        Icon(
                            TermFoldIcons.Sparkle,
                            contentDescription = null,
                            tint = Palette.TextDim,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    onClick = {
                        expanded = false
                        onAutomationSessionMemory()
                    },
                )
            }
            if (onAutomationNavMemory != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.automation_memory_menu_global), color = Palette.Text) },
                    leadingIcon = {
                        Icon(
                            TermFoldIcons.Globe,
                            contentDescription = null,
                            tint = Palette.TextDim,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    onClick = {
                        expanded = false
                        onAutomationNavMemory()
                    },
                )
            }
            if (onHistory != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.acp_history), color = Palette.Text) },
                    leadingIcon = {
                        Icon(
                            TermFoldIcons.History,
                            contentDescription = null,
                            tint = Palette.TextDim,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    onClick = {
                        expanded = false
                        onHistory()
                    },
                )
            }
            if (onMcp != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.mcp_servers), color = Palette.Text) },
                    leadingIcon = {
                        Icon(
                            TermFoldIcons.Gear,
                            contentDescription = null,
                            tint = Palette.TextDim,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    onClick = {
                        expanded = false
                        onMcp()
                    },
                )
            }
            if (onRestart != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.acp_restart), color = Palette.Text) },
                    leadingIcon = {
                        Icon(
                            TermFoldIcons.Refresh,
                            contentDescription = null,
                            tint = Palette.TextDim,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    onClick = {
                        expanded = false
                        onRestart()
                    },
                )
            }
        }
    }
}

/** Shown above an existing conversation while the agent restarts or after it fails. */
@Composable
private fun PhaseBanner(
    state: AcpUiState,
    onRetry: () -> Unit,
    onReinstallCursor: (() -> Unit)? = null,
) {
    val text: String
    val loading: Boolean
    when (state.phase) {
        AcpPhase.INSTALLING, AcpPhase.CONNECTING -> {
            text = state.statusText.ifBlank { stringResource(R.string.acp_status_connecting) }
            loading = true
        }
        AcpPhase.ERROR -> {
            text = state.errorDetail.ifBlank { stringResource(R.string.acp_error_title) }
            loading = false
        }
        AcpPhase.AUTH_REQUIRED -> {
            text = stringResource(R.string.acp_auth_required)
            loading = false
        }
        else -> if (state.restoring) {
            text = stringResource(R.string.acp_restoring)
            loading = true
        } else {
            return
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Palette.Card)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp, color = Palette.Accent)
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = if (state.phase == AcpPhase.ERROR) Palette.Pink else Palette.TextDim,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (!loading) {
            if (onReinstallCursor != null && state.phase == AcpPhase.ERROR) {
                Text(
                    text = stringResource(R.string.acp_reinstall_cursor),
                    style = MaterialTheme.typography.labelLarge,
                    color = Palette.TextDim,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onReinstallCursor)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = stringResource(R.string.action_retry),
                style = MaterialTheme.typography.labelLarge,
                color = Palette.Accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun CenterPanel(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { content() }
    }
}

@Composable
private fun PanelTitle(text: String, color: Color = Palette.Text) {
    Text(text = text, style = MaterialTheme.typography.titleMedium, color = color)
}

@Composable
private fun PanelBody(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = Palette.TextDim,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

@Composable
private fun PillButton(
    label: String,
    icon: ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val contentColor = if (enabled) Palette.Text else Palette.TextDim
    val accent = if (enabled) Palette.Accent else Palette.TextDim
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, Palette.Border, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = contentColor)
    }
}

// --- Timeline ---------------------------------------------------------------------------------

/** Pulls tool file chrome toward the row above (list spacing is 12.dp). */
private val AgentChatToolFilePullUp = (-6).dp

/** Cancels [LazyColumn] spacedBy gap between a sticky file header and its diff body. */
private val AgentChatStickyDiffBodyPullUp = (-12).dp

/** Pinned file header while its diff body is on screen but the in-list header has scrolled away. */
private fun pinnedToolFileSection(
    listState: androidx.compose.foundation.lazy.LazyListState,
    entries: List<AgentChatLazyEntry>,
): AgentChatLazyEntry.ToolFileSection? {
    val visible = listState.layoutInfo.visibleItemsInfo
    if (visible.isEmpty()) return null
    val linesInfo = visible
        .filter { (it.key as? String)?.endsWith("-lines") == true }
        .minByOrNull { it.index }
        ?: return null
    val sectionKey = (linesInfo.key as String).removeSuffix("-lines")
    val headerVisible = visible.any { it.key == sectionKey }
    if (headerVisible) {
        val header = visible.first { it.key == sectionKey }
        if (header.offset >= -header.size / 4) return null
    }
    return entries.filterIsInstance<AgentChatLazyEntry.ToolFileSection>().find { it.key == sectionKey }
}

/**
 * User message that should stick at the top for the current scroll position: the latest user
 * prompt at or above the first visible row (each turn while scrolling up or down).
 */
private fun stickyUserMessageForViewport(
    listState: androidx.compose.foundation.lazy.LazyListState,
    lazyEntries: List<AgentChatLazyEntry>,
): AcpItem.UserMessage? {
    val userSlots = lazyEntries.mapIndexedNotNull { index, entry ->
        if (entry is AgentChatLazyEntry.Standard && entry.item is AcpItem.UserMessage) {
            index to entry.item
        } else {
            null
        }
    }
    if (userSlots.isEmpty()) return null
    val visible = listState.layoutInfo.visibleItemsInfo
    if (visible.isEmpty()) return null
    val firstVisibleIndex = visible.minOf { it.index }
    val message = userSlots.lastOrNull { it.first <= firstVisibleIndex }?.second ?: return null
    val entryKey = "item-${message.id}"
    val inList = visible.find { it.key == entryKey }
    if (inList != null && inList.offset >= -4) {
        return null
    }
    return message
}

private suspend fun androidx.compose.foundation.lazy.LazyListState.scrollAgentChatToBottom(animate: Boolean) {
    val count = layoutInfo.totalItemsCount
    if (count > 0) {
        if (animate) animateScrollToItem(count - 1) else scrollToItem(count - 1)
        scrollBy(100_000f)
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun Timeline(
    state: AcpUiState,
    workspaceDir: String,
    client: AcpClient,
    onEditMessage: (AcpItem.UserMessage) -> Unit,
    stickyUserMessagesEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val items = state.items
    val ghostFrom = state.checkpoints.ghostFromItemId
    val checkpointBusy = state.checkpoints.busy
    var undoConfirmId by remember { mutableStateOf<Long?>(null) }
    val listState = rememberLazyListState()
    val toolDiffPresentationCache = remember { androidx.compose.runtime.mutableStateMapOf<String, com.termfold.app.acp.ToolCallFilePresentation>() }
    val toolDiffPresentationLoading = remember { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }
    val toolCalls = remember(items) { items.filterIsInstance<AcpItem.ToolCall>() }
    ToolCallDiffCacheCoordinator(
        toolCalls = toolCalls,
        cache = toolDiffPresentationCache,
        loading = toolDiffPresentationLoading,
    )
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    var showLiveToolDiffs by remember {
        mutableStateOf(com.termfold.app.acp.AcpChatPrefs.showLiveToolDiffs(context))
    }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                showLiveToolDiffs = com.termfold.app.acp.AcpChatPrefs.showLiveToolDiffs(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val openWorkspaceFile = LocalWorkspaceFileOpener.current
    val onOpenPath: (String) -> Unit = { path ->
        WorkspacePathResolver.resolve(context, path, workspaceDir)?.let { openWorkspaceFile(it) }
    }

    var previewImage by remember { mutableStateOf<AcpBlock.Image?>(null) }

    var collapsedTools by remember { mutableStateOf(mapOf<Long, Boolean>()) }
    val userToggledToolIds = remember { mutableStateSetOf<Long>() }
    var expandedToolFiles by remember { mutableStateOf(mapOf<String, Boolean>()) }
    val toolContextFolds = remember { mutableStateMapOf<String, Set<String>>() }
    LaunchedEffect(items, showLiveToolDiffs) {
        val next = collapsedTools.toMutableMap()
        syncCompletedToolCollapse(items, next, showLiveToolDiffs, userToggledToolIds)
        if (next != collapsedTools) collapsedTools = next
    }
    // Snapshot maps here so collapse/expand toggles always rebuild lazy rows.
    val collapsedSnapshot = collapsedTools
    val expandedFilesSnapshot = expandedToolFiles
    val presentationSnapshot = toolDiffPresentationCache.toMap()
    val toolContextSnapshot = toolContextFolds.toMap()
    val lazyEntries = buildAgentChatTimelineEntries(
        items = items,
        collapsedTools = collapsedSnapshot,
        expandedToolFiles = expandedFilesSnapshot,
        contextFolds = toolContextSnapshot,
        workspaceDir = workspaceDir,
        showLiveToolDiffs = showLiveToolDiffs,
        presentationCache = presentationSnapshot,
    )

    // Follow the conversation while the user is at the bottom; debounce scroll-to-bottom so
    // streaming diffs do not fight the UI thread on every layout tick.
    var following by remember { mutableStateOf(true) }
    var userDragging by remember { mutableStateOf(false) }
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is androidx.compose.foundation.interaction.DragInteraction.Start -> {
                    userDragging = true
                    following = false
                }
                is androidx.compose.foundation.interaction.DragInteraction.Stop,
                is androidx.compose.foundation.interaction.DragInteraction.Cancel -> userDragging = false
            }
        }
    }
    LaunchedEffect(listState) {
        androidx.compose.runtime.snapshotFlow { listState.canScrollForward }
            .distinctUntilChanged()
            .collect { canScrollForward ->
                if (!canScrollForward && !userDragging) following = true
            }
    }
    LaunchedEffect(lazyEntries.size, following) {
        if (!following) return@LaunchedEffect
        runCatching { listState.scrollAgentChatToBottom(animate = false) }
    }
    LaunchedEffect(listState, following) {
        if (!following) return@LaunchedEffect
        androidx.compose.runtime.snapshotFlow {
            listState.layoutInfo.totalItemsCount
        }
            .debounce(80)
            .distinctUntilChanged()
            .collect {
                if (following && !listState.isScrollInProgress) {
                    runCatching { listState.scrollAgentChatToBottom(animate = false) }
                }
            }
    }

    val diffTextStyle = agentChatDiffLineTextStyle()
    val pinnedToolFile by remember(lazyEntries) {
        derivedStateOf {
            pinnedToolFileSection(listState, lazyEntries)
        }
    }
    val lastUserId = items.filterIsInstance<AcpItem.UserMessage>().lastOrNull()?.id
    val stickyUserMessage by remember(lazyEntries, stickyUserMessagesEnabled) {
        derivedStateOf {
            if (!stickyUserMessagesEnabled) return@derivedStateOf null
            stickyUserMessageForViewport(listState, lazyEntries)
        }
    }
    Box(modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.checkpoints.wireResyncing && !state.agentBusy) {
            item(key = "wire-resync") {
                Text(
                    stringResource(R.string.acp_checkpoint_resync),
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextFaint,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
        val lastTimelineItemId = items.lastOrNull()?.id
        lazyEntries.forEach { entry ->
            fun dimModifier(itemId: Long): Modifier =
                if (ghostFrom != null && itemId >= ghostFrom) Modifier.alpha(0.7f) else Modifier
            when (entry) {
                is AgentChatLazyEntry.Standard -> item(key = entry.key) {
                    val item = entry.item
                    val isLast = item.id == lastTimelineItemId
                    Box(dimModifier(item.id)) {
                        when (item) {
                            is AcpItem.UserMessage -> {
                                val showRedo = ghostFrom != null &&
                                    item.id == ghostFrom &&
                                    state.checkpoints.canRedo &&
                                    !state.checkpoints.wireResyncing
                                val canUndo = state.checkpoints.undoableMessageIds.contains(item.id) &&
                                    !(state.agentBusy && item.id == lastUserId) &&
                                    (ghostFrom == null || item.id < ghostFrom) &&
                                    !showRedo
                                val undoBusy = checkpointBusy is CheckpointBusy.Undo && checkpointBusy.messageId == item.id
                                val redoBusy = checkpointBusy is CheckpointBusy.Redo && checkpointBusy.messageId == item.id
                                val actionsLocked = checkpointBusy != null && !undoBusy && !redoBusy
                                val actionsEnabled = !actionsLocked || undoBusy || redoBusy
                                val showEdit = !(state.agentBusy && item.id == lastUserId) &&
                                    (ghostFrom == null || item.id <= ghostFrom)
                                UserMessageRow(
                                    item = item,
                                    showEdit = showEdit,
                                    onEdit = { if (!actionsLocked) onEditMessage(item) },
                                    showUndo = canUndo || undoBusy,
                                    undoLoading = undoBusy,
                                    onUndo = { if (actionsEnabled && !undoBusy) undoConfirmId = item.id },
                                    showRedo = showRedo || redoBusy,
                                    redoLoading = redoBusy,
                                    onRedo = { if (actionsEnabled && !redoBusy) client.redoCheckpoint(item.id) },
                                    actionsEnabled = !actionsLocked || undoBusy || redoBusy,
                                    onImageClick = { previewImage = it },
                                )
                            }
                            is AcpItem.AgentText -> Markdown(item.text, Modifier.fillMaxWidth(), onOpenPath = onOpenPath)
                            is AcpItem.Thought -> ThoughtRow(item, live = isLast && state.agentBusy, onOpenPath = onOpenPath)
                            is AcpItem.Plan -> PlanCard(item)
                            is AcpItem.Notice -> NoticeRow(item.text)
                            is AcpItem.ToolCall -> Unit
                        }
                    }
                }
                is AgentChatLazyEntry.ToolHeader -> item(key = entry.key) {
                    val item = entry.item
                    val collapsed = collapsedSnapshot[item.id] == true
                    Box(dimModifier(item.id)) {
                        ToolCallHeaderRow(
                            item = item,
                            workspaceDir = workspaceDir,
                            collapsed = collapsed,
                            onToggle = {
                                userToggledToolIds.add(item.id)
                                collapsedTools = collapsedTools.toMutableMap().apply {
                                    this[item.id] = !collapsed
                                }
                            },
                        )
                    }
                }
                is AgentChatLazyEntry.ToolFileSection -> {
                    val fileUiKey = "${entry.item.id}:${entry.fileIndex}"
                    val onFileHeaderClick = {
                        expandedToolFiles = expandedToolFiles.toMutableMap().apply {
                            this[fileUiKey] = !entry.fileExpanded
                        }
                    }
                    val headerModifier = dimModifier(entry.item.id)
                        .fillMaxWidth()
                        .background(Palette.Bg)
                        .offset(y = AgentChatToolFilePullUp)
                    val body = entry.body
                    val splitDiff = entry.fileExpanded &&
                        body is AgentChatLazyEntry.ToolFileDiffBody.Lines &&
                        body.rows.isNotEmpty()
                    if (splitDiff) {
                        item(key = entry.key) {
                            ToolCallFileDiffHeader(
                                path = entry.relPath,
                                operationLabel = toolDiffOperationLabel(entry.diff.operation),
                                added = entry.added,
                                removed = entry.removed,
                                expanded = entry.fileExpanded,
                                onClick = onFileHeaderClick,
                                modifier = headerModifier,
                            )
                        }
                        item(key = "${entry.key}-lines") {
                            Column(
                                modifier = dimModifier(entry.item.id)
                                    .fillMaxWidth()
                                    .offset(y = AgentChatStickyDiffBodyPullUp),
                                verticalArrangement = Arrangement.spacedBy(0.dp),
                            ) {
                                AgentChatToolFileDiffLines(
                                    lines = body as AgentChatLazyEntry.ToolFileDiffBody.Lines,
                                    textStyle = diffTextStyle,
                                    toolContextFolds = toolContextFolds,
                                )
                            }
                        }
                    } else {
                        item(key = entry.key) {
                            Column(
                                modifier = headerModifier,
                                verticalArrangement = Arrangement.spacedBy(0.dp),
                            ) {
                                ToolCallFileDiffHeader(
                                    path = entry.relPath,
                                    operationLabel = toolDiffOperationLabel(entry.diff.operation),
                                    added = entry.added,
                                    removed = entry.removed,
                                    expanded = entry.fileExpanded,
                                    onClick = onFileHeaderClick,
                                )
                                AgentChatToolFileDiffBody(
                                    body = body,
                                    textStyle = diffTextStyle,
                                    toolContextFolds = toolContextFolds,
                                )
                            }
                        }
                    }
                }
                is AgentChatLazyEntry.ToolDetail -> item(key = entry.key) {
                    Box(
                        dimModifier(entry.item.id).offset(y = (-4).dp),
                    ) {
                        ToolCallDetailBlock(entry.item)
                    }
                }
            }
        }

        if (state.agentBusy) {
            // Keyed by the turn, so each message the agent takes on starts its own timer.
            val turn = items.count { it is AcpItem.UserMessage }
            item(key = "working-$turn") { WorkingRow() }
        }
    }

        pinnedToolFile?.let { section ->
            val fileUiKey = "${section.item.id}:${section.fileIndex}"
            ToolCallFileDiffHeader(
                path = section.relPath,
                operationLabel = toolDiffOperationLabel(section.diff.operation),
                added = section.added,
                removed = section.removed,
                expanded = section.fileExpanded,
                onClick = {
                    expandedToolFiles = expandedToolFiles.toMutableMap().apply {
                        this[fileUiKey] = !section.fileExpanded
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(Palette.Bg)
                    .padding(start = 16.dp, end = 16.dp, top = 14.dp),
            )
        }

        stickyUserMessage?.let { pinnedUser ->
            if (pinnedToolFile == null) {
            val showRedo = ghostFrom != null &&
                pinnedUser.id == ghostFrom &&
                state.checkpoints.canRedo &&
                !state.checkpoints.wireResyncing
            val canUndo = state.checkpoints.undoableMessageIds.contains(pinnedUser.id) &&
                !(state.agentBusy && pinnedUser.id == lastUserId) &&
                (ghostFrom == null || pinnedUser.id < ghostFrom) &&
                !showRedo
            val undoBusy = checkpointBusy is CheckpointBusy.Undo && checkpointBusy.messageId == pinnedUser.id
            val redoBusy = checkpointBusy is CheckpointBusy.Redo && checkpointBusy.messageId == pinnedUser.id
            val actionsLocked = checkpointBusy != null && !undoBusy && !redoBusy
            val actionsEnabled = !actionsLocked || undoBusy || redoBusy
            val showEdit = !(state.agentBusy && pinnedUser.id == lastUserId) &&
                (ghostFrom == null || pinnedUser.id <= ghostFrom)
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(Palette.Bg)
                    .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 168.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    UserMessageRow(
                        item = pinnedUser,
                        showEdit = showEdit,
                        onEdit = { onEditMessage(pinnedUser) },
                        showUndo = canUndo || undoBusy,
                        undoLoading = undoBusy,
                        onUndo = { undoConfirmId = pinnedUser.id },
                        showRedo = showRedo || redoBusy,
                        redoLoading = redoBusy,
                        onRedo = { client.redoCheckpoint(pinnedUser.id) },
                        actionsEnabled = !actionsLocked || undoBusy || redoBusy,
                        onImageClick = { previewImage = it },
                    )
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Palette.BorderSoft),
                )
            }
            }
        }

        // Back to the newest message after scrolling up to read.
        androidx.compose.animation.AnimatedVisibility(
            visible = !following && listState.canScrollForward,
            enter = fadeIn(Motion.standard()) + androidx.compose.animation.scaleIn(Motion.standard(), initialScale = 0.8f),
            exit = fadeOut(Motion.quick()) + androidx.compose.animation.scaleOut(Motion.quick(), targetScale = 0.8f),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Palette.CardPressed)
                    .border(1.dp, Palette.Border, RoundedCornerShape(50))
                    .clickable {
                        following = true
                        scope.launch {
                            runCatching { listState.scrollAgentChatToBottom(animate = true) }
                        }
                    }
                    .padding(start = 12.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(TermFoldIcons.ChevronDown, null, tint = Palette.Text, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.acp_jump_latest),
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                    color = Palette.Text,
                )
            }
        }
    }

    previewImage?.let { image ->
        AcpImagePreviewDialog(image = image, onDismiss = { previewImage = null })
    }

    undoConfirmId?.let { messageId ->
        AlertDialog(
            onDismissRequest = { undoConfirmId = null },
            containerColor = Palette.Card,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    stringResource(R.string.acp_checkpoint_undo_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = Palette.Text,
                )
            },
            text = {
                Text(
                    stringResource(R.string.acp_checkpoint_undo_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextDim,
                )
            },
            confirmButton = {
                Text(
                    stringResource(R.string.acp_checkpoint_restore),
                    style = MaterialTheme.typography.labelLarge,
                    color = Palette.Accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            undoConfirmId = null
                            client.restoreCheckpoint(messageId)
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            },
            dismissButton = {
                Text(
                    stringResource(R.string.action_cancel),
                    style = MaterialTheme.typography.labelLarge,
                    color = Palette.TextDim,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { undoConfirmId = null }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            },
        )
    }

}

@Composable
private fun UserMessageRow(
    item: AcpItem.UserMessage,
    showEdit: Boolean,
    onEdit: () -> Unit,
    showUndo: Boolean,
    undoLoading: Boolean,
    onUndo: () -> Unit,
    showRedo: Boolean,
    redoLoading: Boolean,
    onRedo: () -> Unit,
    actionsEnabled: Boolean,
    onImageClick: (AcpBlock.Image) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
    ) {
        if (item.images.isNotEmpty() || item.files.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item.files.forEach { file -> FileChip(file) }
                item.images.forEach { image ->
                    ImageThumb(image, size = 88, onClick = { onImageClick(image) })
                }
            }
            if (item.text.isNotBlank()) Spacer(Modifier.height(6.dp))
        }
        if (item.text.isNotBlank()) {
            SelectionContainer {
                Text(
                    text = item.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Text,
                    modifier = Modifier
                        .widthIn(max = 560.dp)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 5.dp))
                        .background(UserBubble)
                        .border(
                            1.dp,
                            Palette.BorderSoft,
                            RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 5.dp),
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
        if (showEdit || showUndo || showRedo) {
            Spacer(Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showEdit) {
                    MessageActionChip(
                        label = stringResource(R.string.acp_checkpoint_edit),
                        icon = TermFoldIcons.Pencil,
                        contentDescription = stringResource(R.string.acp_checkpoint_edit),
                        enabled = actionsEnabled,
                        loading = false,
                        onClick = onEdit,
                    )
                }
                if (showUndo) {
                    MessageActionChip(
                        label = stringResource(R.string.acp_checkpoint_restore),
                        icon = TermFoldIcons.Undo,
                        contentDescription = stringResource(R.string.acp_checkpoint_restore),
                        enabled = actionsEnabled && !undoLoading,
                        loading = undoLoading,
                        onClick = onUndo,
                    )
                }
                if (showRedo) {
                    MessageActionChip(
                        label = stringResource(R.string.acp_checkpoint_redo),
                        icon = TermFoldIcons.Redo,
                        contentDescription = stringResource(R.string.acp_checkpoint_redo),
                        enabled = actionsEnabled && !redoLoading,
                        loading = redoLoading,
                        onClick = onRedo,
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageActionChip(
    label: String,
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    loading: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (enabled) Palette.TextDim else Palette.TextFaint
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled && !loading, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 1.5.dp,
                color = Palette.TextDim,
            )
        } else {
            Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(16.dp))
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

@Composable
private fun ImageThumb(
    image: AcpBlock.Image,
    size: Int,
    onClick: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
) {
    // Decoded off the main thread, and only as large as the thumbnail: a pasted screenshot is
    // ~10 MB as a full bitmap, which a chat with a few of them cannot afford on a small phone.
    val targetPx = with(androidx.compose.ui.platform.LocalDensity.current) { size.dp.roundToPx() }
    val bitmap by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, image.base64, targetPx) {
        value = withContext(Dispatchers.Default) {
            AcpImageBitmaps.decodeThumbnail(image, targetPx)
        }
    }
    Box(
        Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.Card)
            .border(1.dp, Palette.BorderSoft, RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        bitmap?.let { thumb ->
            Image(
                bitmap = thumb,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (onRemove != null) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color(0xCC08080A))
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(TermFoldIcons.Close, stringResource(R.string.acp_remove), tint = Palette.Text, modifier = Modifier.size(12.dp))
            }
        }
    }
}

@Composable
private fun FileChip(file: AcpBlock.TextFile, onClick: (() -> Unit)? = null, onRemove: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.Card)
            .border(1.dp, Palette.BorderSoft, RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 10.dp, end = if (onRemove != null) 4.dp else 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(TermFoldIcons.FileText, contentDescription = null, tint = Palette.Accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = Mono, fontSize = 12.sp),
                color = Palette.Text,
                maxLines = 1,
            )
            Text(
                text = stringResource(R.string.acp_pasted_file_meta, file.text.lines().size, file.text.length.toShortCount()),
                style = MaterialTheme.typography.labelSmall,
                color = Palette.TextFaint,
            )
        }
        if (onRemove != null) {
            Spacer(Modifier.width(2.dp))
            BareIconButton(
                icon = TermFoldIcons.Close,
                contentDescription = stringResource(R.string.acp_remove),
                onClick = onRemove,
                tint = Palette.TextFaint,
                size = 28,
            )
        }
    }
}

/**
 * What a tool-call row says: the agent's title, plus the file it works on when the title does
 * not already name it ("Read" becomes "Read  src/main.kt"). Paths inside the project are shown
 * relative to it.
 */
private fun toolLabel(item: AcpItem.ToolCall, workspaceDir: String): String {
    val path = item.paths.firstOrNull() ?: return item.title
    val shown = path.removePrefix(workspaceDir.trimEnd('/') + "/").ifBlank { path }
    val name = shown.substringAfterLast('/')
    if (item.title.contains(shown) || item.title.contains(name)) return item.title
    val more = if (item.paths.size > 1) " +${item.paths.size - 1}" else ""
    return "${item.title}  $shown$more"
}

/**
 * One Markdown line as ticker text: block markers (headings, bullets, quotes, fences) are dropped,
 * inline marks are kept for [com.termfold.app.ui.components.inline] to render.
 */
private fun tickerText(line: String): String =
    line.trim()
        // Markers need their trailing space, so the "**" of a bold line is not taken for a bullet.
        .replace(Regex("""^(#{1,6}\s+|[-*+]\s+|\d+[.)]\s+|>\s*|```\S*\s*)"""), "")
        .trim()

/**
 * The agent's reasoning. Shown expanded by default; tap the header to collapse to a one-line preview.
 * While the agent is still thinking, the expanded view follows the stream.
 */
@OptIn(androidx.compose.animation.ExperimentalAnimationApi::class)
@Composable
private fun ThoughtRow(item: AcpItem.Thought, live: Boolean, onOpenPath: (String) -> Unit) {
    var collapsed by remember(item.id) { mutableStateOf(false) }
    val text = item.text.trim()
    val scroll = rememberScrollState()

    LaunchedEffect(collapsed, live, text) {
        if (!collapsed && live) {
            androidx.compose.runtime.snapshotFlow { scroll.maxValue }.collect { scroll.scrollTo(it) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .padding(vertical = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { collapsed = !collapsed },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (live) {
                PulsingText(stringResource(R.string.acp_thinking))
            } else {
                Text(
                    text = stringResource(R.string.acp_thought),
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextFaint,
                )
            }
            if (collapsed) {
                Spacer(Modifier.width(10.dp))
                val preview = text.lines().firstOrNull { it.isNotBlank() }?.let(::tickerText)?.trim() ?: ""
                Text(
                    text = com.termfold.app.ui.components.inline(preview),
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = if (collapsed) TermFoldIcons.ChevronRight else TermFoldIcons.ChevronDown,
                contentDescription = null,
                tint = Palette.TextFaint,
                modifier = Modifier.size(14.dp),
            )
        }
        if (!collapsed) {
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
                Box(Modifier.width(2.dp).fillMaxHeight().background(Palette.Border))
                Spacer(Modifier.width(10.dp))
                Markdown(
                    text = text,
                    color = Palette.TextDim,
                    secondary = Palette.TextFaint,
                    bodyStyle = MaterialTheme.typography.bodySmall.copy(lineHeight = 17.sp),
                    modifier = Modifier.heightIn(max = 360.dp).verticalScroll(scroll),
                    onOpenPath = onOpenPath,
                )
            }
        }
    }
}

@Composable
private fun AgentChatToolFileDiffLines(
    lines: AgentChatLazyEntry.ToolFileDiffBody.Lines,
    textStyle: TextStyle,
    toolContextFolds: androidx.compose.runtime.snapshots.SnapshotStateMap<String, Set<String>>,
) {
    lines.rows.forEach { row ->
        GitDiffDisplayRow(
            row = row,
            textStyle = textStyle,
            compact = true,
            onExpandContextFold = { foldKey ->
                val current = toolContextFolds[lines.foldKeyPrefix] ?: emptySet()
                toolContextFolds[lines.foldKeyPrefix] = current + foldKey
            },
        )
    }
}

@Composable
private fun AgentChatToolFileDiffBody(
    body: AgentChatLazyEntry.ToolFileDiffBody?,
    textStyle: TextStyle,
    toolContextFolds: androidx.compose.runtime.snapshots.SnapshotStateMap<String, Set<String>>,
) {
    when (body) {
        null -> Unit
        AgentChatLazyEntry.ToolFileDiffBody.Loading -> {
            Box(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Palette.Accent)
            }
        }
        AgentChatLazyEntry.ToolFileDiffBody.Binary -> {
            Text(
                stringResource(R.string.acp_tool_diff_binary),
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextFaint,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        is AgentChatLazyEntry.ToolFileDiffBody.Lines -> {
            AgentChatToolFileDiffLines(body, textStyle, toolContextFolds)
        }
    }
}

@Composable
private fun ToolCallHeaderRow(
    item: AcpItem.ToolCall,
    workspaceDir: String,
    collapsed: Boolean,
    onToggle: () -> Unit,
) {
    val label = toolLabel(item, workspaceDir)
    val preview = label.lines().firstOrNull { it.isNotBlank() }?.trim() ?: label.trim()
    val headerText = if (collapsed) preview else label.lineSequence().joinToString(" ").trim()
    val failed = item.status == "failed"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = toolIcon(item.kind),
            contentDescription = null,
            tint = if (failed) Palette.Pink else Palette.TextFaint,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = headerText,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = Mono,
                fontSize = 12.5.sp,
                lineHeight = 17.sp,
            ),
            color = when {
                failed -> Palette.Pink
                collapsed -> Palette.TextFaint
                else -> Palette.TextDim
            },
            maxLines = if (collapsed) 1 else 12,
            overflow = if (collapsed) TextOverflow.Ellipsis else TextOverflow.Clip,
            modifier = Modifier.weight(1f),
        )
        if (item.status != "completed" && item.status != "failed") {
            Spacer(Modifier.width(8.dp))
            CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp, color = Palette.Accent)
        }
        Spacer(Modifier.width(6.dp))
        Icon(
            imageVector = if (collapsed) TermFoldIcons.ChevronRight else TermFoldIcons.ChevronDown,
            contentDescription = null,
            tint = Palette.TextFaint,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun ToolCallDetailBlock(item: AcpItem.ToolCall) {
    val hasFileDiffs = item.fileDiffs.isNotEmpty()
    Spacer(Modifier.height(if (hasFileDiffs) 6.dp else 0.dp))
    SelectionContainer {
        Text(
            text = item.detail.trim(),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = Mono, fontSize = 12.sp, lineHeight = 17.sp),
            color = Palette.TermOut,
            softWrap = false,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Palette.Card)
                .heightIn(max = 200.dp)
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
                .padding(10.dp),
        )
    }
}

/** The agent's plan, replaced in place as steps complete. */
@Composable
private fun PlanCard(item: AcpItem.Plan) {
    val done = item.entries.count { it.status == "completed" }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, Palette.BorderSoft, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.acp_plan),
                style = MaterialTheme.typography.labelLarge,
                color = Palette.Text,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "$done/${item.entries.size}",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = Mono),
                color = Palette.TextFaint,
            )
        }
        Spacer(Modifier.height(8.dp))
        SelectionContainer {
            Column {
                item.entries.forEach { entry ->
                    Row(
                        modifier = Modifier.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Box(Modifier.padding(top = 3.dp).size(14.dp), contentAlignment = Alignment.Center) {
                            when (entry.status) {
                                "completed" -> Icon(TermFoldIcons.Check, null, tint = Palette.Green, modifier = Modifier.size(14.dp))
                                "in_progress" -> Box(Modifier.size(8.dp).clip(CircleShape).background(Palette.Accent))
                                else -> Box(Modifier.size(9.dp).clip(CircleShape).border(1.3.dp, Palette.TextFaint, CircleShape))
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = entry.text,
                            style = MaterialTheme.typography.bodySmall,
                            color = when (entry.status) {
                                "completed" -> Palette.TextFaint
                                "in_progress" -> Palette.Text
                                else -> Palette.TextDim
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NoticeRow(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Palette.Pink.copy(alpha = 0.07f))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(Palette.Pink))
        Spacer(Modifier.width(10.dp))
        SelectionContainer {
            Text(text = text, style = MaterialTheme.typography.bodySmall, color = Palette.Text)
        }
    }
}

/** Three stepping dots and the time spent, so a long turn never looks frozen. */
@Composable
private fun WorkingRow() {
    var seconds by remember { mutableLongStateOf(0L) }
    val start = remember { System.currentTimeMillis() }
    LaunchedWhileVisible(Unit) {
        while (true) {
            seconds = (System.currentTimeMillis() - start) / 1_000
            delay(1_000)
        }
    }
    val transition = rememberInfiniteTransition(label = "working")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(1_200)),
        label = "dots",
    )
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            val lit = phase.toInt() == index
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (lit) Palette.Accent else Palette.Border),
            )
            Spacer(Modifier.width(4.dp))
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.acp_working) + if (seconds >= 3) " · ${formatElapsed(seconds)}" else "",
            style = MaterialTheme.typography.labelSmall,
            color = Palette.TextFaint,
        )
    }
}

private fun formatElapsed(seconds: Long): String =
    if (seconds < 60) "${seconds}s" else "${seconds / 60}m ${seconds % 60}s"

@Composable
private fun PulsingText(label: String) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulseAlpha",
    )
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = Palette.Accent,
        modifier = Modifier.alpha(alpha),
    )
}

// --- Permission -------------------------------------------------------------------------------

@Composable
private fun PermissionDialog(
    pending: AcpPendingPermission,
    toolCall: AcpItem.ToolCall?,
    onPick: (String) -> Unit,
    onDeny: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDeny,
        containerColor = Palette.Card,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = stringResource(R.string.acp_permission_title),
                style = MaterialTheme.typography.titleMedium,
                color = Palette.Text,
            )
        },
        text = {
            Column {
                if (toolCall != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Palette.Bg)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(toolIcon(toolCall.kind), null, tint = Palette.TextDim, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = toolCall.title,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = Mono, fontSize = 12.5.sp),
                            color = Palette.Text,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                }
                pending.options.forEach { option ->
                    // Every option reads in white; only "allow once" is filled, as the default.
                    Text(
                        text = option.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .then(
                                if (option.kind == "allow_once") {
                                    Modifier.background(Palette.Accent)
                                } else {
                                    Modifier.border(1.dp, Palette.Border, RoundedCornerShape(12.dp))
                                },
                            )
                            .clickable { onPick(option.id) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {},
    )
}

// --- Composer ---------------------------------------------------------------------------------

/**
 * The prompt composer. Text is typed or pasted; images arrive from the picker, the clipboard, or
 * straight from the keyboard's image and GIF panels; a long paste is turned into a `.txt`
 * attachment instead of flooding the field, and goes to the agent as a file.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Composer(
    draftKey: String,
    guestWorkspace: String,
    agentId: String,
    agentName: String,
    agentBusy: Boolean,
    commands: List<AcpCommand>,
    canResume: Boolean,
    onResume: () -> Unit,
    onNewSession: () -> Unit,
    settings: List<AcpSetting>,
    onChangeSetting: (AcpSetting, String) -> Unit,
    onSend: (String, List<AcpBlock.Image>, List<AcpBlock.TextFile>) -> Unit,
    onQueue: (String, List<AcpBlock.Image>, List<AcpBlock.TextFile>) -> Unit,
    queued: List<com.termfold.app.acp.AcpQueuedPrompt>,
    onSteer: (Long) -> Unit,
    onRemoveQueued: (Long) -> Unit,
    onCancel: () -> Unit,
    branchName: String? = null,
    branchUncommitted: Boolean = false,
    onBranchClick: (() -> Unit)? = null,
    editMessage: AcpItem.UserMessage? = null,
    onCancelEdit: () -> Unit = {},
    onSubmitEdit: (String, List<AcpBlock.Image>, List<AcpBlock.TextFile>) -> Unit = { _, _, _ -> },
    automationComposerCommands: Boolean = false,
) {
    val context = LocalContext.current
    val editing = editMessage != null
    val composerKey = if (editing) "$draftKey-edit-${editMessage?.id}" else draftKey
    key(composerKey) {
    val scope = rememberCoroutineScope()
    val input = rememberTextFieldState(
        initialText = if (editing) editMessage?.text ?: "" else AcpDrafts.load(context, draftKey),
    )

    LaunchedEffect(composerKey) {
        if (editing) return@LaunchedEffect
        snapshotFlow { input.text.toString() }.collect { text ->
            AcpDrafts.save(context, draftKey, text)
        }
    }
    val images = remember(composerKey) {
        mutableStateListOf<AcpBlock.Image>().apply {
            if (editing && editMessage != null) addAll(editMessage.images) else addAll(AcpDrafts.loadImages(context, draftKey))
        }
    }
    val files = remember(composerKey) {
        mutableStateListOf<AcpBlock.TextFile>().apply {
            if (editing && editMessage != null) addAll(editMessage.files) else addAll(AcpDrafts.loadFiles(context, draftKey))
        }
    }
    LaunchedEffect(composerKey, editMessage?.id) {
        if (editing && editMessage != null) {
            input.edit {
                replace(0, length, editMessage.text)
                selection = TextRange(editMessage.text.length)
            }
            images.clear()
            images.addAll(editMessage.images)
            files.clear()
            files.addAll(editMessage.files)
        }
    }
    fun persistAttachments() {
        AcpDrafts.saveAttachments(context, draftKey, images.toList(), files.toList())
    }
    var pasteCounter by remember { mutableIntStateOf(0) }
    var preview by remember { mutableStateOf<AcpBlock.TextFile?>(null) }
    var previewImage by remember { mutableStateOf<AcpBlock.Image?>(null) }

    fun addImage(uri: android.net.Uri) {
        scope.launch {
            val encoded = withContext(Dispatchers.IO) { PastedImages.load(context, uri) }
            if (encoded != null) {
                images.add(encoded.toBlock())
                persistAttachments()
            }
        }
    }

    fun addTextFile(text: String) {
        pasteCounter++
        files.add(AcpBlock.TextFile("pasted-$pasteCounter.txt", text))
        persistAttachments()
    }

    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_COMPOSER_IMAGES),
    ) { uris ->
        uris.forEach { addImage(it) }
    }

    val canSend = input.text.isNotBlank() || images.isNotEmpty() || files.isNotEmpty()

    // Slash and @ menus follow the token at the caret (start of field or mid-sentence).
    // The app's own commands come first (resuming is the app's job, not every agent's), then
    // whatever the agent published.
    val builtIns = buildList {
        if (canResume) add(AcpCommand(BUILTIN_RESUME, stringResource(R.string.acp_cmd_resume), ""))
        add(AcpCommand(BUILTIN_NEW, stringResource(R.string.acp_cmd_new), ""))
        if (agentId == "cursor") {
            add(AcpCommand(BUILTIN_SUMMARIZE, stringResource(R.string.acp_cmd_summarize), ""))
        }
        if (automationComposerCommands) {
            add(AcpCommand(BUILTIN_AUTOMATION_SPEC, stringResource(R.string.acp_cmd_automation_spec), ""))
        }
    }
    val allCommands = builtIns + commands.filter { c -> builtIns.none { it.name == c.name } }
    val typed = input.text.toString()
    val textSelection = input.selection
    val activeToken = remember(typed, textSelection.start, textSelection.end) {
        com.termfold.app.acp.ComposerTriggers.activeToken(typed, textSelection.start, textSelection.end)
    }
    // Esc closes the menu until the token changes again.
    var dismissedFor by remember { mutableStateOf<String?>(null) }
    var dismissedAtFor by remember { mutableStateOf<String?>(null) }
    val slashMatches = if (
        activeToken?.trigger == '/' &&
        dismissedFor != activeToken.dismissKey
    ) {
        com.termfold.app.acp.ComposerFuzzyMatch.rank(
            items = allCommands.asSequence(),
            query = activeToken.query,
            limit = 40,
            searchable = { cmd -> cmd.name },
        )
    } else {
        emptyList()
    }
    val atQuery = if (
        activeToken?.trigger == '@' &&
        slashMatches.isEmpty() &&
        dismissedAtFor != activeToken.dismissKey
    ) {
        activeToken.query
    } else {
        null
    }
    var atMatches by remember { mutableStateOf<List<com.termfold.app.acp.WorkspaceMentionIndex.Entry>>(emptyList()) }
    var atLoading by remember { mutableStateOf(false) }
    LaunchedEffect(guestWorkspace, atQuery) {
        if (atQuery == null) {
            atMatches = emptyList()
            atLoading = false
            return@LaunchedEffect
        }
        val query = atQuery
        atLoading = true
        kotlinx.coroutines.delay(150)
        val results = withContext(Dispatchers.IO) {
            com.termfold.app.acp.WorkspaceMentionIndex.search(context, guestWorkspace, query)
        }
        if (query == atQuery) {
            atMatches = results
            atLoading = false
        }
    }
    // The highlighted row, moved with the arrow keys; back to the top when the text or token changes.
    var highlighted by remember(typed, activeToken?.dismissKey) { mutableIntStateOf(0) }
    val menuCount = when {
        slashMatches.isNotEmpty() -> slashMatches.size
        atQuery != null -> when {
            atLoading || atMatches.isEmpty() -> 1
            else -> atMatches.size
        }
        else -> 0
    }
    if (highlighted >= menuCount) highlighted = 0

    fun replaceActiveToken(replacement: String) {
        val token = com.termfold.app.acp.ComposerTriggers.activeToken(
            input.text.toString(),
            input.selection.start,
            input.selection.end,
        ) ?: return
        input.edit {
            replace(token.start, token.end, replacement)
            selection = TextRange(token.start + replacement.length)
        }
    }

    fun pickCommand(command: AcpCommand) {
        when (command.name) {
            BUILTIN_RESUME -> {
                input.clearText()
                images.clear()
                files.clear()
                AcpDrafts.clearAttachments(context, draftKey)
                onResume()
            }
            BUILTIN_NEW -> {
                input.clearText()
                images.clear()
                files.clear()
                AcpDrafts.clearAttachments(context, draftKey)
                onNewSession()
            }
            BUILTIN_SUMMARIZE -> {
                input.clearText()
                images.clear()
                files.clear()
                AcpDrafts.clearAttachments(context, draftKey)
                val text = "/$BUILTIN_SUMMARIZE"
                if (agentBusy || queued.isNotEmpty()) onQueue(text, emptyList(), emptyList())
                else onSend(text, emptyList(), emptyList())
            }
            BUILTIN_AUTOMATION_SPEC -> replaceActiveToken("/$BUILTIN_AUTOMATION_SPEC ")
            else -> replaceActiveToken("/${command.name} ")
        }
    }

    fun pickMention(entry: com.termfold.app.acp.WorkspaceMentionIndex.Entry) {
        replaceActiveToken("@${entry.relativePath} ")
    }

    // While the agent works, a message waits in the queue above the text area.
    fun send() {
        if (!canSend) return
        val text = input.text.toString().trim()
        val imageList = images.toList()
        val fileList = files.toList()
        if (editing) {
            onSubmitEdit(text, imageList, fileList)
            return
        }
        if (agentBusy || queued.isNotEmpty()) onQueue(text, imageList, fileList) else onSend(text, imageList, fileList)
        input.clearText()
        images.clear()
        files.clear()
        AcpDrafts.clearAttachments(context, draftKey)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = ContentMaxWidth)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Palette.Card)
                .border(1.dp, Palette.Border, RoundedCornerShape(20.dp)),
        ) {
            if (editing) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        stringResource(R.string.acp_composer_editing),
                        style = MaterialTheme.typography.labelLarge,
                        color = Palette.Accent,
                    )
                    Text(
                        stringResource(R.string.acp_composer_cancel_edit),
                        style = MaterialTheme.typography.labelLarge,
                        color = Palette.TextDim,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onCancelEdit)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Palette.BorderSoft),
                )
            }
            if (slashMatches.isNotEmpty()) {
                SlashMenu(slashMatches, highlighted = highlighted, onPick = ::pickCommand)
            }
            if (atQuery != null) {
                AtMentionMenu(
                    matches = atMatches,
                    loading = atLoading,
                    highlighted = highlighted,
                    onPick = ::pickMention,
                )
            }

            QueuedList(queued, agentBusy = agentBusy, onSteer = onSteer, onRemove = onRemoveQueued)

            if (images.isNotEmpty() || files.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(start = 10.dp, end = 10.dp, top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    files.forEach { file ->
                        FileChip(file, onClick = { preview = file }, onRemove = { files.remove(file); persistAttachments() })
                    }
                    images.forEach { image ->
                        ImageThumb(
                            image,
                            size = 56,
                            onClick = { previewImage = image },
                            onRemove = { images.remove(image); persistAttachments() },
                        )
                    }
                }
            }

            // Text first, full width; the controls sit in a toolbar beneath it, with the
            // model/reasoning control and Send together in the bottom-right corner.
            BasicTextField(
                state = input,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Palette.Text),
                cursorBrush = SolidColor(Palette.Accent),
                // A text area: three lines tall from the start so it never jumps while typing,
                // growing to eight, then scrolling inside instead of pushing the chat away.
                lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 3, maxHeightInLines = 8),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp)
                    // Pastes and keyboard content arrive here before they reach the text.
                    // Images are taken as attachments; a long text paste becomes a file.
                    .contentReceiver { content ->
                        if (content.hasMediaType(MediaType.Image) || content.hasMediaType(MediaType.Text)) {
                            content.consume { item: ClipData.Item ->
                                val uri = item.uri
                                val text = item.text?.toString()
                                when {
                                    uri != null && PastedImages.isImage(context, uri) -> {
                                        addImage(uri); true
                                    }
                                    text != null && (text.length > LongPasteChars || text.count { it == '\n' } >= LongPasteLines) -> {
                                        addTextFile(text); true
                                    }
                                    else -> false
                                }
                            }
                        } else {
                            content
                        }
                    }
                    // A hardware keyboard sends with Enter; Shift+Enter keeps a newline. While the
                    // slash menu is open the arrows move through it, Enter or Tab picks the
                    // highlighted command, and Esc closes it.
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        val slashOpen = slashMatches.isNotEmpty()
                        val atOpen = atQuery != null && (atLoading || atMatches.isNotEmpty())
                        val menuOpen = slashOpen || atOpen
                        val size = when {
                            slashOpen -> slashMatches.size
                            atLoading || atMatches.isEmpty() -> 1
                            else -> atMatches.size
                        }
                        when {
                            menuOpen && event.key == Key.DirectionDown -> {
                                highlighted = (highlighted + 1) % size; true
                            }
                            menuOpen && event.key == Key.DirectionUp -> {
                                highlighted = (highlighted - 1 + size) % size; true
                            }
                            slashOpen && (event.key == Key.Tab || (event.key == Key.Enter && !event.isShiftPressed)) -> {
                                pickCommand(slashMatches[highlighted]); true
                            }
                            atOpen && !atLoading && atMatches.isNotEmpty() &&
                                (event.key == Key.Tab || (event.key == Key.Enter && !event.isShiftPressed)) -> {
                                pickMention(atMatches[highlighted]); true
                            }
                            slashOpen && event.key == Key.Escape -> {
                                dismissedFor = activeToken?.dismissKey; true
                            }
                            atOpen && event.key == Key.Escape -> {
                                dismissedAtFor = activeToken?.dismissKey; true
                            }
                            event.key == Key.Enter && !event.isShiftPressed -> {
                                send(); true
                            }
                            else -> false
                        }
                    },
                decorator = { inner ->
                    Box {
                        if (input.text.isEmpty()) {
                            Text(
                                text = stringResource(R.string.acp_message_hint, agentName),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Palette.TextFaint,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        inner()
                    }
                },
            )


            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp, end = 8.dp, top = 2.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BareIconButton(
                    icon = TermFoldIcons.Image,
                    contentDescription = stringResource(R.string.acp_attach_image),
                    onClick = {
                        pickImages.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    tint = Palette.TextDim,
                    size = 38,
                )
                Spacer(Modifier.weight(1f))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (branchName != null && onBranchClick != null) {
                        BranchChip(
                            label = branchName.ifBlank { stringResource(R.string.workspace_branch_unknown) },
                            onClick = onBranchClick,
                            truncate = true,
                            uncommittedChanges = branchUncommitted,
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    if (settings.isNotEmpty()) {
                        SettingsButton(agentId, settings, onChangeSetting)
                        Spacer(Modifier.width(4.dp))
                    }
                    // While the agent works: Stop, and beside it Send, which queues the message.
                    if (agentBusy) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Palette.CardPressed)
                                .clickable(onClick = onCancel),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(TermFoldIcons.Stop, stringResource(R.string.acp_stop), tint = Palette.Text, modifier = Modifier.size(16.dp))
                        }
                    }
                    if (!agentBusy || canSend) {
                        Box(
                            modifier = Modifier
                                .padding(start = if (agentBusy) 6.dp else 0.dp)
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (canSend) Palette.Accent else Palette.CardPressed)
                                .clickable(enabled = canSend) { send() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = TermFoldIcons.Send,
                                contentDescription = stringResource(if (agentBusy) R.string.acp_queue else R.string.cd_send),
                                tint = if (canSend) Palette.OnAccent else Palette.TextFaint,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    previewImage?.let { image ->
        AcpImagePreviewDialog(image = image, onDismiss = { previewImage = null })
    }

    preview?.let { file ->
        AlertDialog(
            onDismissRequest = { preview = null },
            containerColor = Palette.Card,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(file.name, style = MaterialTheme.typography.titleMedium.copy(fontFamily = Mono), color = Palette.Text)
            },
            text = {
                SelectionContainer {
                    Text(
                        text = file.text,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = Mono, fontSize = 12.sp),
                        color = Palette.TermOut,
                        modifier = Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                    )
                }
            },
            confirmButton = {
                Text(
                    text = stringResource(R.string.action_close),
                    style = MaterialTheme.typography.labelLarge,
                    color = Palette.Accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { preview = null }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            },
        )
    }
    }
}

/**
 * Messages waiting for the agent, above the text area: each can be sent now (stopping the
 * current turn so the agent takes it straight away) or removed.
 */
@Composable
private fun QueuedList(
    queued: List<com.termfold.app.acp.AcpQueuedPrompt>,
    agentBusy: Boolean,
    onSteer: (Long) -> Unit,
    onRemove: (Long) -> Unit,
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = queued.isNotEmpty(),
        enter = fadeIn(Motion.standard()) + androidx.compose.animation.expandVertically(Motion.standard()),
        exit = fadeOut(Motion.quick()) + androidx.compose.animation.shrinkVertically(Motion.quick()),
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 10.dp)) {
            queued.forEach { item ->
                key(item.id) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Palette.Bg)
                            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.acp_queued),
                                style = MaterialTheme.typography.labelSmall,
                                color = Palette.TextFaint,
                            )
                            val attachments = item.images.size + item.files.size
                            Text(
                                text = item.text.ifBlank { "" } +
                                    if (attachments > 0) {
                                        (if (item.text.isBlank()) "" else "  ·  ") +
                                            androidx.compose.ui.res.pluralStringResource(R.plurals.acp_attachments, attachments, attachments)
                                    } else {
                                        ""
                                    },
                                style = MaterialTheme.typography.bodySmall,
                                color = Palette.Text,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            stringResource(if (agentBusy) R.string.acp_steer else R.string.cd_send),
                            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                            color = Palette.Accent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onSteer(item.id) }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                        )
                        BareIconButton(
                            icon = TermFoldIcons.Close,
                            contentDescription = stringResource(R.string.acp_remove),
                            onClick = { onRemove(item.id) },
                            tint = Palette.TextFaint,
                            size = 32,
                        )
                    }
                }
            }
        }
    }
}

/** Picker order: what people change most comes first. */
private fun settingRank(setting: AcpSetting): Int = when (setting.category) {
    "model" -> 0
    "thought_level" -> 1
    "mode" -> 2
    "model_config" -> 3
    else -> 4
}

/** Above this many choices the popup gets a search field. */
private const val SearchThreshold = 8

/** How many recently picked models are remembered per agent. */
private const val RecentModels = 5

/** Recently picked model values for one agent, newest first, kept in app preferences. */
private fun recentModels(context: android.content.Context, agentId: String): List<String> =
    context.getSharedPreferences("acp", android.content.Context.MODE_PRIVATE)
        .getString("recent_models_$agentId", "").orEmpty()
        .split('\n').filter { it.isNotBlank() }

private fun rememberModel(context: android.content.Context, agentId: String, value: String) {
    val list = (listOf(value) + recentModels(context, agentId).filter { it != value }).take(RecentModels)
    context.getSharedPreferences("acp", android.content.Context.MODE_PRIVATE)
        .edit().putString("recent_models_$agentId", list.joinToString("\n")).apply()
}

/** "openai/gpt-5" -> "openai"; ids without a provider prefix have none. */
private fun providerOf(value: String): String? =
    value.substringBefore('/', missingDelimiterValue = "").ifBlank { null }

/**
 * The session-settings control in the input bar: the sliders icon plus a short reading of the
 * current model and reasoning level, opening a popup with every setting the agent offers.
 *
 * Long model lists (Pi and OpenCode list every model of every provider they can reach) get a
 * search field, provider grouping, and a Recent section with what the user actually picks.
 */
@Composable
private fun SettingsButton(
    agentId: String,
    settings: List<AcpSetting>,
    onChange: (AcpSetting, String) -> Unit,
) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    /** The setting whose full list is open, by id; null shows the overview. */
    var page by remember { mutableStateOf<String?>(null) }
    var recent by remember(agentId) { mutableStateOf(recentModels(context, agentId)) }
    val ordered = settings.sortedBy(::settingRank)
    val summary = listOfNotNull(
        settings.firstOrNull { it.category == "model" }?.currentLabel,
        settings.firstOrNull { it.category == "thought_level" }?.currentLabel,
    ).joinToString(" · ")

    fun pick(setting: AcpSetting, value: String) {
        onChange(setting, value)
        if (setting.category == "model") {
            rememberModel(context, agentId, value)
            recent = recentModels(context, agentId)
        }
        open = false
        page = null
        query = ""
    }

    Box {
        Row(
            modifier = Modifier
                .padding(vertical = 2.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable { open = true }
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                TermFoldIcons.Sliders,
                contentDescription = stringResource(R.string.acp_settings),
                tint = if (open) Palette.Accent else Palette.TextDim,
                modifier = Modifier.size(18.dp),
            )
            if (summary.isNotBlank()) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                    color = Palette.TextDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 200.dp),
                )
            }
        }

        androidx.compose.material3.DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false; page = null; query = "" },
            containerColor = Palette.Card,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .widthIn(min = 300.dp, max = 400.dp)
                .heightIn(max = 520.dp),
        ) {
            val listPage = page?.let { id -> ordered.firstOrNull { it.id == id } }
            if (listPage == null) {
                // Overview: every setting at a glance. Short lists are one-tap chips; the model
                // (and any other long list) is a single row that opens its own page, so the
                // settings after it never sit below a hundred models.
                ordered.forEachIndexed { index, setting ->
                    if (index > 0) SettingsDivider()
                    when {
                        setting.isToggle -> {
                            SettingsHeader(setting.name)
                            val on = setting.current.toBoolean()
                            ChoiceChips(
                                choices = listOf(
                                    com.termfold.app.acp.AcpChoice("false", stringResource(R.string.acp_setting_off)),
                                    com.termfold.app.acp.AcpChoice("true", stringResource(R.string.acp_setting_on)),
                                ),
                                current = on.toString(),
                                onPick = { value -> onChange(setting, value) },
                            )
                        }

                        opensAsPage(setting) -> Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { page = setting.id; query = "" }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = setting.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Palette.TextFaint,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = setting.currentLabel,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp),
                                    color = Palette.Text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                text = "${setting.choices.size}",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = Mono),
                                color = Palette.TextFaint,
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(TermFoldIcons.ChevronRight, null, tint = Palette.TextFaint, modifier = Modifier.size(16.dp))
                        }

                        else -> {
                            SettingsHeader(setting.name)
                            ChoiceChips(
                                choices = setting.choices,
                                current = setting.current,
                                onPick = { value -> onChange(setting, value) },
                            )
                            val description = setting.choices.firstOrNull { it.value == setting.current }?.description
                            if (!description.isNullOrBlank()) {
                                Text(
                                    text = description,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Palette.TextFaint,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 4.dp),
                                )
                            }
                        }
                    }
                }
            } else {
                // The list page: back to the overview, search, recent picks, provider groups.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 16.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BareIconButton(
                        icon = TermFoldIcons.Back,
                        contentDescription = stringResource(R.string.cd_back),
                        onClick = { page = null; query = "" },
                        tint = Palette.TextDim,
                        size = 36,
                    )
                    Text(
                        text = listPage.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = Palette.Text,
                    )
                }
                if (listPage.choices.size > SearchThreshold) {
                    SettingsSearchField(query = query, onQuery = { query = it })
                }

                val needle = query.trim().lowercase()
                val shown = listPage.choices.filter { choice ->
                    needle.isEmpty() ||
                        choice.name.lowercase().contains(needle) ||
                        choice.value.lowercase().contains(needle) ||
                        choice.description.lowercase().contains(needle)
                }
                val isModel = listPage.category == "model"

                val recentChoices = if (isModel && needle.isEmpty() && listPage.choices.size > SearchThreshold) {
                    recent.mapNotNull { value -> listPage.choices.firstOrNull { it.value == value } }
                } else {
                    emptyList()
                }
                if (recentChoices.isNotEmpty()) {
                    SettingsSubHeader(stringResource(R.string.acp_setting_recent))
                    recentChoices.forEach { choice ->
                        SettingRow(choice.name, choice.description, choice.value == listPage.current) { pick(listPage, choice.value) }
                    }
                }

                val groups = shown.groupBy { providerOf(it.value) }
                if (isModel && groups.size > 1) {
                    groups.forEach { (provider, choices) ->
                        SettingsSubHeader(provider ?: stringResource(R.string.acp_setting_other))
                        choices.forEach { choice ->
                            SettingRow(choice.name, choice.description, choice.value == listPage.current) { pick(listPage, choice.value) }
                        }
                    }
                } else {
                    shown.forEach { choice ->
                        SettingRow(choice.name, choice.description, choice.value == listPage.current) { pick(listPage, choice.value) }
                    }
                }
                if (shown.isEmpty()) {
                    Text(
                        text = stringResource(R.string.acp_setting_no_match),
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.TextFaint,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}

/** Models always get their own page; so does any other setting too long for a row of chips. */
private fun opensAsPage(setting: AcpSetting): Boolean =
    setting.category == "model" || setting.choices.size > 6

/** A setting's choices as a row of one-tap chips (reasoning level, mode, on/off). */
@Composable
private fun ChoiceChips(
    choices: List<com.termfold.app.acp.AcpChoice>,
    current: String,
    onPick: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        choices.forEach { choice ->
            val selected = choice.value == current
            Text(
                text = choice.name,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                color = if (selected) Palette.OnAccent else Palette.TextDim,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) Palette.Accent else Palette.Bg)
                    .clickable { onPick(choice.value) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun SettingsSearchField(query: String, onQuery: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Palette.Bg)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(TermFoldIcons.Search, contentDescription = null, tint = Palette.TextFaint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        androidx.compose.foundation.text.BasicTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp, color = Palette.Text),
            cursorBrush = SolidColor(Palette.Accent),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box {
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.acp_setting_search),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp),
                            color = Palette.TextFaint,
                        )
                    }
                    inner()
                }
            },
        )
        if (query.isNotEmpty()) {
            Icon(
                TermFoldIcons.Close,
                contentDescription = null,
                tint = Palette.TextFaint,
                modifier = Modifier
                    .size(16.dp)
                    .clickable { onQuery("") },
            )
        }
    }
}

@Composable
private fun SettingsHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = Palette.TextFaint,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingsSubHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontFamily = Mono),
        color = Palette.Accent.copy(alpha = 0.8f),
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun SettingsDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .height(1.dp)
            .background(Palette.BorderSoft),
    )
}

@Composable
private fun SettingRow(label: String, description: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp),
                color = if (selected) Palette.Text else Palette.TextDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (description.isNotBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextFaint,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (selected) {
            Spacer(Modifier.width(10.dp))
            Icon(TermFoldIcons.Check, contentDescription = null, tint = Palette.Accent, modifier = Modifier.size(16.dp))
        }
    }
}

/** Commands the app handles itself rather than sending to the agent. */
private const val MAX_COMPOSER_IMAGES = 20

private suspend fun syncWorkspaceGit(
    context: android.content.Context,
    guestPath: String,
    onBranch: (String) -> Unit,
    onDirty: (Boolean) -> Unit,
) {
    val git = com.termfold.app.gitworkspace.GitWorkspace
    git.readBranchFromHost(context, guestPath)?.let(onBranch)
    onDirty(git.isDirty(context, guestPath))
}

private const val BUILTIN_RESUME = "resume"
private const val BUILTIN_NEW = "new"
private const val BUILTIN_SUMMARIZE = "summarize"
private const val BUILTIN_AUTOMATION_SPEC = com.termfold.app.automation.AutomationSpecCommand.SLASH_COMMAND

/**
 * The slash commands matching what is typed. Tapping one picks it; with a keyboard the arrows
 * move the highlight, which is kept on screen.
 */
@Composable
private fun SlashMenu(matches: List<AcpCommand>, highlighted: Int, onPick: (AcpCommand) -> Unit) {
    val listState = rememberLazyListState()
    // Scroll only as far as needed to keep the highlighted row on screen.
    LaunchedEffect(highlighted, matches.size) {
        if (matches.isEmpty()) return@LaunchedEffect
        val visible = listState.layoutInfo.visibleItemsInfo
        val viewportEnd = listState.layoutInfo.viewportEndOffset
        val fully = visible.filter { it.offset >= 0 && it.offset + it.size <= viewportEnd }
        val first = fully.firstOrNull()?.index ?: 0
        val last = fully.lastOrNull()?.index ?: 0
        when {
            highlighted < first -> listState.animateScrollToItem(highlighted)
            highlighted > last -> listState.animateScrollToItem((highlighted - (fully.size - 1)).coerceAtLeast(0))
        }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 260.dp)
            .padding(top = 6.dp, start = 6.dp, end = 6.dp),
    ) {
        items(matches.size) { index ->
            val command = matches[index]
            val active = index == highlighted
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (active) Palette.CardPressed else Color.Transparent)
                    .clickable { onPick(command) }
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "/" + command.name,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = Mono, fontSize = 13.sp),
                    color = Palette.Accent,
                    maxLines = 1,
                )
                if (command.description.isNotBlank() || command.hint.isNotBlank()) {
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = command.description.ifBlank { command.hint },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (active) Palette.TextDim else Palette.TextFaint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .height(1.dp)
            .background(Palette.BorderSoft),
    )
}

@Composable
private fun AtMentionMenu(
    matches: List<com.termfold.app.acp.WorkspaceMentionIndex.Entry>,
    loading: Boolean,
    highlighted: Int,
    onPick: (com.termfold.app.acp.WorkspaceMentionIndex.Entry) -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(highlighted, matches.size, loading) {
        if (loading || matches.isEmpty()) return@LaunchedEffect
        val visible = listState.layoutInfo.visibleItemsInfo
        val viewportEnd = listState.layoutInfo.viewportEndOffset
        val fully = visible.filter { it.offset >= 0 && it.offset + it.size <= viewportEnd }
        val first = fully.firstOrNull()?.index ?: 0
        val last = fully.lastOrNull()?.index ?: 0
        when {
            highlighted < first -> listState.animateScrollToItem(highlighted)
            highlighted > last -> listState.animateScrollToItem((highlighted - (fully.size - 1)).coerceAtLeast(0))
        }
    }
    if (loading || matches.isEmpty()) {
        val active = highlighted == 0
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (active) Palette.CardPressed else Color.Transparent)
                .padding(horizontal = 10.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = Palette.Accent,
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(
                    if (loading) R.string.acp_mention_loading else R.string.acp_mention_empty,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextFaint,
            )
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 260.dp)
                .padding(top = 6.dp, start = 6.dp, end = 6.dp),
        ) {
            items(matches.size) { index ->
                val entry = matches[index]
                val active = index == highlighted
                val lines = com.termfold.app.acp.WorkspaceMentionIndex.displayLines(entry.relativePath)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (active) Palette.CardPressed else Color.Transparent)
                        .clickable { onPick(entry) }
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (entry.isDirectory) TermFoldIcons.Folder else TermFoldIcons.Files,
                        contentDescription = null,
                        tint = if (active) Palette.Accent else Palette.TextFaint,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "@${lines.mentionName}",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = Mono, fontSize = 13.sp),
                            color = Palette.Accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = lines.fullPathFromRoot,
                            style = MaterialTheme.typography.labelSmall,
                            color = Palette.TextFaint,
                            maxLines = 1,
                            overflow = TextOverflow.StartEllipsis,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(
                            if (entry.isDirectory) R.string.acp_mention_folder else R.string.acp_mention_file,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (active) Palette.TextDim else Palette.TextFaint,
                    )
                }
            }
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .height(1.dp)
            .background(Palette.BorderSoft),
    )
}

/**
 * The agent's earlier sessions in this folder. Picking one reopens it (with its history when the
 * agent replays it); "New session" starts over.
 */
@Composable
private fun PastSessionsDialog(
    client: AcpClient,
    currentId: String?,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var sessions by remember { mutableStateOf<List<com.termfold.app.acp.AcpPastSession>?>(null) }
    var query by remember { mutableStateOf("") }
    var confirming by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(currentId) { sessions = client.listPastSessions() }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Card,
        shape = RoundedCornerShape(20.dp),
        title = { Text(stringResource(R.string.acp_history_title), style = MaterialTheme.typography.titleMedium, color = Palette.Text) },
        text = {
            Column(Modifier.heightIn(max = 520.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Palette.Bg)
                        .clickable { client.startNewSession(); onDismiss() }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(TermFoldIcons.Plus, null, tint = Palette.Accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.acp_cmd_new), style = MaterialTheme.typography.labelLarge, color = Palette.Text)
                }
                Spacer(Modifier.height(10.dp))
                val list = sessions
                if (!list.isNullOrEmpty()) {
                    SessionSearch(query, onQuery = { query = it })
                    Spacer(Modifier.height(8.dp))
                }
                val shown = list?.filter { query.isBlank() || it.title.contains(query.trim(), ignoreCase = true) || it.sessionId.startsWith(query.trim()) }
                when {
                    list == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Palette.Accent)
                    }
                    list.isEmpty() -> Text(
                        stringResource(R.string.acp_history_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.TextFaint,
                        modifier = Modifier.padding(8.dp),
                    )
                    shown.isNullOrEmpty() -> Text(
                        stringResource(R.string.acp_history_no_match, query.trim()),
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.TextFaint,
                        modifier = Modifier.padding(8.dp),
                    )
                    else -> LazyColumn {
                        items(shown, key = { it.sessionId }) { past ->
                            val current = past.sessionId == currentId
                            val asking = confirming == past.sessionId
                            Row(
                                modifier = Modifier
                                    .animateItem()
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (asking) Palette.Pink.copy(alpha = 0.08f) else Color.Transparent)
                                    .clickable(enabled = !current && !asking) { client.openPastSession(past.sessionId); onDismiss() }
                                    .padding(start = 12.dp, end = 2.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = if (asking) stringResource(R.string.acp_history_delete_confirm) else past.title.ifBlank { stringResource(R.string.acp_history_untitled) },
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp),
                                        color = when {
                                            asking -> Palette.Pink
                                            current -> Palette.Accent
                                            else -> Palette.Text
                                        },
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    val meta = if (asking) {
                                        past.title.ifBlank { stringResource(R.string.acp_history_untitled) }
                                    } else {
                                        listOfNotNull(
                                            relativeTime(past.updatedAt).ifBlank { null },
                                            if (current) stringResource(R.string.acp_history_current) else null,
                                        ).joinToString(" · ")
                                    }
                                    if (meta.isNotBlank()) {
                                        Text(meta, style = MaterialTheme.typography.labelSmall, color = Palette.TextFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                if (asking) {
                                    Text(
                                        stringResource(R.string.action_cancel),
                                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                                        color = Palette.TextDim,
                                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { confirming = null }.padding(horizontal = 10.dp, vertical = 8.dp),
                                    )
                                    Text(
                                        stringResource(R.string.files_delete),
                                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                                        color = Palette.Pink,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                confirming = null
                                                sessions = sessions?.filterNot { it.sessionId == past.sessionId }
                                                scope.launch { client.deletePastSession(past.sessionId) }
                                            }
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                    )
                                } else if (!current) {
                                    BareIconButton(
                                        icon = TermFoldIcons.Trash,
                                        contentDescription = stringResource(R.string.files_delete),
                                        onClick = { confirming = past.sessionId },
                                        tint = Palette.TextFaint,
                                        size = 36,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun SessionSearch(query: String, onQuery: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.Bg)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(TermFoldIcons.Search, null, tint = Palette.TextFaint, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(stringResource(R.string.acp_history_search), style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp), color = Palette.TextFaint, maxLines = 1)
            }
            androidx.compose.foundation.text.BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp, color = Palette.Text),
                cursorBrush = SolidColor(Palette.Accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            BareIconButton(icon = TermFoldIcons.Close, contentDescription = stringResource(R.string.action_cancel), onClick = { onQuery("") }, tint = Palette.TextFaint, size = 32)
        }
    }
}

/** "3 min ago", "yesterday", "12 Sep" from an ISO-8601 timestamp; blank if unparseable. */
private fun relativeTime(iso: String): String {
    val then = runCatching { java.time.Instant.parse(iso) }.getOrNull()
        ?: runCatching { java.time.OffsetDateTime.parse(iso).toInstant() }.getOrNull()
        ?: return ""
    val minutes = java.time.Duration.between(then, java.time.Instant.now()).toMinutes()
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 24 * 60 -> "${minutes / 60} h ago"
        minutes < 48 * 60 -> "yesterday"
        minutes < 7 * 24 * 60 -> "${minutes / (24 * 60)} days ago"
        else -> java.time.format.DateTimeFormatter.ofPattern("d MMM")
            .format(then.atZone(java.time.ZoneId.systemDefault()))
    }
}

private fun Int.toShortCount(): String =
    if (this >= 1_000) "${"%.1f".format(this / 1_000f)}k" else toString()

private fun toolIcon(kind: String): ImageVector = when (kind) {
    "read" -> TermFoldIcons.FileText
    "edit", "delete", "move" -> TermFoldIcons.Pencil
    "search", "fetch" -> TermFoldIcons.Search
    "execute", "command" -> TermFoldIcons.Terminal
    "think" -> TermFoldIcons.ChevronRight
    else -> TermFoldIcons.Gear
}

