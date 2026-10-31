package com.termfold.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import com.termfold.app.ui.screens.PreviewPane
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.termfold.app.core.AppViewModel
import com.termfold.app.core.SessionPreset
import com.termfold.app.core.ShellState
import com.termfold.app.shell.TerminalHost
import com.termfold.app.ui.components.BottomNav
import com.termfold.app.ui.components.NavRail
import com.termfold.app.ui.components.currentWindowWidth
import com.termfold.app.ui.components.isWide
import com.termfold.app.ui.components.AppSurface
import com.termfold.app.ui.components.NavTab
import com.termfold.app.ui.screens.AgentSessionScreen
import com.termfold.app.ui.screens.ConfirmDialog
import com.termfold.app.ui.screens.FileManagerScreen
import com.termfold.app.ui.screens.FilesWorkspace
import com.termfold.app.ui.screens.LocalAutomationReadOnlyGuestRoot
import com.termfold.app.ui.screens.LocalPmViewBranchLabel
import com.termfold.app.ui.screens.LocalWorkspaceEditorOpen
import com.termfold.app.ui.screens.FolderDetailScreen
import com.termfold.app.automation.AutomationPaths
import com.termfold.app.core.Folder
import com.termfold.app.core.Session
import com.termfold.app.gitworkspace.WorkspaceIds
import com.termfold.app.ui.screens.AutomationAgentSessionScreen
import com.termfold.app.ui.screens.AutomationProjectDetailScreen
import com.termfold.app.ui.screens.TasksScreen
import com.termfold.app.ui.screens.WorkflowsScreen
import com.termfold.app.ui.screens.WorkflowsSection
import com.termfold.app.ui.screens.WorkflowsSectionScreen
import com.termfold.app.ui.screens.FoldersScreen
import com.termfold.app.ui.screens.OptionsSheet
import com.termfold.app.ui.screens.PresetChips
import com.termfold.app.ui.screens.ProvisioningScreen
import com.termfold.app.ui.screens.SearchField
import com.termfold.app.ui.screens.SettingsScreen
import com.termfold.app.ui.screens.StartupCommandsDialog
import com.termfold.app.ui.screens.TerminalScreen
import com.termfold.app.ui.screens.TextPromptDialog
import com.termfold.app.ui.theme.Palette
import com.termfold.app.ui.theme.TermFoldTheme
import com.termfold.app.ui.theme.Motion

class MainActivity : TermFoldActivity() {

    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Creating the channels while on screen is what makes Android 13+ ask for the
        // notification permission.
        com.termfold.app.notify.Notifier.ensureChannels(this)
        com.termfold.app.notify.Notifier.placeFrom(intent)?.let(com.termfold.app.notify.AppPresence::request)

        setContent {
            TermFoldTheme {
                TermFoldRoot(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        com.termfold.app.notify.Notifier.placeFrom(intent)?.let(com.termfold.app.notify.AppPresence::request)
    }

    override fun onStart() {
        super.onStart()
        // Back in the full app: the bubble would only be a second copy of it.
        com.termfold.app.notify.Notifier.cancelBubble(this)
    }

    /**
     * The user is leaving for another app (Home, Recents, a gesture), not opening one of ours
     * like the photo picker. If they were working in a folder or a session, the app floats on as
     * a bubble that opens right there.
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val place = com.termfold.app.notify.AppPresence.place ?: return
        com.termfold.app.notify.Notifier.showBubble(this, place, com.termfold.app.notify.AppPresence.label)
    }
}

/**
 * What every TermFold window shares: the physical-keyboard handling. The main window and the
 * bubble both extend it.
 */
open class TermFoldActivity : ComponentActivity() {

    /**
     * The first key typed on a physical keyboard puts the on-screen one away: it is covering the
     * terminal and nobody is using it. It comes back as soon as the user taps a text field or the
     * terminal, which is them choosing touch again (see [PhysicalKeyboard]).
     */
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        val wasInUse = com.termfold.app.ui.components.PhysicalKeyboard.inUse
        if (event.action == android.view.KeyEvent.ACTION_DOWN &&
            com.termfold.app.ui.components.PhysicalKeyboard.onKeyEvent(event) && !wasInUse
        ) {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
            imm?.hideSoftInputFromWindow(window.decorView.windowToken, 0)
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
        com.termfold.app.ui.components.PhysicalKeyboard.onTouch(event)
        return super.dispatchTouchEvent(event)
    }
}


/** Destinations. Plain state is enough for a graph this shallow. */
private sealed interface Destination {
    data object Tabs : Destination
    data class Folder(val folderId: String) : Destination
    data class Terminal(
        val folderId: String,
        val sessionId: String,
        val workspaceId: String = com.termfold.app.gitworkspace.WorkspaceIds.GOLDEN,
    ) : Destination
    data class WorkflowsSection(val section: com.termfold.app.ui.screens.WorkflowsSection) : Destination
    data class AutomationProject(val projectId: String) : Destination
    data class AutomationSession(val projectId: String, val sessionId: String) : Destination
    data class PrReviewSession(val sessionId: String) : Destination
    data class PmSession(val sessionId: String) : Destination
    data class PdfToolsSession(val sessionId: String) : Destination
}

/** How deep a destination sits, used to give the transitions a consistent direction. */
private fun Destination.depth(): Int = when (this) {
    Destination.Tabs -> 0
    is Destination.Folder -> 1
    is Destination.Terminal -> 2
    is Destination.WorkflowsSection -> 1
    is Destination.AutomationProject -> 2
    is Destination.AutomationSession -> 3
    is Destination.PrReviewSession -> 2
    is Destination.PmSession -> 2
    is Destination.PdfToolsSession -> 2
}

/**
 * The shared screen transition: a short directional slide with a fade. Moving deeper into the app
 * enters from the right; coming back out reverses it, so navigation reads as spatial movement.
 * [rankOf] maps each value to its position in that navigation depth, e.g. tabs before folder
 * before terminal.
 */
private fun <T> directionalTransition(rankOf: (T) -> Int): AnimatedContentTransitionScope<T>.() -> ContentTransform = {
    val forward = rankOf(targetState) > rankOf(initialState)
    val enter = slideInHorizontally(Motion.slow()) { full ->
        if (forward) full / 4 else -full / 4
    } + fadeIn(Motion.standard())
    val exit = slideOutHorizontally(Motion.slow()) { full ->
        if (forward) -full / 4 else full / 4
    } + fadeOut(Motion.quick())
    enter togetherWith exit
}

/** What an options sheet or confirmation is acting on. */
private sealed interface OptionsTarget {
    val folderId: String

    data class Folder(override val folderId: String) : OptionsTarget
    data class Session(override val folderId: String, val sessionId: String) : OptionsTarget
}

private sealed interface AutomationOptionsTarget {
    val projectId: String

    data class Project(override val projectId: String) : AutomationOptionsTarget
    data class Session(override val projectId: String, val sessionId: String) : AutomationOptionsTarget
}

private sealed interface WorkflowSessionOptionsTarget {
    data class PrReview(val sessionId: String) : WorkflowSessionOptionsTarget
    data class Pm(val sessionId: String) : WorkflowSessionOptionsTarget
    data class PdfTools(val sessionId: String) : WorkflowSessionOptionsTarget
}

@Composable
internal fun TermFoldRoot(viewModel: AppViewModel) {
    val data by viewModel.folders.collectAsStateWithLifecycle()
    val automationData by viewModel.automation.collectAsStateWithLifecycle()
    val prReviewData by viewModel.prReviews.collectAsStateWithLifecycle()
    val prReviewJob by viewModel.prReviewJob.collectAsStateWithLifecycle()
    val pmData by viewModel.pmSessions.collectAsStateWithLifecycle()
    val pmJob by viewModel.pmJob.collectAsStateWithLifecycle()
    val pdfToolsData by viewModel.pdfTools.collectAsStateWithLifecycle()
    val pdfToolsJob by viewModel.pdfToolsJob.collectAsStateWithLifecycle()
    val pdfToolsLibrariesReady by viewModel.pdfToolsLibrariesReady.collectAsStateWithLifecycle()
    val tasksData by viewModel.tasks.collectAsStateWithLifecycle()
    val startupCommandsData by viewModel.startupCommands.collectAsStateWithLifecycle()
    val startupCommandsRunning by viewModel.startupCommandsRunning.collectAsStateWithLifecycle()
    val startupCommandLogs by viewModel.startupCommandLogs.collectAsStateWithLifecycle()
    val shell by viewModel.shell.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var destination by remember { mutableStateOf<Destination>(Destination.Tabs) }

    // A notification or the bubble asked to show a folder or session.
    val request by com.termfold.app.notify.AppPresence.requests.collectAsStateWithLifecycle()
    LaunchedEffect(request, data) {
        val place = request ?: return@LaunchedEffect
        val folder = data.folder(place.folderId) ?: return@LaunchedEffect
        destination = if (place.sessionId != null && folder.sessions.any { it.id == place.sessionId }) {
            Destination.Terminal(folder.id, place.sessionId, place.workspaceId)
        } else {
            Destination.Folder(folder.id)
        }
        com.termfold.app.notify.AppPresence.consume(place)
    }
    // Tell the rest of the app where the user is, for notifications and the bubble.
    LaunchedEffect(destination, data) {
        val presence = com.termfold.app.notify.AppPresence
        when (val d = destination) {
            is Destination.Folder -> {
                presence.place = com.termfold.app.notify.AppPresence.Place(d.folderId)
                presence.label = data.folder(d.folderId)?.name.orEmpty()
            }
            is Destination.Terminal -> {
                presence.place = com.termfold.app.notify.AppPresence.Place(
                    d.folderId,
                    d.sessionId,
                    d.workspaceId,
                )
                val folder = data.folder(d.folderId)
                val session = folder?.sessions?.firstOrNull { it.id == d.sessionId }
                presence.label = listOfNotNull(session?.name, folder?.name).joinToString(" \u00B7 ")
                val key = com.termfold.app.gitworkspace.SessionKeys.compose(d.folderId, d.workspaceId, d.sessionId)
                com.termfold.app.notify.Notifier.cancelFor(context, key)
            }
            else -> presence.place = null
        }
    }
    var filesOpen by rememberSaveable { mutableStateOf(false) }
    val preview by com.termfold.app.preview.Preview.state.collectAsStateWithLifecycle()
    val gitChanges by com.termfold.app.gitworkspace.GitChanges.state.collectAsStateWithLifecycle()
    // On a tablet the preview sits beside the work; this is its share of the width.
    var previewFraction by rememberSaveable { mutableFloatStateOf(0.5f) }
    var previewMax by rememberSaveable { mutableStateOf(false) }
    var tab by remember { mutableStateOf(NavTab.FOLDERS) }
    var newProject by remember { mutableStateOf(false) }
    var newAutomationProject by remember { mutableStateOf(false) }
    var newAutomationSessionFor by remember { mutableStateOf<String?>(null) }
    val job by viewModel.job.collectAsStateWithLifecycle()
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var startupCommandsOpen by remember { mutableStateOf(false) }

    var optionsFor by remember { mutableStateOf<OptionsTarget?>(null) }
    var renameFolderId by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<OptionsTarget?>(null) }
    var automationOptionsFor by remember { mutableStateOf<AutomationOptionsTarget?>(null) }
    var automationConfirmDelete by remember { mutableStateOf<AutomationOptionsTarget?>(null) }
    var workflowSessionOptionsFor by remember { mutableStateOf<WorkflowSessionOptionsTarget?>(null) }
    var workflowSessionConfirmDelete by remember { mutableStateOf<WorkflowSessionOptionsTarget?>(null) }
    var addSessionFor by remember { mutableStateOf<String?>(null) }
    var pendingCursorAdd by remember { mutableStateOf<PendingCursorAdd?>(null) }
    var cursorBranchPick by remember { mutableStateOf(false) }
    var pendingCursorProvision by remember { mutableStateOf<PendingCursorProvision?>(null) }

    val pickFolder = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri: Uri? ->
            if (uri != null) viewModel.importProject(uri)
            searchOpen = false
        },
    )

    // The bundled Linux environment is unpacked once, lazily, the first time the app runs. It
    // costs a few seconds and is then never repeated.
    LaunchedEffect(Unit) { viewModel.ensureShellReady() }

    // A new release is looked for in the background (at most twice a day); Settings shows a dot.
    LaunchedEffect(Unit) { com.termfold.app.update.UpdateManager.checkIfDue(context) }

    // Projects are folders in ~/projects; the list follows that folder (a project made with
    // mkdir in a shell appears, a deleted one goes), checked on start and on every return.
    LaunchedEffect(shell.state) {
        viewModel.syncProjects()
        // Agents in the Linux environment can use the preview browser from now on.
        if (shell.state == ShellState.READY) com.termfold.app.preview.BrowserBridge.start(context)
    }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) viewModel.syncProjects()
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }


    when (shell.state) {
        ShellState.UNKNOWN, ShellState.PREPARING -> {
            AppSurface {
                ProvisioningScreen(
                    step = shell.step,
                    progress = shell.fraction,
                    error = null,
                    onRetry = { viewModel.retryShell() },
                )
            }
            return
        }

        ShellState.FAILED -> {
            AppSurface {
                ProvisioningScreen(
                    step = shell.step,
                    progress = shell.fraction,
                    error = shell.error,
                    onRetry = { viewModel.retryShell() },
                )
            }
            return
        }

        ShellState.MIGRATE_OFFER -> {
            AppSurface {
                com.termfold.app.ui.screens.MigrationOfferScreen(
                    onMigrate = { viewModel.startMigration() },
                    onLater = { viewModel.postponeMigration() },
                )
            }
            return
        }

        ShellState.MIGRATING -> {
            AppSurface {
                ProvisioningScreen(
                    step = shell.step,
                    progress = shell.fraction,
                    error = null,
                    onRetry = { viewModel.retryShell() },
                    title = stringResource(R.string.migrate_working_title),
                    body = stringResource(R.string.migrate_working_body),
                )
            }
            return
        }

        ShellState.READY, ShellState.UNSUPPORTED -> Unit
    }

    val windowWidth = currentWindowWidth()
    // Animations are slightly slower on a tablet, where surfaces are bigger and travel further.
    androidx.compose.runtime.SideEffect { Motion.configure(windowWidth.isWide) }

    // Phone and tablet share one tree; only the navigation container and the folder presentation
    // differ, so there is no second layout to keep in sync.
    // Web links in chats open in the preview (which can hand them on to a browser); mail and
    // other links go to the system as before.
    val systemUriHandler = LocalUriHandler.current
    val uriHandler = remember(systemUriHandler) {
        object : UriHandler {
            override fun openUri(uri: String) {
                if (uri.startsWith("http://", ignoreCase = true) || uri.startsWith("https://", ignoreCase = true)) {
                    com.termfold.app.preview.Preview.open(uri)
                } else {
                    runCatching { systemUriHandler.openUri(uri) }
                }
            }
        }
    }
    CompositionLocalProvider(LocalUriHandler provides uriHandler) {
    AppSurface {
        // Opaque and hit-testable, so a minimized browser parked underneath stays hidden and
        // never receives a touch meant for the app.
        Row(modifier = Modifier.fillMaxSize().background(Palette.Bg).pointerInput(Unit) {}) {
            if (windowWidth.isWide) {
                // The rail is visible over every destination, so picking a tab must also leave
                // whatever folder or terminal screen is open — setting the tab alone would change
                // nothing, because the destination branch is what the content area displays.
                NavRail(
                    selected = tab,
                    onSelect = { selected ->
                        tab = selected
                        if (selected != NavTab.FOLDERS) searchOpen = false
                        destination = Destination.Tabs
                    },
                )
            }
            // The folder being worked in, if any: its files can be shown beside the folder view,
            // a chat or a shell (not on the home screen).
            val workspaceFolder = when (val d = destination) {
                is Destination.Folder -> data.folder(d.folderId)
                is Destination.Terminal -> data.folder(d.folderId)
                is Destination.AutomationSession -> {
                    automationData.session(d.projectId, d.sessionId)?.let { (project, session) ->
                        Folder(
                            id = project.id,
                            name = project.name,
                            path = AutomationPaths.hostSessionDir(context, project.id, session.id).absolutePath,
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
                    }
                }
                is Destination.PmSession -> {
                    val pm = pmData.session(d.sessionId)
                    val coding = pm?.folderId?.let { data.folder(it) }
                    coding ?: pm?.let { session ->
                        Folder(
                            id = "pm-${session.id}",
                            name = session.name,
                            path = com.termfold.app.pm.PmPaths.hostSessionDir(context, session.id).absolutePath,
                            tint = session.tint,
                            sessions = listOf(
                                Session(
                                    id = session.id,
                                    name = session.name,
                                    command = "",
                                    tint = session.tint,
                                    acpAgentId = session.acpAgentId,
                                    workspaceId = session.workspaceId
                                        ?: com.termfold.app.gitworkspace.WorkspaceIds.GOLDEN,
                                ),
                            ),
                        )
                    }
                }
                is Destination.PrReviewSession -> {
                    val review = prReviewData.session(d.sessionId)
                    val coding = review?.folderId?.let { data.folder(it) }
                    coding ?: review?.let { session ->
                        Folder(
                            id = "pr-review-${session.id}",
                            name = session.name,
                            path = com.termfold.app.prreview.PrReviewPaths.hostSessionDir(context, session.id).absolutePath,
                            tint = session.tint,
                            sessions = listOf(
                                Session(
                                    id = session.id,
                                    name = session.name,
                                    command = "",
                                    tint = session.tint,
                                    acpAgentId = session.acpAgentId,
                                    workspaceId = session.workspaceId ?: WorkspaceIds.GOLDEN,
                                ),
                            ),
                        )
                    }
                }
                is Destination.PdfToolsSession -> {
                    pdfToolsData.session(d.sessionId)?.let { session ->
                        Folder(
                            id = "pdf-tools-${session.id}",
                            name = session.name,
                            path = com.termfold.app.pdftools.PdfToolsPaths.hostSessionDir(context, session.id).absolutePath,
                            tint = session.tint,
                            sessions = listOf(
                                Session(
                                    id = session.id,
                                    name = session.name,
                                    command = "",
                                    tint = session.tint,
                                    acpAgentId = session.acpAgentId,
                                    workspaceId = "pdf-stub",
                                ),
                            ),
                        )
                    }
                }
                else -> null
            }
            val filesRootHostPath = when (val d = destination) {
                is Destination.Terminal -> {
                    if (d.workspaceId == com.termfold.app.gitworkspace.WorkspaceIds.GOLDEN) {
                        null
                    } else {
                        workspaceFolder?.let { folder ->
                            com.termfold.app.gitworkspace.WorkspaceStore.guestPath(context, folder, d.workspaceId)
                                ?.let { guest ->
                                    com.termfold.app.shell.Projects.hostPath(context, guest).absolutePath
                                }
                        }
                    }
                }
                is Destination.PrReviewSession -> {
                    val review = prReviewData.session(d.sessionId)
                    val coding = review?.folderId?.let { data.folder(it) }
                    val copyGuest = if (review?.workspaceId != null && coding != null) {
                        com.termfold.app.gitworkspace.WorkspaceStore.guestPath(context, coding, review.workspaceId)
                    } else {
                        null
                    }
                    val guest = copyGuest ?: review?.let {
                        com.termfold.app.prreview.PrReviewPaths.sessionGuest(it.id)
                    }
                    guest?.let { com.termfold.app.shell.Projects.hostPath(context, it).absolutePath }
                }
                is Destination.PmSession -> {
                    val pm = pmData.session(d.sessionId)
                    val coding = pm?.folderId?.let { data.folder(it) }
                    when {
                        pm != null && coding != null && pm.mode == com.termfold.app.pm.PmMode.DISCOVERY -> {
                            com.termfold.app.gitworkspace.WorkspaceStore.goldenGuestPath(context, coding)
                                ?.let { com.termfold.app.shell.Projects.hostPath(context, it).absolutePath }
                        }
                        pm != null && coding != null && pm.workspaceId != null -> {
                            com.termfold.app.gitworkspace.WorkspaceStore.guestPath(context, coding, pm.workspaceId)
                                ?.let { com.termfold.app.shell.Projects.hostPath(context, it).absolutePath }
                        }
                        else -> pm?.let {
                            com.termfold.app.pm.PmPaths.hostSessionDir(context, it.id).absolutePath
                        }
                    }
                }
                is Destination.PdfToolsSession ->
                    pdfToolsData.session(d.sessionId)?.let {
                        com.termfold.app.pdftools.PdfToolsPaths.hostSessionDir(context, it.id).absolutePath
                    }
                else -> null
            }
            val sessionWorkspaceFilesScope = when (val d = destination) {
                is Destination.Terminal ->
                    d.workspaceId != com.termfold.app.gitworkspace.WorkspaceIds.GOLDEN
                is Destination.PrReviewSession -> true
                is Destination.PmSession ->
                    pmData.session(d.sessionId)?.mode == com.termfold.app.pm.PmMode.RELEASE &&
                        pmData.session(d.sessionId)?.workspaceId != null
                else -> false
            }
            val pmFilesContext = when (val d = destination) {
                is Destination.PmSession -> {
                    val pm = pmData.session(d.sessionId)
                    val coding = pm?.folderId?.let { data.folder(it) }
                    val goldenGuest = if (pm?.mode == com.termfold.app.pm.PmMode.DISCOVERY && coding != null) {
                        com.termfold.app.gitworkspace.WorkspaceStore.goldenGuestPath(context, coding)
                    } else {
                        null
                    }
                    val viewBranch = if (pm?.mode == com.termfold.app.pm.PmMode.DISCOVERY) pm.viewBranch else null
                    goldenGuest to viewBranch
                }
                else -> null to null
            }
            // A phone's drawer closes when the screen changes; a tablet keeps the tree docked.
            LaunchedEffect(destination, windowWidth.isWide) {
                if (!windowWidth.isWide) filesOpen = false
            }
            // Opaque and hit-testable too: a minimized side browser is parked beneath this.
            Box(modifier = Modifier.weight(1f).background(Palette.Bg).pointerInput(Unit) {}) {
              androidx.compose.runtime.CompositionLocalProvider(
                LocalAutomationReadOnlyGuestRoot provides pmFilesContext.first,
                LocalPmViewBranchLabel provides pmFilesContext.second,
              ) {
              FilesWorkspace(
                folder = workspaceFolder,
                filesOpen = filesOpen,
                onCloseFiles = { filesOpen = false },
                wide = windowWidth.isWide,
                filesRootHostPath = filesRootHostPath,
                sessionWorkspaceScope = sessionWorkspaceFilesScope,
              ) {
                AnimatedContent(
                    targetState = destination,
                    transitionSpec = directionalTransition { it.depth() },
                    label = "destination",
                ) { dest ->
                when (val current = dest) {
                    Destination.Tabs -> Column(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .windowInsetsPadding(
                                    WindowInsets.safeDrawing.only(
                                        WindowInsetsSides.Top + WindowInsetsSides.End
                                    )
                                ),
                        ) {
                            AnimatedContent(
                                targetState = tab,
                                transitionSpec = directionalTransition { it.ordinal },
                                label = "tab",
                            ) { currentTab ->
                            when (currentTab) {
                                NavTab.FOLDERS -> FoldersScreen(
                                    folders = data.folders,
                                    query = query,
                                    onQueryChange = { query = it },
                                    onOpenFolder = { destination = Destination.Folder(it) },
                                    onAddFolder = { newProject = true },
                                    onSearchToggle = {
                                        searchOpen = !searchOpen
                                        if (!searchOpen) query = ""
                                    },
                                    searchOpen = searchOpen,
                                    onStartupCommands = { startupCommandsOpen = true },
                                    onMore = { tab = NavTab.SETTINGS },
                                    useGrid = windowWidth.isWide,
                                    modifier = Modifier.fillMaxSize(),
                                )

                                NavTab.WORKFLOWS -> WorkflowsScreen(
                                    onOpenSection = { section ->
                                        destination = Destination.WorkflowsSection(section)
                                    },
                                    useGrid = windowWidth.isWide,
                                    modifier = Modifier.fillMaxSize(),
                                )

                                NavTab.TASKS -> TasksScreen(
                                    folders = data.folders,
                                    tasks = tasksData.tasks,
                                    wide = windowWidth.isWide,
                                    onCreateTask = { dayKey, folderId, title, description, status, uris ->
                                        viewModel.createTask(dayKey, folderId, title, description, status, uris)
                                    },
                                    onUpdateTask = { id, dayKey, folderId, title, description, status, uris ->
                                        viewModel.updateTask(id, dayKey, folderId, title, description, status, uris)
                                    },
                                    onDeleteTask = viewModel::deleteTask,
                                    onSetStatus = viewModel::setTaskStatus,
                                    modifier = Modifier.fillMaxSize(),
                                )

                                NavTab.SETTINGS -> SettingsScreen(
                                    shellState = shell.state,
                                    folderCount = data.folders.size,
                                    sessionCount = data.folders.sumOf { it.sessions.size },
                                    onRepairShell = { viewModel.retryShell() },
                                    onMigrateShell = { viewModel.offerMigration() },
                                    viewModel = viewModel,
                                    wide = windowWidth.isWide,
                                )
                            }
                            }
                        }

                        if (!windowWidth.isWide) {
                            BottomNav(
                                selected = tab,
                                onSelect = { selected ->
                                    tab = selected
                                    if (selected != NavTab.FOLDERS) searchOpen = false
                                    destination = Destination.Tabs
                                },
                            )
                        }
                    }

                is Destination.Folder -> {
                    val folder = data.folder(current.folderId)
                    if (folder == null) {
                        LaunchedEffect(current.folderId) { destination = Destination.Tabs }
                    } else {
                        val workspaceEditorOpen = LocalWorkspaceEditorOpen.current
                        BackHandler(enabled = !workspaceEditorOpen) { destination = Destination.Tabs }
                        FolderDetailScreen(
                            folder = folder,
                            wide = windowWidth.isWide,
                            modifier = Modifier.windowInsetsPadding(
                                WindowInsets.safeDrawing.only(
                                    WindowInsetsSides.Top + WindowInsetsSides.End
                                )
                            ),
                            onBack = { destination = Destination.Tabs },
                            filesOpen = filesOpen,
                            onToggleFiles = { filesOpen = !filesOpen },
                            onOpenSession = { sessionId ->
                                val ws = folder.sessions.firstOrNull { it.id == sessionId }?.workspaceId
                                    ?: com.termfold.app.gitworkspace.WorkspaceIds.GOLDEN
                                destination = Destination.Terminal(folder.id, sessionId, ws)
                            },
                            onAddSession = { addSessionFor = folder.id },
                            onRenameFolder = { renameFolderId = folder.id },
                            onOpenOptions = { sessionId ->
                                optionsFor = if (sessionId == null) {
                                    OptionsTarget.Folder(folder.id)
                                } else {
                                    OptionsTarget.Session(folder.id, sessionId)
                                }
                            },
                        )
                    }
                }

                is Destination.WorkflowsSection -> {
                    BackHandler {
                        tab = NavTab.WORKFLOWS
                        destination = Destination.Tabs
                    }
                    WorkflowsSectionScreen(
                        section = current.section,
                        projects = automationData.projects,
                        codingFolders = data.folders,
                        prReviews = prReviewData.sessions,
                        prReviewError = prReviewJob?.error,
                        pmSessions = pmData.sessions,
                        pmError = pmJob?.error,
                        onBack = {
                            tab = NavTab.WORKFLOWS
                            destination = Destination.Tabs
                        },
                        onOpenProject = { destination = Destination.AutomationProject(it) },
                        onOpenProjectOptions = { projectId ->
                            automationOptionsFor = AutomationOptionsTarget.Project(projectId)
                        },
                        onAddProject = { newAutomationProject = true },
                        onOpenPrReview = { destination = Destination.PrReviewSession(it) },
                        onOpenPrReviewOptions = { sessionId ->
                            workflowSessionOptionsFor = WorkflowSessionOptionsTarget.PrReview(sessionId)
                        },
                        onStartPrReview = { url ->
                            viewModel.bindPrReview(url) { id ->
                                destination = Destination.PrReviewSession(id)
                            }
                        },
                        onOpenPmSession = { destination = Destination.PmSession(it) },
                        onOpenPmSessionOptions = { sessionId ->
                            workflowSessionOptionsFor = WorkflowSessionOptionsTarget.Pm(sessionId)
                        },
                        onStartPmDiscovery = { folderId, name ->
                            viewModel.startPmDiscovery(folderId, name)
                        },
                        onStartPmRelease = { folderId, branch, name, label ->
                            viewModel.startPmRelease(folderId, branch, name, label)
                        },
                        pdfToolsSessions = pdfToolsData.sessions,
                        pdfToolsError = pdfToolsJob?.let { job ->
                            job.error ?: job.step.takeIf { it.isNotBlank() && job.doneSessionId == null }
                        },
                        pdfToolsLibrariesReady = pdfToolsLibrariesReady,
                        onOpenPdfToolsSession = { destination = Destination.PdfToolsSession(it) },
                        onOpenPdfToolsSessionOptions = { sessionId ->
                            workflowSessionOptionsFor = WorkflowSessionOptionsTarget.PdfTools(sessionId)
                        },
                        onStartPdfInspect = {
                            viewModel.createPdfToolsSession(
                                com.termfold.app.pdftools.PdfToolsJobType.INSPECT,
                                "Inspect",
                            ) { id -> destination = Destination.PdfToolsSession(id) }
                        },
                        onStartPdfGenerate = {
                            viewModel.createPdfToolsSession(
                                com.termfold.app.pdftools.PdfToolsJobType.GENERATE,
                                "Generate",
                            ) { id -> destination = Destination.PdfToolsSession(id) }
                        },
                        onInstallPdfTools = { viewModel.installPdfToolsLibraries() },
                        onPdfToolkit = { op, files, authz, extra ->
                            viewModel.runPdfToolkit(op, files, extra = extra, authz = authz)
                        },
                        useGrid = windowWidth.isWide,
                        modifier = Modifier.windowInsetsPadding(
                            WindowInsets.safeDrawing.only(
                                WindowInsetsSides.Top + WindowInsetsSides.End,
                            ),
                        ),
                    )
                }

                is Destination.AutomationProject -> {
                    val project = automationData.project(current.projectId)
                    if (project == null) {
                        LaunchedEffect(current.projectId) {
                            destination = Destination.WorkflowsSection(WorkflowsSection.AUTOMATION)
                        }
                    } else {
                        BackHandler {
                            tab = NavTab.WORKFLOWS
                            destination = Destination.WorkflowsSection(WorkflowsSection.AUTOMATION)
                        }
                        AutomationProjectDetailScreen(
                            project = project,
                            codingFolders = data.folders,
                            onBack = {
                                tab = NavTab.WORKFLOWS
                                destination = Destination.WorkflowsSection(WorkflowsSection.AUTOMATION)
                            },
                            onOpenSession = { sessionId ->
                                destination = Destination.AutomationSession(project.id, sessionId)
                            },
                            onOpenSessionOptions = { sessionId ->
                                automationOptionsFor = AutomationOptionsTarget.Session(project.id, sessionId)
                            },
                            onOpenProjectOptions = {
                                automationOptionsFor = AutomationOptionsTarget.Project(project.id)
                            },
                            onAddSession = { newAutomationSessionFor = project.id },
                            onLinkFolder = { folderId -> viewModel.linkAutomationFolder(project.id, folderId) },
                            wide = windowWidth.isWide,
                            modifier = Modifier.windowInsetsPadding(
                                WindowInsets.safeDrawing.only(
                                    WindowInsetsSides.Top + WindowInsetsSides.End,
                                ),
                            ),
                        )
                    }
                }

                is Destination.PrReviewSession -> {
                    val session = prReviewData.session(current.sessionId)
                    if (session == null) {
                        LaunchedEffect(current.sessionId) {
                            destination = Destination.WorkflowsSection(WorkflowsSection.PR_REVIEWER)
                        }
                    } else {
                        val coding = data.folder(session.folderId)
                        BackHandler {
                            tab = NavTab.WORKFLOWS
                            destination = Destination.WorkflowsSection(WorkflowsSection.PR_REVIEWER)
                        }
                        com.termfold.app.ui.screens.PrReviewAgentSessionScreen(
                            session = session,
                            codingFolder = coding,
                            onBack = {
                                tab = NavTab.WORKFLOWS
                                destination = Destination.WorkflowsSection(WorkflowsSection.PR_REVIEWER)
                            },
                            onDoneKind = { viewModel.reviewDoneKind(it) },
                            onApplyDone = { viewModel.applyReviewDone(it) },
                            onPasteAnother = { url ->
                                viewModel.bindPrReview(url, existingSessionId = session.id) { id ->
                                    destination = Destination.PrReviewSession(id)
                                }
                            },
                            onSaveParse = { userMessageId, parse ->
                                viewModel.saveReviewParse(session.id, userMessageId, parse)
                            },
                            onSyncReportsToTimeline = { items, reports ->
                                viewModel.syncPrReviewReportsToTimeline(session.id, items, reports)
                            },
                            filesOpen = filesOpen,
                            onToggleFiles = { filesOpen = !filesOpen },
                            modifier = Modifier.windowInsetsPadding(
                                WindowInsets.safeDrawing.only(
                                    WindowInsetsSides.Top + WindowInsetsSides.End,
                                ),
                            ),
                        )
                    }
                }

                is Destination.PmSession -> {
                    val session = pmData.session(current.sessionId)
                    if (session == null) {
                        LaunchedEffect(current.sessionId) {
                            destination = Destination.WorkflowsSection(WorkflowsSection.PRODUCT_MANAGER)
                        }
                    } else {
                        val coding = data.folder(session.folderId)
                        BackHandler {
                            tab = NavTab.WORKFLOWS
                            destination = Destination.WorkflowsSection(WorkflowsSection.PRODUCT_MANAGER)
                        }
                        com.termfold.app.ui.screens.PmAgentSessionScreen(
                            session = session,
                            codingFolder = coding,
                            onBack = {
                                tab = NavTab.WORKFLOWS
                                destination = Destination.WorkflowsSection(WorkflowsSection.PRODUCT_MANAGER)
                            },
                            onUpdateSession = { viewModel.updatePmSession(it) },
                            onDoneKind = { viewModel.pmDoneKind(it) },
                            onApplyDone = { viewModel.applyPmDone(it) },
                            onRefreshResearch = { id, showProgress ->
                                viewModel.refreshPmResearch(id, showProgress = showProgress)
                            },
                            onSetViewBranch = { id, branch ->
                                viewModel.setPmViewBranch(id, branch)
                            },
                            filesOpen = filesOpen,
                            onToggleFiles = { filesOpen = !filesOpen },
                            modifier = Modifier.windowInsetsPadding(
                                WindowInsets.safeDrawing.only(
                                    WindowInsetsSides.Top + WindowInsetsSides.End,
                                ),
                            ),
                        )
                    }
                }

                is Destination.PdfToolsSession -> {
                    val session = pdfToolsData.session(current.sessionId)
                    if (session == null) {
                        LaunchedEffect(current.sessionId) {
                            destination = Destination.WorkflowsSection(WorkflowsSection.PDF_TOOLS)
                        }
                    } else {
                        BackHandler {
                            tab = NavTab.WORKFLOWS
                            destination = Destination.WorkflowsSection(WorkflowsSection.PDF_TOOLS)
                        }
                        com.termfold.app.ui.screens.PdfToolsAgentSessionScreen(
                            session = session,
                            onBack = {
                                tab = NavTab.WORKFLOWS
                                destination = Destination.WorkflowsSection(WorkflowsSection.PDF_TOOLS)
                            },
                            onUpdate = { viewModel.updatePdfToolsSession(it) },
                            onImportFile = { file, password -> viewModel.importPdfToSession(session.id, file, password) },
                            onGenerate = { fields -> viewModel.generatePdfSession(session.id, fields) },
                            onShare = {
                                val dir = com.termfold.app.pdftools.PdfToolsPaths.hostSessionDir(context, session.id)
                                val pdf = com.termfold.app.pdftools.PdfToolsPaths.outputPdf(dir)
                                val report = com.termfold.app.pdftools.PdfToolsPaths.reportFile(dir)
                                val inspect = com.termfold.app.pdftools.PdfToolsPaths.inspectFile(dir)
                                val files = listOf(pdf, report, inspect).filter { it.isFile }
                                if (files.isNotEmpty()) {
                                    com.termfold.app.files.FileActions.share(context, files)
                                }
                            },
                            pendingPlanJson = com.termfold.app.pdftools.PdfToolsSessionContext.pendingPlanJson
                                .collectAsStateWithLifecycle()
                                .value,
                            onApprovePlan = { _ ->
                                if (com.termfold.app.pdftools.PdfToolsSessionContext.approvePending()) {
                                    viewModel.updatePdfToolsSession(
                                        session.copy(
                                            approvedPlanId = com.termfold.app.pdftools.PdfToolsSessionContext.approvedPlanId,
                                        ),
                                    )
                                }
                            },
                            filesOpen = filesOpen,
                            onToggleFiles = { filesOpen = !filesOpen },
                            modifier = Modifier.windowInsetsPadding(
                                WindowInsets.safeDrawing.only(
                                    WindowInsetsSides.Top + WindowInsetsSides.End,
                                ),
                            ),
                        )
                    }
                }

                is Destination.AutomationSession -> {
                    val pair = automationData.session(current.projectId, current.sessionId)
                    if (pair == null) {
                        LaunchedEffect(current.projectId, current.sessionId) {
                            destination = Destination.AutomationProject(current.projectId)
                        }
                    } else {
                        val (project, session) = pair
                        val linked = project.linkedFolderId?.let { data.folder(it) }
                        BackHandler { destination = Destination.AutomationProject(project.id) }
                        AutomationAgentSessionScreen(
                            project = project,
                            session = session,
                            linkedFolder = linked,
                            onBack = { destination = Destination.AutomationProject(project.id) },
                            filesOpen = filesOpen,
                            onToggleFiles = { filesOpen = !filesOpen },
                            modifier = Modifier.windowInsetsPadding(
                                WindowInsets.safeDrawing.only(
                                    WindowInsetsSides.Top + WindowInsetsSides.End,
                                ),
                            ),
                        )
                    }
                }

                is Destination.Terminal -> {
                    val folder = data.folder(current.folderId)
                    val session = folder?.sessions?.firstOrNull { it.id == current.sessionId }
                    if (folder == null || session == null) {
                        LaunchedEffect(current.folderId, current.sessionId) {
                            destination = Destination.Tabs
                        }
                    } else if (session.acpAgentId.isNotBlank()) {
                        val workspaceEditorOpen = LocalWorkspaceEditorOpen.current
                        BackHandler(enabled = !workspaceEditorOpen) { destination = Destination.Folder(folder.id) }
                        AgentSessionScreen(
                            folder = folder,
                            sessionId = session.id,
                            workspaceId = current.workspaceId,
                            agentId = session.acpAgentId,
                            modifier = Modifier.windowInsetsPadding(
                                WindowInsets.safeDrawing.only(
                                    WindowInsetsSides.Top + WindowInsetsSides.End
                                )
                            ),
                            onBack = { destination = Destination.Folder(folder.id) },
                            filesOpen = filesOpen,
                            onToggleFiles = { filesOpen = !filesOpen },
                        )
                    } else {
                        val workspaceEditorOpen = LocalWorkspaceEditorOpen.current
                        BackHandler(enabled = !workspaceEditorOpen) { destination = Destination.Folder(folder.id) }
                        TerminalScreen(
                            folder = folder,
                            sessionId = session.id,
                            workspaceId = current.workspaceId,
                            sessionName = session.name,
                            initialCommand = session.command,
                            wide = windowWidth.isWide,
                            modifier = Modifier.windowInsetsPadding(
                                WindowInsets.safeDrawing.only(
                                    WindowInsetsSides.Top + WindowInsetsSides.End
                                )
                            ),
                            onBack = { destination = Destination.Folder(folder.id) },
                            onRestart = { TerminalHost.restart(context) },
                            filesOpen = filesOpen,
                            onToggleFiles = { filesOpen = !filesOpen },
                        )
                    }
                }
                    }
                }
              }
              }
            }

            // ---- The preview beside the work (tablet), with a handle to resize it.
            androidx.compose.animation.AnimatedVisibility(
                visible = windowWidth.isWide && preview.open && !previewMax,
                // Not clipped: minimized, the pane has no width of its own and must still be drawn
                // (beneath the work) for its page to keep rendering.
                enter = androidx.compose.animation.expandHorizontally(animationSpec = Motion.slow(), expandFrom = Alignment.Start, clip = false) + androidx.compose.animation.fadeIn(Motion.standard()),
                exit = androidx.compose.animation.shrinkHorizontally(animationSpec = Motion.standard(), shrinkTowards = Alignment.Start, clip = false) + androidx.compose.animation.fadeOut(Motion.quick()),
                modifier = Modifier.zIndex(if (preview.minimized) -1f else 0f),
            ) {
                val total = LocalConfiguration.current.screenWidthDp.dp - 84.dp
                val density = LocalDensity.current
                Row(Modifier.fillMaxHeight().parked(preview.minimized, overlapStart = true)) {
                    Box(
                        Modifier
                            .width(14.dp)
                            .fillMaxHeight()
                            .draggable(
                                orientation = Orientation.Horizontal,
                                state = rememberDraggableState { delta ->
                                    val totalPx = with(density) { total.toPx() }
                                    previewFraction = (previewFraction - delta / totalPx).coerceIn(0.3f, 0.72f)
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.width(1.dp).fillMaxHeight().background(Palette.BorderSoft))
                        Box(Modifier.width(4.dp).height(36.dp).clip(RoundedCornerShape(2.dp)).background(Palette.Border))
                    }
                    PreviewPane(
                        state = preview,
                        wide = true,
                        maximized = false,
                        onToggleMaximize = { previewMax = true },
                        onClose = { com.termfold.app.preview.Preview.close() },
                        onMinimize = { com.termfold.app.preview.Preview.minimize() },
                        parked = preview.minimized,
                        modifier = Modifier
                            .width(total * previewFraction)
                            .fillMaxHeight()
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.End + WindowInsetsSides.Bottom)),
                    )
                }
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = gitChanges.open,
            enter = androidx.compose.animation.fadeIn(Motion.standard()),
            exit = androidx.compose.animation.fadeOut(Motion.quick()),
            modifier = Modifier.zIndex(2f),
        ) {
            androidx.activity.compose.BackHandler { com.termfold.app.gitworkspace.GitChanges.close() }
            com.termfold.app.ui.screens.GitChangesPane(
                state = gitChanges,
                onClose = { com.termfold.app.gitworkspace.GitChanges.close() },
                modifier = Modifier
                    .fillMaxSize()
                    .background(com.termfold.app.ui.theme.Palette.Bg)
                    .windowInsetsPadding(WindowInsets.safeDrawing),
            )
        }

        // ---- The preview over everything (phone, or full screen on a tablet).
        androidx.compose.animation.AnimatedVisibility(
            visible = preview.open && (!windowWidth.isWide || previewMax),
            // A fade, not a slide: a moving layer can leave the WebView (a native view) drawn
            // out of place on some phones.
            enter = androidx.compose.animation.fadeIn(Motion.standard()),
            exit = androidx.compose.animation.fadeOut(Motion.quick()),
            modifier = Modifier.zIndex(if (preview.minimized) -1f else 1f),
        ) {
            BackHandler(enabled = !preview.minimized) { if (previewMax) previewMax = false else com.termfold.app.preview.Preview.close() }
            PreviewPane(
                onMinimize = { com.termfold.app.preview.Preview.minimize() },
                parked = preview.minimized,
                state = preview,
                wide = windowWidth.isWide,
                maximized = previewMax,
                onToggleMaximize = if (windowWidth.isWide) ({ previewMax = false }) else null,
                onClose = {
                    previewMax = false
                    com.termfold.app.preview.Preview.close()
                },
                // Dark behind the status and navigation bars too, not just inside them.
                modifier = Modifier.fillMaxSize().parked(preview.minimized).background(Palette.Bg).windowInsetsPadding(WindowInsets.safeDrawing),
            )
        }

        // ---- The minimized browser: a pill that can be dragged anywhere.
        androidx.compose.animation.AnimatedVisibility(
            visible = preview.open && preview.minimized,
            enter = androidx.compose.animation.scaleIn(Motion.standard(), initialScale = 0.8f) + androidx.compose.animation.fadeIn(Motion.standard()),
            exit = androidx.compose.animation.scaleOut(Motion.quick(), targetScale = 0.8f) + androidx.compose.animation.fadeOut(Motion.quick()),
            modifier = Modifier.fillMaxSize(),
        ) {
            androidx.compose.foundation.layout.BoxWithConstraints(
                Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
            ) {
                val density = LocalDensity.current
                val pillW = with(density) { 236.dp.toPx() }
                val pillH = with(density) { 46.dp.toPx() }
                val maxX = (constraints.maxWidth - pillW).coerceAtLeast(0f)
                val maxY = (constraints.maxHeight - pillH).coerceAtLeast(0f)
                // Starts bottom-right, clear of the bottom bar on a phone; stays where it is dropped.
                var pillX by rememberSaveable { mutableFloatStateOf(-1f) }
                var pillY by rememberSaveable { mutableFloatStateOf(-1f) }
                if (pillX < 0f) pillX = maxX - with(density) { 16.dp.toPx() }
                // Top right, just under the header so its buttons stay reachable.
                if (pillY < 0f) pillY = with(density) { 60.dp.toPx() }.coerceAtMost(maxY)
                com.termfold.app.ui.screens.PreviewPill(
                    Modifier
                        .offset { androidx.compose.ui.unit.IntOffset(pillX.coerceIn(0f, maxX).toInt(), pillY.coerceIn(0f, maxY).toInt()) }
                        .pointerInput(maxX, maxY) {
                            detectDragGestures { change, drag ->
                                change.consume()
                                pillX = (pillX.coerceIn(0f, maxX) + drag.x).coerceIn(0f, maxX)
                                pillY = (pillY.coerceIn(0f, maxY) + drag.y).coerceIn(0f, maxY)
                            }
                        },
                )
            }
        }
    }
    }

    if (newAutomationProject) {
        var name by remember { mutableStateOf("") }
        TextPromptDialog(
            title = stringResource(R.string.workflows_new),
            value = name,
            onValueChange = { name = it },
            placeholder = stringResource(R.string.project_name_hint),
            onConfirm = {
                if (name.isNotBlank()) {
                    viewModel.createAutomationProject(name.trim()) { id ->
                        destination = Destination.AutomationProject(id)
                    }
                }
                newAutomationProject = false
            },
            onDismiss = { newAutomationProject = false },
        )
    }
    newAutomationSessionFor?.let { projectId ->
        var name by remember(projectId) { mutableStateOf("") }
        TextPromptDialog(
            title = stringResource(R.string.automation_new_session),
            value = name,
            onValueChange = { name = it },
            placeholder = stringResource(R.string.session_name_hint),
            onConfirm = {
                if (name.isNotBlank()) {
                    viewModel.addAutomationSession(projectId, name.trim()) { sessionId ->
                        destination = Destination.AutomationSession(projectId, sessionId)
                    }
                }
                newAutomationSessionFor = null
            },
            onDismiss = { newAutomationSessionFor = null },
        )
    }
    if (newProject) {
        com.termfold.app.ui.screens.NewProjectDialog(
            onCreateEmpty = { name ->
                newProject = false
                viewModel.createProject(name) { id -> destination = Destination.Folder(id) }
            },
            onClone = { url, name ->
                newProject = false
                viewModel.cloneProject(url, name)
            },
            onImport = {
                newProject = false
                pickFolder.launch(null)
            },
            onDismiss = { newProject = false },
        )
    }
    if (startupCommandsOpen) {
        StartupCommandsDialog(
            folders = data.folders,
            data = startupCommandsData,
            runningIds = startupCommandsRunning,
            logs = startupCommandLogs,
            onDismiss = { startupCommandsOpen = false },
            onAdd = { name, folderId, command ->
                viewModel.addStartupCommand(name, folderId, command)
            },
            onAddGroup = { name, commandIds ->
                viewModel.addStartupCommandGroup(name, commandIds)
            },
            onUpdate = { id, name, folderId, command ->
                viewModel.updateStartupCommand(id, name, folderId, command)
            },
            onUpdateGroup = { id, name, commandIds ->
                viewModel.updateStartupCommandGroup(id, name, commandIds)
            },
            onStart = { id ->
                viewModel.startStartupCommand(id) { msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            },
            onStop = { viewModel.stopStartupCommand(it) },
            onStartGroup = { id ->
                viewModel.startStartupCommandGroup(id) { msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            },
            onStopGroup = { viewModel.stopStartupCommandGroup(it) },
            onDelete = { viewModel.removeStartupCommand(it) },
            onDeleteGroup = { viewModel.removeStartupCommandGroup(it) },
        )
    }
    LaunchedEffect(pmJob?.doneSessionId) {
        val id = pmJob?.doneSessionId ?: return@LaunchedEffect
        destination = Destination.PmSession(id)
        kotlinx.coroutines.delay(400)
        viewModel.dismissPmJob()
    }
    pmJob?.let { job ->
        val error = job.error
        if (error != null) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissPmJob() },
                title = { Text(job.title, color = Palette.Text) },
                text = { Text(error, color = Palette.TextDim) },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismissPmJob() }) {
                        Text(stringResource(R.string.action_ok), color = Palette.Accent)
                    }
                },
            )
        } else if (job.doneSessionId == null) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text(job.title, color = Palette.Text) },
                text = {
                    Column {
                        Text(job.step, color = Palette.TextDim, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { (job.percent / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { viewModel.cancelPmBind() }) {
                        Text(stringResource(R.string.pr_review_job_cancel), color = Palette.TextDim)
                    }
                },
            )
        }
    }
    prReviewJob?.let { job ->
        val error = job.error
        if (error != null) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissPrReviewJob() },
                title = { Text(job.title, color = Palette.Text) },
                text = { Text(error, color = Palette.TextDim) },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismissPrReviewJob() }) {
                        Text(stringResource(R.string.action_ok), color = Palette.Accent)
                    }
                },
            )
        } else if (job.doneSessionId == null) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text(job.title, color = Palette.Text) },
                text = {
                    Column {
                        Text(job.step, color = Palette.TextDim, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { (job.percent / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(
                        onClick = { viewModel.cancelPrReviewBind() },
                    ) {
                        Text(stringResource(R.string.pr_review_job_cancel), color = Palette.TextDim)
                    }
                },
            )
        }
    }
    job?.let { j ->
        com.termfold.app.ui.screens.ProjectJobDialog(
            job = j,
            onOpen = { id ->
                viewModel.dismissJob()
                destination = Destination.Folder(id)
            },
            onDismiss = { viewModel.dismissJob() },
        )
    }

    OptionsLayer(
        viewModel = viewModel,
        optionsFor = optionsFor,
        onDismissOptions = { optionsFor = null },
        onRequestDelete = { confirmDelete = it },
        renameFolderId = renameFolderId,
        onDismissRename = { renameFolderId = null },
        confirmDelete = confirmDelete,
        onDismissDelete = { confirmDelete = null },
        addSessionFor = addSessionFor,
        onDismissAddSession = { addSessionFor = null },
        onCreateSession = { folderId, name, command, acpAgentId ->
            if (acpAgentId == CURSOR_AGENT_ID) {
                pendingCursorAdd = PendingCursorAdd(folderId, name, acpAgentId)
                addSessionFor = null
            } else {
                viewModel.addSession(folderId, name, command, acpAgentId)
                addSessionFor = null
            }
        },
        folderNameFor = { id -> data.folder(id)?.name.orEmpty() },
        sessionIdsFor = { id -> data.folder(id)?.sessions?.map { it.id }.orEmpty() },
    )

    WorkflowSessionOptionsLayer(
        optionsFor = workflowSessionOptionsFor,
        onDismissOptions = { workflowSessionOptionsFor = null },
        onRequestDelete = { workflowSessionConfirmDelete = it },
        confirmDelete = workflowSessionConfirmDelete,
        onDismissDelete = { workflowSessionConfirmDelete = null },
        sessionNameFor = { target ->
            when (target) {
                is WorkflowSessionOptionsTarget.PrReview ->
                    prReviewData.session(target.sessionId)?.name.orEmpty()
                is WorkflowSessionOptionsTarget.Pm ->
                    pmData.session(target.sessionId)?.name.orEmpty()
                is WorkflowSessionOptionsTarget.PdfTools ->
                    pdfToolsData.session(target.sessionId)?.name.orEmpty()
            }
        },
        onConfirmDelete = { target ->
            when (target) {
                is WorkflowSessionOptionsTarget.PrReview -> {
                    viewModel.removePrReviewSession(target.sessionId)
                    if (destination is Destination.PrReviewSession &&
                        (destination as Destination.PrReviewSession).sessionId == target.sessionId
                    ) {
                        tab = NavTab.WORKFLOWS
                        destination = Destination.WorkflowsSection(WorkflowsSection.PR_REVIEWER)
                    }
                }
                is WorkflowSessionOptionsTarget.Pm -> {
                    viewModel.removePmSession(target.sessionId)
                    if (destination is Destination.PmSession &&
                        (destination as Destination.PmSession).sessionId == target.sessionId
                    ) {
                        tab = NavTab.WORKFLOWS
                        destination = Destination.WorkflowsSection(WorkflowsSection.PRODUCT_MANAGER)
                    }
                }
                is WorkflowSessionOptionsTarget.PdfTools -> {
                    viewModel.removePdfToolsSession(target.sessionId)
                    if (destination is Destination.PdfToolsSession &&
                        (destination as Destination.PdfToolsSession).sessionId == target.sessionId
                    ) {
                        tab = NavTab.WORKFLOWS
                        destination = Destination.WorkflowsSection(WorkflowsSection.PDF_TOOLS)
                    }
                }
            }
            workflowSessionConfirmDelete = null
            workflowSessionOptionsFor = null
        },
    )

    AutomationOptionsLayer(
        optionsFor = automationOptionsFor,
        onDismissOptions = { automationOptionsFor = null },
        onRequestDelete = { automationConfirmDelete = it },
        confirmDelete = automationConfirmDelete,
        onDismissDelete = { automationConfirmDelete = null },
        projectNameFor = { id -> automationData.project(id)?.name.orEmpty() },
        sessionNameFor = { projectId, sessionId ->
            automationData.session(projectId, sessionId)?.second?.name.orEmpty()
        },
        onConfirmDelete = { target ->
            when (target) {
                is AutomationOptionsTarget.Project -> {
                    com.termfold.app.shell.SessionRegistry.closeAll(target.projectId)
                    viewModel.removeAutomationProject(target.projectId)
                    when (val d = destination) {
                        is Destination.AutomationProject if d.projectId == target.projectId ->
                            destination = Destination.WorkflowsSection(WorkflowsSection.AUTOMATION)
                        is Destination.AutomationSession if d.projectId == target.projectId ->
                            destination = Destination.WorkflowsSection(WorkflowsSection.AUTOMATION)
                        else -> Unit
                    }
                }
                is AutomationOptionsTarget.Session -> {
                    com.termfold.app.shell.SessionRegistry.closeAll(target.projectId, target.sessionId)
                    viewModel.removeAutomationSession(target.projectId, target.sessionId)
                    val d = destination
                    if (
                        d is Destination.AutomationSession &&
                        d.projectId == target.projectId &&
                        d.sessionId == target.sessionId
                    ) {
                        destination = Destination.AutomationProject(target.projectId)
                    }
                }
            }
            automationConfirmDelete = null
            automationOptionsFor = null
        },
    )

    pendingCursorAdd?.let { pending ->
        val folder = data.folder(pending.folderId)
        if (folder == null) {
            pendingCursorAdd = null
            cursorBranchPick = false
        } else if (cursorBranchPick) {
            com.termfold.app.ui.screens.WorkspaceBranchPickDialog(
                folder = folder,
                onDismiss = {
                    cursorBranchPick = false
                    pendingCursorAdd = null
                },
                onPickBranch = { branch ->
                    val workspaceId = com.termfold.app.gitworkspace.ProjectWorkspaces.newSessionId()
                    val sessionId = com.termfold.app.core.Ids.new()
                    pendingCursorProvision = PendingCursorProvision(
                        folderId = pending.folderId,
                        name = pending.name,
                        acpAgentId = pending.acpAgentId,
                        branch = branch,
                        workspaceId = workspaceId,
                        sessionId = sessionId,
                    )
                    pendingCursorAdd = null
                    cursorBranchPick = false
                },
            )
        } else {
            com.termfold.app.ui.screens.CursorAddModeDialog(
                onDismiss = { pendingCursorAdd = null },
                onSharedProject = {
                    viewModel.addSession(pending.folderId, pending.name, "", pending.acpAgentId)
                    pendingCursorAdd = null
                },
                onBranchCopy = { cursorBranchPick = true },
            )
        }
    }

    pendingCursorProvision?.let { pending ->
        val folder = data.folder(pending.folderId)
        if (folder == null) {
            pendingCursorProvision = null
        } else {
            com.termfold.app.ui.screens.WorkspaceProvisionDialog(
                folder = folder,
                branch = pending.branch,
                sessionWorkspaceId = pending.workspaceId,
                onDismiss = { pendingCursorProvision = null },
                onComplete = {
                    viewModel.addSession(
                        pending.folderId,
                        pending.name,
                        "",
                        pending.acpAgentId,
                        pending.workspaceId,
                        pending.sessionId,
                    )
                    destination = Destination.Folder(pending.folderId)
                    pendingCursorProvision = null
                },
                onCancelled = { pendingCursorProvision = null },
            )
        }
    }
}

private const val CURSOR_AGENT_ID = "cursor"

private data class PendingCursorAdd(
    val folderId: String,
    val name: String,
    val acpAgentId: String,
)

private data class PendingCursorProvision(
    val folderId: String,
    val name: String,
    val acpAgentId: String,
    val branch: String,
    val workspaceId: String,
    val sessionId: String,
)

@Composable
private fun WorkflowSessionOptionsLayer(
    optionsFor: WorkflowSessionOptionsTarget?,
    onDismissOptions: () -> Unit,
    onRequestDelete: (WorkflowSessionOptionsTarget) -> Unit,
    confirmDelete: WorkflowSessionOptionsTarget?,
    onDismissDelete: () -> Unit,
    sessionNameFor: (WorkflowSessionOptionsTarget) -> String,
    onConfirmDelete: (WorkflowSessionOptionsTarget) -> Unit,
) {
    optionsFor?.let { target ->
        com.termfold.app.ui.screens.OptionsSheet(
            title = sessionNameFor(target),
            actions = listOf(stringResource(R.string.action_remove) to { onRequestDelete(target) }),
            onDismiss = onDismissOptions,
        )
    }
    confirmDelete?.let { target ->
        val name = sessionNameFor(target)
        com.termfold.app.ui.screens.ConfirmDialog(
            title = stringResource(R.string.workflow_session_remove_title, name.replace("%", "%%")),
            body = stringResource(R.string.workflow_session_remove_body),
            confirmLabel = stringResource(R.string.action_remove),
            onConfirm = { onConfirmDelete(target) },
            onDismiss = onDismissDelete,
        )
    }
}

@Composable
private fun AutomationOptionsLayer(
    optionsFor: AutomationOptionsTarget?,
    onDismissOptions: () -> Unit,
    onRequestDelete: (AutomationOptionsTarget) -> Unit,
    confirmDelete: AutomationOptionsTarget?,
    onDismissDelete: () -> Unit,
    projectNameFor: (String) -> String,
    sessionNameFor: (String, String) -> String,
    onConfirmDelete: (AutomationOptionsTarget) -> Unit,
) {
    optionsFor?.let { target ->
        val title = when (target) {
            is AutomationOptionsTarget.Project -> projectNameFor(target.projectId)
            is AutomationOptionsTarget.Session -> sessionNameFor(target.projectId, target.sessionId)
        }
        OptionsSheet(
            title = title,
            actions = listOf(stringResource(R.string.action_remove) to { onRequestDelete(target) }),
            onDismiss = onDismissOptions,
        )
    }

    confirmDelete?.let { target ->
        val isProject = target is AutomationOptionsTarget.Project
        val title = when (target) {
            is AutomationOptionsTarget.Project ->
                stringResource(R.string.project_delete_title, projectNameFor(target.projectId))
            is AutomationOptionsTarget.Session -> stringResource(R.string.action_remove)
        }
        val body = when (target) {
            is AutomationOptionsTarget.Project -> stringResource(R.string.project_delete_body)
            is AutomationOptionsTarget.Session ->
                sessionNameFor(target.projectId, target.sessionId)
        }
        ConfirmDialog(
            title = title,
            body = body,
            confirmLabel = if (isProject) stringResource(R.string.files_delete) else stringResource(R.string.action_remove),
            onConfirm = { onConfirmDelete(target) },
            onDismiss = onDismissDelete,
        )
    }
}

@Composable
private fun OptionsLayer(
    viewModel: AppViewModel,
    optionsFor: OptionsTarget?,
    onDismissOptions: () -> Unit,
    onRequestDelete: (OptionsTarget) -> Unit,
    renameFolderId: String?,
    onDismissRename: () -> Unit,
    confirmDelete: OptionsTarget?,
    onDismissDelete: () -> Unit,
    addSessionFor: String?,
    onDismissAddSession: () -> Unit,
    onCreateSession: (folderId: String, name: String, command: String, acpAgentId: String) -> Unit,
    folderNameFor: (String) -> String,
    sessionIdsFor: (String) -> List<String>,
) {
    optionsFor?.let { target ->
        val actions = listOf(
            stringResource(R.string.action_remove) to { onRequestDelete(target) },
        )

        OptionsSheet(
            title = folderNameFor(target.folderId),
            actions = actions,
            onDismiss = onDismissOptions,
        )
    }

    renameFolderId?.let { folderId ->
        var name by remember(folderId) { mutableStateOf(folderNameFor(folderId)) }
        TextPromptDialog(
            title = stringResource(R.string.cd_name),
            value = name,
            onValueChange = { name = it },
            onConfirm = {
                if (name.isNotBlank()) viewModel.renameFolder(folderId, name.trim())
                onDismissRename()
            },
            onDismiss = onDismissRename,
            confirmLabel = stringResource(R.string.action_add),
        )
    }

    confirmDelete?.let { target ->
        val isFolder = target is OptionsTarget.Folder
        ConfirmDialog(
            title = if (isFolder) stringResource(R.string.project_delete_title, folderNameFor(target.folderId)) else stringResource(R.string.action_remove),
            body = if (isFolder) stringResource(R.string.project_delete_body) else folderNameFor(target.folderId),
            confirmLabel = if (isFolder) stringResource(R.string.files_delete) else stringResource(R.string.action_remove),
            onConfirm = {
                if (isFolder) {
                    // End the folder's running sessions before the definitions disappear,
                    // otherwise their processes would idle on with no way to reach them.
                    com.termfold.app.shell.SessionRegistry.closeAll(target.folderId)
                    viewModel.removeFolder(target.folderId)
                } else {
                    val sessionId = (target as OptionsTarget.Session).sessionId
                    com.termfold.app.shell.SessionRegistry.closeAll(target.folderId, sessionId)
                    viewModel.removeSession(target.folderId, sessionId)
                }
            },
            onDismiss = onDismissDelete,
        )
    }

    addSessionFor?.let { folderId ->
        NewSessionDialog(
            onDismiss = onDismissAddSession,
            onCreate = { name, command, acpAgentId ->
                onCreateSession(folderId, name, command, acpAgentId)
            },
        )
    }
}

/** Preset-driven session creation, with a free-text command for the custom case. */
@Composable
private fun NewSessionDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, command: String, acpAgentId: String) -> Unit,
) {
    val context = LocalContext.current
    val presets = SessionPreset.entries
    var selected by remember { mutableStateOf(0) }
    var name by remember { mutableStateOf("") }
    var command by remember { mutableStateOf("") }

    // The trailing pseudo-preset switches the session into ACP mode: it runs a registry agent
    // through the native agent interface instead of a shell.
    val acpIndex = presets.size
    var agents by remember { mutableStateOf<List<com.termfold.app.acp.AcpAgent>>(emptyList()) }
    var loadingAgents by remember { mutableStateOf(false) }
    var selectedAgent by remember {
        mutableStateOf<com.termfold.app.acp.AcpAgent?>(null)
    }

    LaunchedEffect(selected) {
        if (selected == acpIndex && agents.isEmpty() && !loadingAgents) {
            loadingAgents = true
            agents = com.termfold.app.acp.AcpRegistry.agents(context)
            loadingAgents = false
        }
    }

    val preset = presets.getOrNull(selected)
    val effectiveCommand = if (preset == SessionPreset.CUSTOM) command
    else preset?.defaultCommand.orEmpty()
    val isAcp = selected == acpIndex
    val fallbackName = when {
        isAcp -> selectedAgent?.name ?: "ACP"
        preset != null -> stringResource(preset.labelRes)
        else -> "ACP"
    }
    val canConfirm = isAcp && selectedAgent != null ||
        preset == SessionPreset.SHELL ||
        effectiveCommand.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Card,
        titleContentColor = Palette.Text,
        title = {
            Text(
                text = stringResource(R.string.new_session_title),
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                PresetChips(
                    labels = presets.map { stringResource(it.labelRes) } + stringResource(R.string.acp_chip),
                    selectedIndex = selected,
                    onSelect = { selected = it },
                )

                Spacer(Modifier.height(16.dp))

                if (isAcp) {
                    Text(
                        text = stringResource(R.string.acp_agent_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.TextFaint,
                    )
                    Spacer(Modifier.height(6.dp))
                    when {
                        loadingAgents -> Text(
                            text = stringResource(R.string.acp_registry_loading),
                            style = MaterialTheme.typography.bodySmall,
                            color = Palette.TextDim,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )

                        agents.isEmpty() -> Text(
                            text = stringResource(R.string.acp_registry_offline),
                            style = MaterialTheme.typography.bodySmall,
                            color = Palette.Pink,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )

                        else -> Column(
                            modifier = Modifier
                                .heightIn(max = 220.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            agents.forEach { entry ->
                                val active = selectedAgent?.id == entry.id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (active) Palette.CardPressed else Color.Transparent)
                                        .clickable {
                                            selectedAgent = entry
                                            if (name.isBlank()) name = entry.name
                                        }
                                        .padding(horizontal = 8.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    com.termfold.app.ui.components.AgentIconImage(
                                        iconUrl = entry.iconUrl,
                                        name = entry.name,
                                        size = 26.dp,
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = entry.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Palette.Text,
                                            maxLines = 1,
                                        )
                                        if (entry.description.isNotBlank()) {
                                            Text(
                                                text = entry.description,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Palette.TextFaint,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.acp_session_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.TextFaint,
                    )
                    Spacer(Modifier.height(14.dp))
                }

                if (preset == SessionPreset.CUSTOM && !isAcp) {
                    LabelledField(
                        label = stringResource(R.string.custom_command),
                        value = command,
                        onValueChange = { command = it },
                        placeholder = "npm start",
                    )
                    Spacer(Modifier.height(14.dp))
                }

                LabelledField(
                    label = stringResource(R.string.cd_name),
                    value = name,
                    onValueChange = { name = it },
                    placeholder = fallbackName,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onCreate(
                        name.ifBlank { fallbackName }.trim(),
                        effectiveCommand.trim(),
                        if (isAcp) selectedAgent?.id.orEmpty() else "",
                    )
                },
                enabled = canConfirm,
            ) {
                Text(
                    text = stringResource(R.string.action_add),
                    color = if (canConfirm) Palette.Accent else Palette.TextFaint,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.action_cancel),
                    color = Palette.TextDim,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
    )
}

@Composable
private fun LabelledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Palette.TextFaint,
        )
        Spacer(Modifier.height(6.dp))
        SearchField(
            query = value,
            onQueryChange = onValueChange,
            placeholder = placeholder,
        )
    }
}

/**
 * Takes the content out of view without taking it out of the app: it keeps its full size and
 * keeps running (a minimized browser keeps drawing its page for an agent) but occupies no space.
 * It stays on the screen, under the app (the caller lowers its z-index; the app above is
 * opaque and hit-testable), because a WebView moved off the screen stops drawing and its
 * screenshots come out black. [overlapStart] lays it over the space to its left, for a pane at
 * the end of a row.
 */
private fun Modifier.parked(parked: Boolean, overlapStart: Boolean = false): Modifier = this.layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    if (!parked) {
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    } else {
        layout(0, 0) { placeable.place(if (overlapStart) -placeable.width else 0, 0) }
    }
}
