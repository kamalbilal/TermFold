package com.termfold.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.termfold.app.automation.AutomationGuardrailContext
import com.termfold.app.automation.AutomationGuardrails
import com.termfold.app.automation.AutomationLinkedPaths
import com.termfold.app.automation.AutomationPaths
import com.termfold.app.automation.AutomationProject
import com.termfold.app.automation.AutomationPromptAssembler
import com.termfold.app.automation.AutomationSession
import com.termfold.app.automation.AutomationSessionContext
import com.termfold.app.automation.AutomationStorage
import com.termfold.app.core.Folder
import com.termfold.app.core.Session
import com.termfold.app.gitworkspace.WorkspaceIds
import com.termfold.app.preview.BrowserBridge
import com.termfold.app.preview.Preview
import com.termfold.app.preview.engine.BrowserSessionRegistry
import java.io.File

@Composable
fun AutomationAgentSessionScreen(
    project: AutomationProject,
    session: AutomationSession,
    linkedFolder: Folder?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    filesOpen: Boolean = false,
    onToggleFiles: () -> Unit = {},
) {
    val context = LocalContext.current
    val sessionKey = AutomationSessionContext.key(project.id, session.id)
    DisposableEffect(project.id, session.id) {
        AutomationSessionContext.activeKey = sessionKey
        BrowserBridge.automationSessionKey = sessionKey
        BrowserBridge.writeBrowserConfig(context.applicationContext)
        Preview.webViewDataDirectorySuffix = sessionKey
        BrowserSessionRegistry.ensureAutomation(
            context.applicationContext,
            sessionKey,
            "auto-${session.id}",
        )
        AutomationStorage.ensureSessionTree(context, project.id, session.id)
        onDispose {
            if (AutomationSessionContext.activeKey == sessionKey) {
                AutomationSessionContext.activeKey = null
            }
            if (BrowserBridge.automationSessionKey == sessionKey) {
                BrowserBridge.automationSessionKey = null
            }
            if (Preview.webViewDataDirectorySuffix == sessionKey) {
                Preview.webViewDataDirectorySuffix = null
            }
            BrowserBridge.writeBrowserConfig(context.applicationContext)
            BrowserSessionRegistry.release(sessionKey)
        }
    }
    val hostDir = AutomationPaths.hostSessionDir(context, project.id, session.id)
    val constraintsHostFile = File(hostDir, "docs/constraints.md")
    val previewDir = AutomationLinkedPaths.previewProjectDir(context, project, session.id, linkedFolder)
    val readOnlyGuest = AutomationLinkedPaths.linkedGuestRoot(context, linkedFolder)
    val folder = Folder(
        id = project.id,
        name = project.name,
        path = hostDir.absolutePath,
        tint = project.tint,
        sessions = listOf(
            Session(
                id = session.id,
                name = session.name,
                command = "",
                tint = session.tint,
                acpAgentId = session.acpAgentId,
                workspaceId = WorkspaceIds.GOLDEN,
            ),
        ),
    )
    DisposableEffect(previewDir) {
        Preview.open(projectDir = previewDir)
        onDispose { }
    }
    CompositionLocalProvider(LocalAutomationReadOnlyGuestRoot provides readOnlyGuest) {
        AgentSessionScreen(
            folder = folder,
            sessionId = session.id,
            workspaceId = WorkspaceIds.GOLDEN,
            agentId = session.acpAgentId,
            onBack = onBack,
            modifier = modifier,
            filesOpen = filesOpen,
            onToggleFiles = onToggleFiles,
            automationGuardrails = {
                AutomationGuardrailContext(
                    policy = AutomationGuardrails.policyFromConstraintsFile(constraintsHostFile),
                    constraintsFile = constraintsHostFile,
                )
            },
            automationPromptPrefix = {
                AutomationPromptAssembler.assemble(
                    context = context,
                    projectId = project.id,
                    sessionId = session.id,
                    linkedFolder = linkedFolder,
                    currentHost = Preview.state.value.url?.substringAfter("://")?.substringBefore('/'),
                )
            },
            previewProjectDirOverride = previewDir,
            automationReadOnlyGuestRoot = readOnlyGuest,
            automationSessionKey = sessionKey,
        )
    }
}
