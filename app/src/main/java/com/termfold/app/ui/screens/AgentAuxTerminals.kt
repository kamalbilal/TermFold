package com.termfold.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.termfold.app.R
import com.termfold.app.shell.TerminalHost
import com.termfold.app.ui.components.BareIconButton
import com.termfold.app.ui.components.ConfigureDialogWindowForKeyboard
import com.termfold.app.ui.theme.Palette
import com.termfold.app.ui.theme.TermFoldIcons

fun auxTerminalSessionId(acpSessionId: String, tabId: String): String = "aux-$acpSessionId-$tabId"

@Composable
private fun AuxTerminalTabChip(
    title: String,
    selected: Boolean,
    showClose: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Palette.CardPressed else Palette.Card),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.agent_aux_terminal_tab, title),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Palette.Text else Palette.TextDim,
            modifier = Modifier
                .clickable(onClick = onSelect)
                .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = if (showClose) 2.dp else 12.dp),
        )
        if (showClose) {
            BareIconButton(
                icon = TermFoldIcons.Close,
                contentDescription = stringResource(R.string.agent_aux_terminal_close_tab),
                onClick = onClose,
                tint = if (selected) Palette.TextDim else Palette.TextFaint,
                size = 32,
            )
        }
    }
}

@Composable
fun AuxTerminalCloseConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.agent_aux_terminal_close_confirm_title)) },
        text = { Text(stringResource(R.string.agent_aux_terminal_close_confirm_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.agent_aux_terminal_close_confirm_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
fun AgentAuxTerminalDialog(
    folderId: String,
    workspaceId: String,
    acpSessionId: String,
    guestWorkspace: String,
    folderLabel: String,
    initialTabId: String? = null,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val parentKey = AuxTerminalTabsStore.parentKey(folderId, workspaceId, acpSessionId)
    val tabs = rememberAuxTerminalTabs(parentKey)
    var selectedTabId by remember(parentKey) {
        mutableStateOf(AuxTerminalTabsStore.selectedTabId(parentKey) ?: tabs.first().id)
    }
    var pendingCloseTab by remember { mutableStateOf<AuxTerminalTab?>(null) }

    LaunchedEffect(parentKey, tabs.size, initialTabId) {
        if (tabs.isEmpty()) return@LaunchedEffect
        val pick = when {
            initialTabId != null && tabs.any { it.id == initialTabId } -> initialTabId
            selectedTabId.isNotBlank() && tabs.any { it.id == selectedTabId } -> selectedTabId
            else -> tabs.first().id
        }
        selectedTabId = pick
        AuxTerminalTabsStore.setSelectedTabId(parentKey, pick)
    }

    val selectedTab = tabs.firstOrNull { it.id == selectedTabId } ?: tabs.first()

    val workspaceHost = remember(guestWorkspace) {
        com.termfold.app.shell.Projects.hostPath(context, guestWorkspace).absolutePath
    }
    val sessionKey = remember(folderId, workspaceId, acpSessionId, selectedTab.id) {
        TerminalHost.sessionKey(folderId, workspaceId, auxTerminalSessionId(acpSessionId, selectedTab.id))
    }
    val spec = remember(sessionKey, workspaceHost, selectedTab.title) {
        TerminalHost.Spec(
            workspace = workspaceHost.takeIf { it.isNotBlank() },
            initialCommand = "",
            title = selectedTab.title,
        )
    }

    val exited by TerminalHost.exited.collectAsStateWithLifecycle()
    val fontSize by TerminalHost.fontSize.collectAsStateWithLifecycle()
    val generation by TerminalHost.generation.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current
    var terminalView by remember { mutableStateOf<com.termux.view.TerminalView?>(null) }
    var controlDown by remember { mutableStateOf(false) }
    var altDown by remember { mutableStateOf(false) }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) TerminalHost.insertImage(context, uri)
    }

    LaunchedEffect(sessionKey, spec) {
        TerminalHost.ensureStarted(context, sessionKey, spec)
    }

    LaunchedEffect(sessionKey, generation, terminalView) {
        val view = terminalView ?: return@LaunchedEffect
        kotlinx.coroutines.delay(200)
        requestTerminalFocus(view, keyboard)
    }

    DisposableEffect(sessionKey) {
        val host = TerminalHost
        val tap = { requestTerminalFocus(terminalView, keyboard) }
        host.onTap = tap
        onDispose {
            if (host.onTap === tap) host.onTap = null
        }
    }

    fun dismiss() {
        TerminalHost.detachDisplay(sessionKey)
        onDismiss()
    }

    fun confirmRemoveTab(tab: AuxTerminalTab) {
        AuxTerminalTabsStore.closeTab(folderId, workspaceId, acpSessionId, tab.id)
        if (selectedTabId == tab.id) {
            selectedTabId = tabs.first().id
        }
    }

    pendingCloseTab?.let { tab ->
        AuxTerminalCloseConfirmDialog(
            onDismiss = { pendingCloseTab = null },
            onConfirm = {
                confirmRemoveTab(tab)
                pendingCloseTab = null
            },
        )
    }

    BackHandler(onBack = ::dismiss)

    Dialog(
        onDismissRequest = ::dismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        ConfigureDialogWindowForKeyboard()
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(Palette.Bg)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
            color = Palette.Bg,
        ) {
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
                        onClick = ::dismiss,
                    )
                    Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text(
                            stringResource(R.string.agent_aux_terminal_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = Palette.Text,
                        )
                        Text(
                            folderLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = Palette.TextFaint,
                            maxLines = 1,
                        )
                    }
                    if (exited) {
                        Text(
                            text = stringResource(R.string.action_restart),
                            color = Palette.Accent,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { TerminalHost.restart(context) }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val canCloseTabs = tabs.size > 1
                    tabs.forEach { tab ->
                        val selected = tab.id == selectedTabId
                        AuxTerminalTabChip(
                            title = tab.title,
                            selected = selected,
                            showClose = canCloseTabs,
                            onSelect = {
                                selectedTabId = tab.id
                                AuxTerminalTabsStore.setSelectedTabId(parentKey, tab.id)
                            },
                            onClose = { pendingCloseTab = tab },
                        )
                    }
                    BareIconButton(
                        icon = TermFoldIcons.Plus,
                        contentDescription = stringResource(R.string.agent_aux_terminal_new),
                        onClick = {
                            val tab = AuxTerminalTabsStore.addTab(parentKey)
                            if (tab != null) selectedTabId = tab.id
                        },
                        tint = Palette.Accent,
                        size = 36,
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    EmbeddedTerminal(
                        modifier = Modifier.fillMaxSize(),
                        fontSizeSp = fontSize,
                        onReady = { terminalView = it },
                    )
                }

                val wakeOn = TerminalHost.wakeEnabled(sessionKey)
                val autoOn = TerminalHost.wakeAuto(sessionKey)

                KeyRow(
                    controlDown = controlDown,
                    altDown = altDown,
                    wakeOn = wakeOn,
                    autoOn = autoOn,
                    onToggleWake = { TerminalHost.setWakeEnabled(context, sessionKey, !wakeOn) },
                    onToggleAuto = { TerminalHost.setWakeAuto(context, sessionKey, !autoOn) },
                    onToggleControl = {
                        controlDown = !controlDown
                        TerminalHost.currentBridge?.setControlKey(controlDown)
                    },
                    onToggleAlt = {
                        altDown = !altDown
                        TerminalHost.currentBridge?.setAltKey(altDown)
                    },
                    onKey = { key ->
                        if (key == SpecialKey.IMAGE) {
                            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            return@KeyRow
                        }
                        TerminalHost.currentBridge?.let { bridge ->
                            when (key) {
                                SpecialKey.ESC -> bridge.sendEscape()
                                SpecialKey.TAB -> bridge.sendTab()
                                SpecialKey.ENTER -> bridge.sendEnter()
                                SpecialKey.CTRL_C -> bridge.sendControl('C')
                                SpecialKey.CTRL_D -> bridge.sendControl('D')
                                SpecialKey.PASTE -> TerminalHost.paste(context)
                                SpecialKey.PIPE -> bridge.sendSequence("|")
                                SpecialKey.DASH -> bridge.sendSequence("-")
                                SpecialKey.SLASH -> bridge.sendSequence("/")
                                SpecialKey.UP -> bridge.sendSequence("\u001b[A")
                                SpecialKey.DOWN -> bridge.sendSequence("\u001b[B")
                                SpecialKey.RIGHT -> bridge.sendSequence("\u001b[C")
                                SpecialKey.LEFT -> bridge.sendSequence("\u001b[D")
                                SpecialKey.HOME -> bridge.sendSequence("\u001b[H")
                                SpecialKey.END -> bridge.sendSequence("\u001b[F")
                                SpecialKey.PAGE_UP -> bridge.sendSequence("\u001b[5~")
                                SpecialKey.PAGE_DOWN -> bridge.sendSequence("\u001b[6~")
                                SpecialKey.IMAGE -> Unit
                            }
                        }
                    },
                )
            }
        }
    }
}
