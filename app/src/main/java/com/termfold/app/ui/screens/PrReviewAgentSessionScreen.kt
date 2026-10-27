package com.termfold.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.termfold.app.prreview.PrReviewReport
import com.termfold.app.prreview.PrReviewReportParser
import com.termfold.app.prreview.PrReviewSession
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
    onSaveParse: (PrReviewParse) -> Unit = {},
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
    LaunchedEffect(session.id) {
        PrReviewPaths.ensureStub(context, session.id)
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
        turnFooter = { userMessageId, turnItems ->
            val parse = PrReviewReportParser.parseTurnSlice(turnItems)
            val useSessionFallback = userMessageId == lastUserMessageId &&
                agentChatTurnHasAgentReply(turnItems)
            val (report, parseError) = prReviewChromeFromParse(
                parse = parse,
                fallbackReport = if (useSessionFallback) {
                    prReviewReportFromSessionJson(session.lastReportJson)
                } else {
                    null
                },
                fallbackParseError = if (useSessionFallback) session.lastParseError else null,
            )
            PrReviewReportChrome(report = report, parseError = parseError)
        },
        rewriteAgentText = PrReviewReportParser::stripFences,
        onAcpSnapshot = { items: List<AcpItem>, busy ->
            lastUserMessageId = items.filterIsInstance<AcpItem.UserMessage>().lastOrNull()?.id
            if (agentWasBusy && !busy) {
                onSaveParse(PrReviewReportParser.parseTurn(items))
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

private fun prReviewChromeFromParse(
    parse: PrReviewParse,
    fallbackReport: PrReviewReport?,
    fallbackParseError: String?,
): Pair<PrReviewReport?, String?> {
    if (parse is PrReviewParse.Ok) return parse.report to null
    if (fallbackReport != null && parse !is PrReviewParse.Invalid) {
        return fallbackReport to fallbackParseError
    }
    return when (parse) {
        PrReviewParse.Missing -> null to "missing"
        is PrReviewParse.Invalid -> null to parse.reason
        PrReviewParse.Skipped -> null to null
        is PrReviewParse.Ok -> parse.report to null
    }
}
