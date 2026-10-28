package com.termfold.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.termfold.app.R
import com.termfold.app.acp.AcpItem
import com.termfold.app.core.Folder
import com.termfold.app.core.Session
import com.termfold.app.gitworkspace.WorkspaceStore
import com.termfold.app.prreview.DoneKind
import com.termfold.app.prreview.PrReviewKeys
import com.termfold.app.prreview.PrReviewParse
import com.termfold.app.prreview.PrReviewPaths
import com.termfold.app.prreview.PrReviewPrompt
import com.termfold.app.memory.AgentMemoryWorkflow
import com.termfold.app.prreview.PrReviewReportBridge
import com.termfold.app.prreview.PrReviewSessionContext
import com.termfold.app.preview.BrowserBridge
import com.termfold.app.prreview.PrReviewSession
import com.termfold.app.prreview.PrReviewReportParser
import com.termfold.app.prreview.prReviewChromeFromParse
import com.termfold.app.prreview.prReviewJsonForTurn
import com.termfold.app.prreview.prReviewParseErrorFallback
import com.termfold.app.prreview.prReviewReportsAlignedToTimeline
import com.termfold.app.ui.theme.Palette
import kotlinx.coroutines.launch

private const val STUB_WORKSPACE_ID = "pr-stub"

@Composable
fun PrReviewAgentSessionScreen(
    session: PrReviewSession,
    codingFolder: Folder?,
    onBack: () -> Unit,
    onDoneKind: suspend (String) -> DoneKind,
    onApplyDone: (String) -> Unit,
    onPasteAnother: (String) -> Unit,
    onSaveParse: (userMessageId: Long, parse: PrReviewParse) -> Unit = { _, _ -> },
    onSyncReportsToTimeline: (items: List<AcpItem>, reports: Map<Long, String>) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    filesOpen: Boolean = false,
    onToggleFiles: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmDone by remember { mutableStateOf(false) }
    var pasteOpen by remember { mutableStateOf(false) }
    var pasteValue by remember { mutableStateOf("") }
    var agentWasBusy by remember(session.id) { mutableStateOf(false) }
    var lastUserMessageId by remember(session.id) { mutableStateOf<Long?>(null) }
    var reportsByUserMessageId by remember(session.id) {
        mutableStateOf(session.reportsByUserMessageId)
    }
    LaunchedEffect(session.reportsByUserMessageId) {
        reportsByUserMessageId = session.reportsByUserMessageId
    }
    LaunchedEffect(session.id) {
        PrReviewPaths.ensureStub(context, session.id)
    }
    val submitListenerOwner = remember(session.id) { Any() }
    DisposableEffect(session.id) {
        val owner = submitListenerOwner
        PrReviewSessionContext.activeSessionId = session.id
        BrowserBridge.prReviewSessionId = session.id
        BrowserBridge.writeBrowserConfig(context.applicationContext)
        PrReviewReportBridge.setOnSubmit(owner) { sid, userMessageId, parse ->
            if (sid == session.id) {
                val json = PrReviewReportParser.encode(parse.report).toString()
                reportsByUserMessageId = reportsByUserMessageId + (userMessageId to json)
                onSaveParse(userMessageId, parse)
            }
        }
        onDispose {
            if (PrReviewSessionContext.activeSessionId == session.id) {
                PrReviewSessionContext.activeSessionId = null
                PrReviewSessionContext.activeUserMessageId = null
            }
            if (BrowserBridge.prReviewSessionId == session.id) {
                BrowserBridge.prReviewSessionId = null
            }
            BrowserBridge.writeBrowserConfig(context.applicationContext)
            PrReviewReportBridge.clearOnSubmit(owner)
        }
    }
    val copyGuest = codingFolder?.let { folder ->
        session.workspaceId?.let { WorkspaceStore.guestPath(context, folder, it) }
    }
    val guestDir = copyGuest ?: PrReviewPaths.sessionGuest(session.id)
    val folder = Folder(
        id = "pr-review-${session.id}",
        name = session.name,
        path = PrReviewPaths.hostSessionDir(context, session.id).absolutePath,
        tint = session.tint,
        sessions = listOf(
            Session(
                id = session.id,
                name = session.name,
                command = "",
                tint = session.tint,
                acpAgentId = session.acpAgentId,
                workspaceId = session.workspaceId ?: STUB_WORKSPACE_ID,
            ),
        ),
    )
    AgentSessionScreen(
        folder = folder,
        sessionId = session.id,
        workspaceId = session.workspaceId ?: STUB_WORKSPACE_ID,
        agentId = session.acpAgentId,
        onBack = onBack,
        modifier = modifier,
        filesOpen = filesOpen,
        onToggleFiles = onToggleFiles,
        sessionKeyOverride = PrReviewKeys.acpSessionKey(session.id),
        guestDirOverride = guestDir,
        hideBranchPicker = true,
        promptPrefix = { _ -> PrReviewPrompt.prefix(session) },
        agentMemoryWorkflow = AgentMemoryWorkflow.PR_REVIEW,
        prReviewMode = true,
        turnFooter = { userMessageId, turnItems, turnFinished ->
            val parse = PrReviewReportParser.parseTurnSlice(turnItems, turnFinished = turnFinished)
            val savedJson = prReviewJsonForTurn(
                userMessageId = userMessageId,
                turnItems = turnItems,
                turnFinished = turnFinished,
                reportsByUserMessageId = reportsByUserMessageId,
                lastUserMessageId = lastUserMessageId,
                sessionLastReportJson = session.lastReportJson,
            )
            val (report, parseError) = prReviewChromeFromParse(
                parse = parse,
                fallbackReport = savedJson?.let { prReviewReportFromSessionJson(it) },
                fallbackParseError = prReviewParseErrorFallback(
                    userMessageId = userMessageId,
                    lastUserMessageId = lastUserMessageId,
                    reportsByUserMessageId = reportsByUserMessageId,
                    sessionLastParseError = session.lastParseError,
                ),
            )
            PrReviewReportChrome(report = report, parseError = parseError)
        },
        rewriteAgentText = PrReviewReportParser::stripFences,
        onAcpSnapshot = { items: List<AcpItem>, busy ->
            val lastUser = items.filterIsInstance<AcpItem.UserMessage>().lastOrNull()?.id
            lastUserMessageId = lastUser
            PrReviewSessionContext.activeUserMessageId = lastUser
            val aligned = prReviewReportsAlignedToTimeline(items, reportsByUserMessageId)
            if (aligned != reportsByUserMessageId) {
                reportsByUserMessageId = aligned
                onSyncReportsToTimeline(items, aligned)
            }
            if (agentWasBusy && !busy) {
                val turnUserId = lastUserMessageId
                if (turnUserId != null) {
                    val turnItems = agentChatItemsForUserTurn(items, turnUserId)
                    val parse = PrReviewReportParser.parseTurnSlice(turnItems, turnFinished = true)
                    val alreadySaved = reportsByUserMessageId[turnUserId]
                    when {
                        parse is PrReviewParse.Ok -> {
                            val json = PrReviewReportParser.encode(parse.report).toString()
                            reportsByUserMessageId = reportsByUserMessageId + (turnUserId to json)
                            onSaveParse(turnUserId, parse)
                        }
                        alreadySaved != null -> onSaveParse(turnUserId, PrReviewParse.Skipped)
                        else -> onSaveParse(turnUserId, parse)
                    }
                }
            }
            agentWasBusy = busy
        },
        onReviewDone = {
            scope.launch {
                when (onDoneKind(session.id)) {
                    DoneKind.AlreadyGone, DoneKind.DeleteNow -> onApplyDone(session.id)
                    DoneKind.ConfirmDirtyOrUnpushed -> confirmDone = true
                }
            }
        },
        onReviewAnother = { pasteOpen = true },
    )
    if (confirmDone) {
        AlertDialog(
            onDismissRequest = { confirmDone = false },
            title = { Text(stringResource(R.string.pr_review_done_confirm_title), color = Palette.Text) },
            text = { Text(stringResource(R.string.pr_review_done_confirm_body), color = Palette.TextDim) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDone = false
                        onApplyDone(session.id)
                    },
                ) {
                    Text(stringResource(R.string.pr_review_done_delete), color = Palette.Accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDone = false }) {
                    Text(stringResource(R.string.action_cancel), color = Palette.TextDim)
                }
            },
        )
    }
    if (pasteOpen) {
        AlertDialog(
            onDismissRequest = { pasteOpen = false },
            title = { Text(stringResource(R.string.pr_review_paste_another_title), color = Palette.Text) },
            text = {
                Column {
                    OutlinedTextField(
                        value = pasteValue,
                        onValueChange = { pasteValue = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(stringResource(R.string.pr_review_url_hint), color = Palette.TextFaint)
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Palette.Text,
                            unfocusedTextColor = Palette.Text,
                            cursorColor = Palette.Accent,
                            focusedBorderColor = Palette.Accent,
                            unfocusedBorderColor = Palette.Border,
                        ),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val next = pasteValue.trim()
                        if (next.isNotEmpty()) {
                            pasteOpen = false
                            pasteValue = ""
                            onPasteAnother(next)
                        }
                    },
                ) {
                    Text(stringResource(R.string.pr_review_start), color = Palette.Accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { pasteOpen = false }) {
                    Text(stringResource(R.string.action_cancel), color = Palette.TextDim)
                }
            },
        )
    }
}
