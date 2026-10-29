package com.termfold.app.core

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.termfold.app.backup.TermFoldBackup
import com.termfold.app.automation.AutomationPaths
import com.termfold.app.automation.AutomationProject
import com.termfold.app.automation.AutomationSession
import com.termfold.app.automation.AutomationStorage
import com.termfold.app.data.AutomationStore
import com.termfold.app.data.FolderStore
import com.termfold.app.data.PmSessionStore
import com.termfold.app.data.PrReviewStore
import com.termfold.app.pm.PmBriefFiles
import com.termfold.app.pm.PmBind
import com.termfold.app.pm.PmData
import com.termfold.app.pm.PmMode
import com.termfold.app.pm.PmPaths
import com.termfold.app.pm.PmRepoResearch
import com.termfold.app.pm.PmResearchOrchestrator
import com.termfold.app.pm.PmSession
import com.termfold.app.pm.memory.PmMemoryStore
import com.termfold.app.gitworkspace.CheckoutMode
import com.termfold.app.gitworkspace.GitWorkspace
import com.termfold.app.gitworkspace.ProjectWorkspaces
import com.termfold.app.gitworkspace.WorkspaceRecord
import com.termfold.app.gitworkspace.WorkspaceStore
import com.termfold.app.prreview.BindAction
import com.termfold.app.prreview.DoneKind
import com.termfold.app.prreview.GitHubOrigin
import com.termfold.app.prreview.GitHubPrApi
import com.termfold.app.prreview.GitHubPrFetch
import com.termfold.app.prreview.GitHubPrUrl
import com.termfold.app.prreview.PrReviewBind
import com.termfold.app.prreview.PrReviewData
import com.termfold.app.prreview.PrReviewFactsPack
import com.termfold.app.prreview.GitHubPrInfo
import com.termfold.app.prreview.GitHubPrRef
import com.termfold.app.prreview.PrReviewParse
import com.termfold.app.prreview.PrReviewPaths
import com.termfold.app.prreview.PrReviewSession
import com.termfold.app.prreview.PrReviewTurnSync
import com.termfold.app.acp.AcpItem
import com.termfold.app.shell.DistroMigration
import com.termfold.app.shell.Projects
import com.termfold.app.shell.ShellRuntime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Progress of a full backup or restore to a single `.termfold` file. */
data class BackupJob(
    val title: String,
    val step: String = "",
    val fraction: Float = 0f,
    val error: String? = null,
    val done: Boolean = false,
)

data class PrReviewJob(
    val title: String,
    val step: String = "",
    val percent: Int = 0,
    val error: String? = null,
    val doneSessionId: String? = null,
)

data class PmJob(
    val title: String,
    val step: String = "",
    val percent: Int = 0,
    val error: String? = null,
    val doneSessionId: String? = null,
)

data class PdfToolsJob(
    val title: String,
    val step: String = "",
    val percent: Int = 0,
    val error: String? = null,
    val doneSessionId: String? = null,
)

/** A project being created in the background (a git clone or a device-folder import). */
data class ProjectJob(
    val title: String,
    val progress: String = "",
    val error: String? = null,
    val done: Boolean = false,
    /** The new project, once it exists. */
    val folderId: String? = null,
)

/**
 * Whether the bundled Linux environment has been unpacked yet. [MIGRATE_OFFER] and [MIGRATING]
 * are for an install made with Ubuntu, which is offered the move to Debian ([DistroMigration]).
 */
enum class ShellState { UNKNOWN, PREPARING, READY, FAILED, UNSUPPORTED, MIGRATE_OFFER, MIGRATING }

/** Progress of the one-time setup, so the UI can narrate it. */
data class ShellProgress(
    val state: ShellState = ShellState.UNKNOWN,
    val step: String = "",
    val fraction: Float = 0f,
    val error: String? = null,
)

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private companion object {
        /** How long "Later" holds the offer back. */
        const val SNOOZE_MS = 24L * 60 * 60 * 1000
    }

    private val store = FolderStore(application)
    private val automationStore = AutomationStore(application)
    private val prReviewStore = PrReviewStore(application)
    private val pmSessionStore = PmSessionStore(application)
    private val pdfToolsStore = com.termfold.app.data.PdfToolsStore(application)

    val folders: StateFlow<AppData> = store.data
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppData())

    val automation: StateFlow<com.termfold.app.automation.AutomationData> = automationStore.data
        .stateIn(viewModelScope, SharingStarted.Eagerly, com.termfold.app.automation.AutomationData())

    val prReviews: StateFlow<PrReviewData> = prReviewStore.data
        .stateIn(viewModelScope, SharingStarted.Eagerly, PrReviewData())

    val pmSessions: StateFlow<PmData> = pmSessionStore.data
        .stateIn(viewModelScope, SharingStarted.Eagerly, PmData())

    val pdfTools: StateFlow<com.termfold.app.pdftools.PdfToolsData> = pdfToolsStore.data
        .stateIn(viewModelScope, SharingStarted.Eagerly, com.termfold.app.pdftools.PdfToolsData())

    private val _shell = MutableStateFlow(ShellProgress())
    val shell: StateFlow<ShellProgress> = _shell.asStateFlow()

    private val _job = MutableStateFlow<ProjectJob?>(null)
    val job: StateFlow<ProjectJob?> = _job.asStateFlow()

    private val _backupJob = MutableStateFlow<BackupJob?>(null)
    val backupJob: StateFlow<BackupJob?> = _backupJob.asStateFlow()

    private val _prReviewJob = MutableStateFlow<PrReviewJob?>(null)
    val prReviewJob: StateFlow<PrReviewJob?> = _prReviewJob.asStateFlow()
    private var prReviewBindJob: Job? = null

    private val _pmJob = MutableStateFlow<PmJob?>(null)
    val pmJob: StateFlow<PmJob?> = _pmJob.asStateFlow()
    private var pmBindJob: Job? = null

    private val _pdfToolsJob = MutableStateFlow<PdfToolsJob?>(null)
    val pdfToolsJob: StateFlow<PdfToolsJob?> = _pdfToolsJob.asStateFlow()
    private val _pdfToolsLibrariesReady = MutableStateFlow(
        com.termfold.app.pdftools.PdfToolsGuest.librariesReady(application),
    )
    val pdfToolsLibrariesReady: StateFlow<Boolean> = _pdfToolsLibrariesReady.asStateFlow()

    fun dismissJob() {
        if (_job.value?.done == true || _job.value?.error != null) _job.value = null
    }

    fun dismissBackupJob() {
        if (_backupJob.value?.done == true || _backupJob.value?.error != null) _backupJob.value = null
    }

    private fun backupInFlight(): Boolean =
        _backupJob.value?.let { !it.done && it.error == null } == true

    fun startBackup(uri: Uri) {
        if (backupInFlight()) return
        val app = getApplication<Application>()
        _backupJob.value = BackupJob(
            title = app.getString(com.termfold.app.R.string.backup_create),
            step = "",
            fraction = 0f,
        )
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    app.contentResolver.openOutputStream(uri)?.use { output ->
                        TermFoldBackup.export(app, output) { progress ->
                            _backupJob.update {
                                it?.copy(step = progress.step, fraction = progress.fraction)
                            }
                        }
                    } ?: error("Could not open the destination file.")
                }
            }
            _backupJob.value = result.fold(
                onSuccess = {
                    BackupJob(
                        title = app.getString(com.termfold.app.R.string.backup_create),
                        step = app.getString(com.termfold.app.R.string.backup_done_body),
                        fraction = 1f,
                        done = true,
                    )
                },
                onFailure = { error ->
                    BackupJob(
                        title = app.getString(com.termfold.app.R.string.backup_create),
                        error = error.message ?: error.javaClass.simpleName,
                    )
                },
            )
        }
    }

    fun startRestore(uri: Uri) {
        if (backupInFlight()) return
        val app = getApplication<Application>()
        _backupJob.value = BackupJob(
            title = app.getString(com.termfold.app.R.string.backup_restore),
            step = "",
            fraction = 0f,
        )
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    app.contentResolver.openInputStream(uri)?.use { input ->
                        TermFoldBackup.restore(app, input) { progress ->
                            _backupJob.update {
                                it?.copy(step = progress.step, fraction = progress.fraction)
                            }
                        }
                    } ?: error("Could not read the backup file.")
                }
            }
            result.onSuccess {
                syncProjects()
                _shell.value = ShellProgress(state = ShellState.READY, step = "", fraction = 1f)
            }
            _backupJob.value = result.fold(
                onSuccess = {
                    BackupJob(
                        title = app.getString(com.termfold.app.R.string.backup_restore),
                        step = app.getString(com.termfold.app.R.string.backup_restore_done_body),
                        fraction = 1f,
                        done = true,
                    )
                },
                onFailure = { error ->
                    BackupJob(
                        title = app.getString(com.termfold.app.R.string.backup_restore),
                        error = error.message ?: error.javaClass.simpleName,
                    )
                },
            )
        }
    }

    /** Set when the last attempt to move to Debian failed, so Retry tries that again. */
    private var migrationFailed = false

    private val migrationPrefs =
        application.getSharedPreferences("distro_migration", android.content.Context.MODE_PRIVATE)

    init {
        // Provisioning is started from the UI rather than here, so a failure surfaces on screen
        // instead of being swallowed during ViewModel construction.
        _shell.value = ShellProgress(
            state = when {
                !ShellRuntime.isReady(application) -> ShellState.UNKNOWN
                // An Ubuntu install is offered the move to Debian, unless the user said "later"
                // recently. Until then it keeps working exactly as before.
                ShellRuntime.isLegacyUbuntu(application) && !migrationSnoozed() -> ShellState.MIGRATE_OFFER
                else -> ShellState.READY
            }
        )
    }

    private fun migrationSnoozed(): Boolean =
        System.currentTimeMillis() - migrationPrefs.getLong("snoozed_at", 0L) < SNOOZE_MS

    /** Shows the offer to move to Debian; used by the button in Settings. */
    fun offerMigration() {
        if (_shell.value.state == ShellState.READY && ShellRuntime.isLegacyUbuntu(getApplication())) {
            _shell.value = ShellProgress(state = ShellState.MIGRATE_OFFER)
        }
    }

    /** "Later": carry on with Ubuntu for now, and ask again tomorrow. */
    fun postponeMigration() {
        migrationPrefs.edit().putLong("snoozed_at", System.currentTimeMillis()).apply()
        _shell.value = ShellProgress(state = ShellState.READY)
        // The refresh an existing environment gets on launch was held back by the offer.
        refreshed = false
        ensureShellReady()
    }

    /** Moves the environment from Ubuntu to Debian, keeping the user's files. */
    fun startMigration() {
        if (_shell.value.state == ShellState.MIGRATING) return
        migrationFailed = false
        _shell.value = ShellProgress(state = ShellState.MIGRATING, step = "", fraction = 0f)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    DistroMigration.migrate(getApplication()) { step, fraction ->
                        _shell.update { it.copy(step = step, fraction = fraction) }
                    }
                }
            }
            _shell.value = result.fold(
                onSuccess = {
                    refreshed = true
                    ShellProgress(state = ShellState.READY, step = "", fraction = 1f)
                },
                onFailure = { error ->
                    Log.e("AppViewModel", "Migration to Debian failed", error)
                    migrationFailed = true
                    ShellProgress(state = ShellState.FAILED, error = error.message ?: error.javaClass.simpleName)
                },
            )
            syncProjects()
        }
    }

    /**
     * Unpacks the bundled Linux environment if it is not already there.
     *
     * Safe to call repeatedly: [ShellRuntime.provision] returns immediately once the rootfs
     * marker is in place, so this is a cheap no-op on every launch after the first.
     */
    /** Whether this process has already run the refresh for an existing environment. */
    private var refreshed = false

    fun ensureShellReady() {
        val current = _shell.value
        if (current.state == ShellState.MIGRATE_OFFER || current.state == ShellState.MIGRATING) return
        if (current.state == ShellState.READY) {
            // Already unpacked: still run provision's cheap path once per process. It is what
            // brings an existing install up to date after an app update (setup script, git
            // defaults, helper libraries); skipping it left those only on fresh installs.
            if (!refreshed) {
                refreshed = true
                viewModelScope.launch(Dispatchers.IO) {
                    runCatching { ShellRuntime.provision(getApplication()) }
                        .onFailure { Log.w("AppViewModel", "Environment refresh failed", it) }
                }
            }
            return
        }
        if (current.state == ShellState.PREPARING) return

        _shell.value = ShellProgress(state = ShellState.PREPARING, step = "", fraction = 0f)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    ShellRuntime.provision(getApplication()) { step, fraction ->
                        _shell.update { it.copy(step = step, fraction = fraction) }
                    }
                }
            }
            _shell.value = result.fold(
                onSuccess = {
                    ShellProgress(state = ShellState.READY, step = "", fraction = 1f)
                },
                onFailure = { error ->
                    Log.e("AppViewModel", "Linux setup failed", error)
                    ShellProgress(
                        state = ShellState.FAILED,
                        error = error.message ?: error.javaClass.simpleName,
                    )
                },
            )
        }
    }

    /** Clears a previous failure and tries the setup again. */
    fun retryShell() {
        if (migrationFailed) {
            startMigration()
            return
        }
        _shell.value = ShellProgress(state = ShellState.UNKNOWN)
        ensureShellReady()
    }

    /**
     * Makes the project list match `~/projects`: a folder created there from a shell appears,
     * one deleted there disappears, and entries for folders outside the Linux environment
     * (the old Android-storage folders) are dropped. The files of those stay on the device.
     */
    fun syncProjects() {
        val app = getApplication<Application>()
        if (!ShellRuntime.isReady(app)) return
        viewModelScope.launch(Dispatchers.IO) {
            val dirs = Projects.dir(app).apply { mkdirs() }
                .listFiles { f -> f.isDirectory && !f.name.startsWith(".") }
                .orEmpty()
                .sortedBy { it.name.lowercase() }
            store.update { data ->
                val kept = data.folders.filter { f ->
                    Projects.isInGuest(app, f.path) && java.io.File(f.path).isDirectory
                }
                val known = kept.map { java.io.File(it.path).absolutePath }.toSet()
                val added = dirs.filter { it.absolutePath !in known }.mapIndexed { i, dir ->
                    newFolder(dir, dir.name, (kept.size + i) % 6)
                }
                (kept + added).forEach { folder ->
                    com.termfold.app.gitworkspace.TermfoldGitignore.ensureTermfoldIgnored(java.io.File(folder.path))
                }
                if (added.isEmpty() && kept.size == data.folders.size) data else data.copy(folders = kept + added)
            }
        }
    }

    private fun newFolder(dir: java.io.File, name: String, tint: Int) = Folder(
        id = Ids.new(),
        name = name,
        path = dir.absolutePath,
        tint = tint,
        sessions = defaultSessions(),
    )

    private suspend fun addProject(dir: java.io.File, name: String): String {
        val folder = newFolder(dir, name, folders.value.folders.size % 6)
        store.update { data -> data.copy(folders = data.folders + folder) }
        return folder.id
    }

    /** An empty project. Calls [onCreated] with its id. */
    fun createProject(name: String, onCreated: (String) -> Unit = {}) {
        viewModelScope.launch {
            val dir = withContext(Dispatchers.IO) { Projects.create(getApplication(), name) }
            onCreated(addProject(dir, name.trim().ifEmpty { dir.name }))
        }
    }

    /** Clones a git repository into a new project, reporting progress through [job]. */
    fun cloneProject(url: String, name: String) {
        val app = getApplication<Application>()
        val display = name.trim().ifEmpty {
            url.trimEnd('/').substringAfterLast('/').removeSuffix(".git").ifEmpty { "project" }
        }
        _job.value = ProjectJob(title = display, progress = "Starting…")
        viewModelScope.launch {
            val dir = withContext(Dispatchers.IO) { Projects.newDirFor(app, display) }
            val error = withContext(Dispatchers.IO) {
                runCatching {
                    Projects.clone(app, url.trim(), dir) { line -> _job.update { it?.copy(progress = line) } }
                }.getOrElse { it.message ?: "clone failed" }
            }
            if (error != null) {
                withContext(Dispatchers.IO) { dir.deleteRecursively() }
                _job.update { it?.copy(error = error) }
            } else {
                val id = addProject(dir, display)
                _job.update { it?.copy(done = true, folderId = id, progress = "") }
            }
        }
    }

    /** Copies a folder from the device into a new project, reporting progress through [job]. */
    fun importProject(treeUri: Uri) {
        val app = getApplication<Application>()
        val display = androidx.documentfile.provider.DocumentFile.fromTreeUri(app, treeUri)?.name
            ?.takeIf { it.isNotBlank() } ?: "Imported"
        _job.value = ProjectJob(title = display, progress = "Copying…")
        viewModelScope.launch {
            val dir = withContext(Dispatchers.IO) { Projects.newDirFor(app, display) }
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    Projects.importTree(app, treeUri, dir) { p -> _job.update { it?.copy(progress = p) } }
                }
            }
            result.onSuccess { count ->
                val id = addProject(dir, display)
                _job.update { it?.copy(done = true, folderId = id, progress = "$count files") }
            }.onFailure { e ->
                withContext(Dispatchers.IO) { dir.deleteRecursively() }
                _job.update { it?.copy(error = e.message ?: "import failed") }
            }
        }
    }

    fun renameFolder(folderId: String, name: String) {
        viewModelScope.launch {
            store.update { data ->
                data.copy(
                    folders = data.folders.map {
                        if (it.id == folderId) it.copy(name = name) else it
                    }
                )
            }
        }
    }

    fun removeFolder(folderId: String) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val folder = folders.value.folder(folderId)
            store.update { data ->
                data.copy(folders = data.folders.filterNot { it.id == folderId })
            }
            // A project's files live only inside the app, so removing it deletes them (the
            // confirmation says so). Anything outside ~/projects is never touched.
            val path = folder?.path
            if (path != null) {
                withContext(Dispatchers.IO) {
                    val dir = java.io.File(path)
                    val projects = Projects.dir(app).absolutePath + "/"
                    if (dir.absolutePath.startsWith(projects)) dir.deleteRecursively()
                }
            }
        }
    }

    fun addSession(
        folderId: String,
        name: String,
        command: String,
        acpAgentId: String = "",
        workspaceId: String = com.termfold.app.gitworkspace.WorkspaceIds.GOLDEN,
        sessionId: String = Ids.new(),
    ) {
        viewModelScope.launch {
            store.update { data ->
                data.copy(
                    folders = data.folders.map { folder ->
                        if (folder.id != folderId) return@map folder
                        val session = Session(
                            id = sessionId,
                            name = name,
                            command = command,
                            tint = folder.sessions.size % 6,
                            acpAgentId = acpAgentId,
                            workspaceId = workspaceId,
                        )
                        folder.copy(sessions = folder.sessions + session)
                    }
                )
            }
        }
    }

    fun removeSession(folderId: String, sessionId: String) {
        viewModelScope.launch {
            var workspaceId = com.termfold.app.gitworkspace.WorkspaceIds.GOLDEN
            store.update { data ->
                data.copy(
                    folders = data.folders.map { folder ->
                        if (folder.id != folderId) return@map folder
                        workspaceId = folder.sessions.find { it.id == sessionId }?.workspaceId
                            ?: workspaceId
                        folder.copy(sessions = folder.sessions.filterNot { it.id == sessionId })
                    }
                )
            }
            withContext(Dispatchers.IO) {
                val compose = com.termfold.app.gitworkspace.SessionKeys.compose(folderId, workspaceId, sessionId)
                com.termfold.app.preview.network.NetworkRuleStore.forApp(getApplication())
                    .deleteSession(com.termfold.app.preview.engine.PreviewUrlRouter.sessionKeyForCoding(compose))
                com.termfold.app.preview.network.NetworkRulesHub.notifyChanged()
            }
        }
    }

    /** Sessions every new folder starts with, matching the agent presets. */
    private fun defaultSessions(): List<Session> = listOf(
        Session(Ids.new(), "Shell", "", tint = 0),
        Session(Ids.new(), "OpenCode", "opencode", tint = 1),
        Session(Ids.new(), "Pi", "pi", tint = 2),
    )

    fun createAutomationProject(name: String, onCreated: (String) -> Unit = {}) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val id = Ids.new()
            withContext(Dispatchers.IO) {
                AutomationPaths.hostProjectDir(app, id).mkdirs()
            }
            val project = AutomationProject(
                id = id,
                name = name.trim().ifEmpty { "Automation" },
                tint = automation.value.projects.size % 6,
            )
            automationStore.update { data -> data.copy(projects = data.projects + project) }
            onCreated(id)
        }
    }

    fun addAutomationSession(projectId: String, name: String, onCreated: (String) -> Unit = {}) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val sessionId = Ids.new()
            withContext(Dispatchers.IO) {
                AutomationStorage.ensureSessionTree(app, projectId, sessionId)
            }
            automationStore.update { data ->
                data.copy(
                    projects = data.projects.map { project ->
                        if (project.id != projectId) return@map project
                        val session = AutomationSession(
                            id = sessionId,
                            name = name.trim().ifEmpty { "Session" },
                            tint = project.sessions.size % 6,
                        )
                        project.copy(sessions = project.sessions + session)
                    },
                )
            }
            onCreated(sessionId)
        }
    }

    fun linkAutomationFolder(projectId: String, folderId: String?) {
        viewModelScope.launch {
            automationStore.update { data ->
                data.copy(
                    projects = data.projects.map { project ->
                        if (project.id != projectId) project
                        else project.copy(linkedFolderId = folderId)
                    },
                )
            }
        }
    }

    fun removeAutomationProject(projectId: String) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            automationStore.update { data ->
                data.copy(projects = data.projects.filterNot { it.id == projectId })
            }
            withContext(Dispatchers.IO) {
                AutomationPaths.hostProjectDir(app, projectId).deleteRecursively()
                val store = com.termfold.app.preview.network.NetworkRuleStore.forApp(app)
                store.allSessions().keys.filter { it.startsWith("$projectId:") }.forEach { store.deleteSession(it) }
                com.termfold.app.preview.network.NetworkRulesHub.notifyChanged()
            }
        }
    }

    fun removeAutomationSession(projectId: String, sessionId: String) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            automationStore.update { data ->
                data.copy(
                    projects = data.projects.map { project ->
                        if (project.id != projectId) return@map project
                        project.copy(sessions = project.sessions.filterNot { it.id == sessionId })
                    },
                )
            }
            withContext(Dispatchers.IO) {
                AutomationPaths.hostSessionDir(app, projectId, sessionId).deleteRecursively()
                com.termfold.app.preview.network.NetworkRuleStore.forApp(app)
                    .deleteSession(com.termfold.app.automation.AutomationSessionContext.key(projectId, sessionId))
                com.termfold.app.preview.network.NetworkRulesHub.notifyChanged()
            }
        }
    }

    fun dismissPrReviewJob() {
        val job = _prReviewJob.value ?: return
        if (job.error != null || job.doneSessionId != null) _prReviewJob.value = null
    }

    fun cancelPrReviewBind() {
        prReviewBindJob?.cancel()
    }

    fun bindPrReview(url: String, existingSessionId: String? = null, onReady: (String) -> Unit = {}) {
        val app = getApplication<Application>()
        prReviewBindJob?.cancel()
        prReviewBindJob = viewModelScope.launch {
            var partialFolderId: String? = null
            var partialWorkspaceId: String? = null
            var partialGolden: String? = null
            var partialGuest: String? = null
            var createdSessionId: String? = null
            try {
                val ref = GitHubPrUrl.parse(url)
                if (ref == null) {
                    _prReviewJob.value = PrReviewJob(
                        title = "PR review",
                        error = "Paste a GitHub pull request link (github.com/owner/repo/pull/123).",
                    )
                    return@launch
                }
                _prReviewJob.value = PrReviewJob(title = "PR review", step = "Reading pull request…")
                val fetch = withContext(Dispatchers.IO) {
                    GitHubPrApi.fetch(
                        app,
                        ref,
                        onProgress = { step ->
                            _prReviewJob.value = PrReviewJob(title = "PR review", step = step)
                        },
                    )
                }
                val info = when (fetch) {
                    is GitHubPrFetch.Ok -> fetch.info
                    is GitHubPrFetch.Error -> {
                        _prReviewJob.value = PrReviewJob(title = "PR review", error = fetch.message)
                        return@launch
                    }
                }
                val origins = folders.value.folders.map { folder ->
                    val guest = WorkspaceStore.goldenGuestPath(app, folder)
                    val origin = GitWorkspace.originUrl(app, guest).orEmpty()
                    folder.id to origin
                }
                val match = GitHubOrigin.match(ref.repoKey, origins)
                val existing = existingSessionId?.let { prReviews.value.session(it) }
                val copyId = existing?.workspaceId
                val copyDirty = if (existing != null && copyId != null) {
                    val folder = folders.value.folder(existing.folderId)
                    val guest = folder?.let { WorkspaceStore.guestPath(app, it, copyId) }
                    guest != null && GitWorkspace.isDirty(app, guest)
                } else {
                    false
                }
                when (
                    val action = PrReviewBind.decide(
                        incoming = ref,
                        headBranch = info.headRef,
                        match = match,
                        session = existing,
                        copyWorkspaceId = copyId,
                        copyDirty = copyDirty,
                    )
                ) {
                    is BindAction.Error -> {
                        _prReviewJob.value = PrReviewJob(title = "PR review", error = action.message)
                    }
                    is BindAction.ReuseSwitch -> {
                        val session = existing ?: return@launch
                        val folder = folders.value.folder(session.folderId) ?: return@launch
                        val guest = WorkspaceStore.guestPath(app, folder, action.workspaceId)
                        if (guest == null) {
                            _prReviewJob.value = PrReviewJob(
                                title = "PR review",
                                error = "The temp copy is missing. Tap Done and paste the PR again.",
                            )
                            return@launch
                        }
                        _prReviewJob.value = PrReviewJob(title = "PR review", step = "Switching to ${action.branch}…")
                        GitWorkspace.checkoutBranch(app, guest, action.branch, CheckoutMode.FAIL_IF_DIRTY)
                            .getOrElse { err ->
                                _prReviewJob.value = PrReviewJob(
                                    title = "PR review",
                                    error = err.message ?: PrReviewBind.ERR_DIRTY_SWITCH,
                                )
                                return@launch
                            }
                        WorkspaceStore.updateBranch(app, folder.id, action.workspaceId, action.branch)
                        _prReviewJob.value = PrReviewJob(title = "PR review", step = "Preparing review files…")
                        withContext(Dispatchers.IO) {
                            writeReviewFactsPack(app, guest, info, ref)
                        }
                        upsertReviewSession(
                            existing.boundToNewPr(
                                info = info,
                                ref = ref,
                                headBranch = action.branch,
                                workspaceId = action.workspaceId,
                            ),
                        )
                        _prReviewJob.value = PrReviewJob(
                            title = "PR review",
                            doneSessionId = session.id,
                            percent = 100,
                        )
                        onReady(session.id)
                    }
                    is BindAction.Provision -> {
                        val folder = folders.value.folder(action.folderId)
                        if (folder == null) {
                            _prReviewJob.value = PrReviewJob(title = "PR review", error = PrReviewBind.ERR_NOT_CLONED)
                            return@launch
                        }
                        val sessionId = existing?.id ?: Ids.new()
                        if (existing == null) createdSessionId = sessionId
                        withContext(Dispatchers.IO) { PrReviewPaths.ensureStub(app, sessionId) }
                        val golden = WorkspaceStore.goldenGuestPath(app, folder)
                        GitWorkspace.fetchOrigin(app, golden)
                        val workspaceId = ProjectWorkspaces.newSessionId()
                        val guest = ProjectWorkspaces.sessionGuestPath(golden, workspaceId)
                        partialFolderId = folder.id
                        partialWorkspaceId = workspaceId
                        partialGolden = golden
                        partialGuest = guest
                        val record = WorkspaceRecord(
                            id = workspaceId,
                            goldenProjectId = folder.id,
                            guestPath = guest,
                            createdAt = System.currentTimeMillis(),
                            sourceBranch = action.branch,
                            lastKnownBranch = action.branch,
                            complete = false,
                            label = "PR #${ref.number}",
                        )
                        WorkspaceStore.insert(app, record)
                        val session = (existing ?: PrReviewSession(
                            id = sessionId,
                            name = info.title,
                            url = info.htmlUrl,
                            owner = ref.owner,
                            repo = ref.repo,
                            number = ref.number,
                            headBranch = action.branch,
                            folderId = folder.id,
                            tint = prReviews.value.sessions.size % 6,
                        )).boundToNewPr(
                            info = info,
                            ref = ref,
                            headBranch = action.branch,
                            folderId = folder.id,
                            workspaceId = null,
                        ).copy(
                            id = sessionId,
                            acpAgentId = existing?.acpAgentId ?: "cursor",
                            tint = existing?.tint ?: (prReviews.value.sessions.size % 6),
                        )
                        upsertReviewSession(session)
                        GitWorkspace.provisionWorkspace(
                            context = app,
                            goldenGuestPath = golden,
                            branch = action.branch,
                            sessionWorkspaceId = workspaceId,
                            onProgress = { progress ->
                                _prReviewJob.value = PrReviewJob(
                                    title = "PR review",
                                    step = "Copying ${action.branch}…",
                                    percent = progress.percent,
                                )
                            },
                        )
                        WorkspaceStore.insert(app, record.copy(complete = true))
                        _prReviewJob.value = PrReviewJob(
                            title = "PR review",
                            step = "Preparing review files…",
                            percent = 100,
                        )
                        withContext(Dispatchers.IO) {
                            writeReviewFactsPack(app, guest, info, ref)
                        }
                        upsertReviewSession(session.copy(workspaceId = workspaceId))
                        _prReviewJob.value = PrReviewJob(
                            title = "PR review",
                            doneSessionId = sessionId,
                            percent = 100,
                        )
                        onReady(sessionId)
                    }
                }
            } catch (e: CancellationException) {
                val golden = partialGolden
                val guest = partialGuest
                val folderId = partialFolderId
                val workspaceId = partialWorkspaceId
                if (golden != null && guest != null) {
                    GitWorkspace.deletePartialWorkspace(app, golden, guest)
                }
                if (folderId != null && workspaceId != null) {
                    WorkspaceStore.remove(app, folderId, workspaceId)
                }
                createdSessionId?.let { id -> prReviewStore.update { it.without(id) } }
                _prReviewJob.value = null
                throw e
            } catch (e: Throwable) {
                val golden = partialGolden
                val guest = partialGuest
                val folderId = partialFolderId
                val workspaceId = partialWorkspaceId
                if (golden != null && guest != null) {
                    GitWorkspace.deletePartialWorkspace(app, golden, guest)
                }
                if (folderId != null && workspaceId != null) {
                    WorkspaceStore.remove(app, folderId, workspaceId)
                }
                createdSessionId?.let { id -> prReviewStore.update { it.without(id) } }
                _prReviewJob.value = PrReviewJob(
                    title = "PR review",
                    error = e.message ?: e.toString(),
                )
            }
        }
    }

    suspend fun reviewDoneKind(sessionId: String): DoneKind {
        val app = getApplication<Application>()
        val session = prReviews.value.session(sessionId) ?: return DoneKind.AlreadyGone
        val workspaceId = session.workspaceId ?: return DoneKind.AlreadyGone
        val folder = folders.value.folder(session.folderId) ?: return DoneKind.AlreadyGone
        val guest = WorkspaceStore.guestPath(app, folder, workspaceId) ?: return DoneKind.AlreadyGone
        val dirty = GitWorkspace.isDirty(app, guest)
        val unpushed = GitWorkspace.hasUnpushedCommits(app, guest)
        return PrReviewBind.decideDone(copyExists = true, dirty = dirty, unpushed = unpushed)
    }

    fun applyReviewDone(sessionId: String) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val session = prReviews.value.session(sessionId) ?: return@launch
            val workspaceId = session.workspaceId
            val folder = folders.value.folder(session.folderId)
            if (workspaceId != null && folder != null) {
                val golden = WorkspaceStore.goldenGuestPath(app, folder)
                val guest = WorkspaceStore.guestPath(app, folder, workspaceId)
                if (guest != null) {
                    GitWorkspace.deletePartialWorkspace(app, golden, guest)
                }
                WorkspaceStore.remove(app, folder.id, workspaceId)
            }
            upsertReviewSession(session.copy(workspaceId = null))
            withContext(Dispatchers.IO) { PrReviewPaths.ensureStub(app, sessionId) }
        }
    }

    fun removePrReviewSession(sessionId: String) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            com.termfold.app.acp.AcpSessions.close(
                com.termfold.app.prreview.PrReviewKeys.acpSessionKey(sessionId),
            )
            val session = prReviews.value.session(sessionId)
            if (session?.workspaceId != null) {
                val folder = folders.value.folder(session.folderId)
                if (folder != null) {
                    val golden = WorkspaceStore.goldenGuestPath(app, folder)
                    val guest = WorkspaceStore.guestPath(app, folder, session.workspaceId)
                    if (golden != null && guest != null) {
                        GitWorkspace.deletePartialWorkspace(app, golden, guest)
                    }
                    WorkspaceStore.remove(app, folder.id, session.workspaceId)
                }
            }
            withContext(Dispatchers.IO) {
                com.termfold.app.prreview.PrReviewPaths.hostSessionDir(app, sessionId).deleteRecursively()
            }
            prReviewStore.update { it.without(sessionId) }
        }
    }

    fun saveReviewParse(sessionId: String, userMessageId: Long, parse: PrReviewParse) {
        viewModelScope.launch {
            val session = prReviews.value.session(sessionId) ?: return@launch
            upsertReviewSession(PrReviewTurnSync.apply(session, parse, userMessageId))
        }
    }

    fun syncPrReviewReportsToTimeline(sessionId: String, items: List<AcpItem>, localReports: Map<Long, String>) {
        viewModelScope.launch {
            val session = prReviews.value.session(sessionId) ?: return@launch
            val merged = session.reportsByUserMessageId + localReports
            val next = PrReviewTurnSync.realignToTimeline(session.copy(reportsByUserMessageId = merged), items)
            if (next != session) upsertReviewSession(next)
        }
    }

    private fun writeReviewFactsPack(
        app: Application,
        guest: String,
        info: GitHubPrInfo,
        ref: GitHubPrRef,
    ) {
        val comments = runCatching { GitHubPrApi.fetchComments(app, ref) }
            .getOrElse { "No GitHub comments.\n" }
        runCatching { PrReviewFactsPack.writeOnGuest(app, guest, info, comments) }
    }

    private suspend fun upsertReviewSession(session: PrReviewSession) {
        prReviewStore.update { it.upsert(session) }
    }

    fun dismissPmJob() {
        val job = _pmJob.value ?: return
        if (job.error != null || job.doneSessionId != null) _pmJob.value = null
    }

    fun cancelPmBind() {
        pmBindJob?.cancel()
    }

    fun startPmDiscovery(folderId: String, name: String, onReady: (String) -> Unit = {}) {
        pmBindJob?.cancel()
        pmBindJob = viewModelScope.launch {
            val app = getApplication<Application>()
            try {
                _pmJob.value = PmJob(title = "Product Manager", step = "Linking project…")
                val folder = folders.value.folder(folderId)
                    ?: throw IllegalStateException("Project not found")
                val golden = WorkspaceStore.goldenGuestPath(app, folder)
                    ?: throw IllegalStateException("Workspace unavailable")
                val id = UUID.randomUUID().toString()
                val viewBranch = PmRepoResearch.defaultViewBranch(app, golden)
                val session = PmSession(
                    id = id,
                    name = name.ifBlank { "Discovery" },
                    folderId = folderId,
                    mode = PmMode.DISCOVERY,
                    viewBranch = viewBranch,
                    tint = pmSessions.value.sessions.size % 6,
                    createdAtMs = System.currentTimeMillis(),
                    updatedAtMs = System.currentTimeMillis(),
                )
                withContext(Dispatchers.IO) {
                    PmPaths.ensureStub(app, id)
                    PmBriefFiles.ensureDefaults(app, session)
                }
                upsertPmSession(session)
                _pmJob.value = PmJob(title = "Product Manager", doneSessionId = id, percent = 100)
                onReady(id)
            } catch (e: CancellationException) {
                _pmJob.value = null
                throw e
            } catch (e: Throwable) {
                _pmJob.value = PmJob(title = "Product Manager", error = e.message ?: e.toString())
            }
        }
    }

    fun startPmRelease(
        folderId: String,
        branch: String,
        name: String,
        releaseLabel: String = "",
        onReady: (String) -> Unit = {},
    ) {
        pmBindJob?.cancel()
        pmBindJob = viewModelScope.launch {
            val app = getApplication<Application>()
            var partialGolden: String? = null
            var partialGuest: String? = null
            var partialFolderId: String? = null
            var partialWorkspaceId: String? = null
            var createdId: String? = null
            try {
                _pmJob.value = PmJob(title = "Product Manager", step = "Preparing release copy…")
                val folder = folders.value.folder(folderId)
                    ?: throw IllegalStateException("Project not found")
                val golden = WorkspaceStore.goldenGuestPath(app, folder)
                    ?: throw IllegalStateException("Workspace unavailable")
                partialGolden = golden
                partialFolderId = folderId
                val id = UUID.randomUUID().toString()
                createdId = id
                val workspaceId = ProjectWorkspaces.newSessionId()
                partialWorkspaceId = workspaceId
                val guest = ProjectWorkspaces.sessionGuestPath(golden, workspaceId)
                partialGuest = guest
                GitWorkspace.fetchOrigin(app, golden)
                val session = PmSession(
                    id = id,
                    name = name.ifBlank { releaseLabel.ifBlank { branch } },
                    folderId = folderId,
                    mode = PmMode.RELEASE,
                    viewBranch = branch,
                    branch = branch,
                    releaseLabel = releaseLabel,
                    workspaceId = workspaceId,
                    tint = pmSessions.value.sessions.size % 6,
                    createdAtMs = System.currentTimeMillis(),
                    updatedAtMs = System.currentTimeMillis(),
                )
                withContext(Dispatchers.IO) {
                    PmPaths.ensureStub(app, id)
                    PmBriefFiles.ensureDefaults(app, session)
                }
                upsertPmSession(session)
                val record = WorkspaceRecord(
                    id = workspaceId,
                    goldenProjectId = folderId,
                    guestPath = guest,
                    createdAt = System.currentTimeMillis(),
                    sourceBranch = branch,
                    lastKnownBranch = branch,
                    complete = false,
                    label = "PM release",
                )
                WorkspaceStore.insert(app, record)
                GitWorkspace.provisionWorkspace(
                    context = app,
                    goldenGuestPath = golden,
                    branch = branch,
                    sessionWorkspaceId = workspaceId,
                    onProgress = { progress ->
                        _pmJob.value = PmJob(
                            title = "Product Manager",
                            step = "Copying $branch…",
                            percent = progress.percent,
                        )
                    },
                )
                WorkspaceStore.insert(app, record.copy(complete = true))
                upsertPmSession(session.copy(workspaceId = workspaceId))
                _pmJob.value = PmJob(title = "Product Manager", doneSessionId = id, percent = 100)
                onReady(id)
            } catch (e: CancellationException) {
                partialGolden?.let { g ->
                    partialGuest?.let { guest ->
                        GitWorkspace.deletePartialWorkspace(app, g, guest)
                    }
                }
                if (partialFolderId != null && partialWorkspaceId != null) {
                    WorkspaceStore.remove(app, partialFolderId!!, partialWorkspaceId!!)
                }
                createdId?.let { upsertPmSessionRemoval(it) }
                _pmJob.value = null
                throw e
            } catch (e: Throwable) {
                partialGolden?.let { g ->
                    partialGuest?.let { guest ->
                        GitWorkspace.deletePartialWorkspace(app, g, guest)
                    }
                }
                if (partialFolderId != null && partialWorkspaceId != null) {
                    WorkspaceStore.remove(app, partialFolderId!!, partialWorkspaceId!!)
                }
                createdId?.let { upsertPmSessionRemoval(it) }
                _pmJob.value = PmJob(title = "Product Manager", error = e.message ?: e.toString())
            }
        }
    }

    fun updatePmSession(session: PmSession) {
        viewModelScope.launch { upsertPmSession(session) }
    }

    fun setPmViewBranch(sessionId: String, branch: String) {
        viewModelScope.launch {
            val session = pmSessions.value.session(sessionId) ?: return@launch
            val next = PmResearchOrchestrator.invalidateOnViewBranchChange(session, branch)
            upsertPmSession(next)
            refreshPmResearch(sessionId, force = true, showProgress = false)
        }
    }

    fun refreshPmResearch(sessionId: String, force: Boolean = true, showProgress: Boolean = false) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            try {
                val session = pmSessions.value.session(sessionId) ?: return@launch
                val folder = folders.value.folder(session.folderId) ?: return@launch
                val golden = WorkspaceStore.goldenGuestPath(app, folder) ?: return@launch
                if (showProgress) {
                    _pmJob.value = PmJob(title = "Product Manager", step = "Research…")
                }
                val updated = PmResearchOrchestrator.runResearch(
                    context = app,
                    session = session,
                    goldenGuestPath = golden,
                    forceRefresh = force,
                    onProgress = { step ->
                        if (showProgress) {
                            _pmJob.value = PmJob(title = "Product Manager", step = step)
                        }
                    },
                )
                upsertPmSession(updated)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (showProgress) {
                    _pmJob.value = PmJob(title = "Product Manager", error = e.message ?: e.toString())
                }
            } finally {
                if (showProgress && _pmJob.value?.error == null) {
                    _pmJob.value = null
                }
            }
        }
    }

    fun removePmSession(sessionId: String) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            com.termfold.app.acp.AcpSessions.close(
                com.termfold.app.pm.PmKeys.acpSessionKey(sessionId),
            )
            val session = pmSessions.value.session(sessionId)
            if (session?.workspaceId != null) {
                val folder = folders.value.folder(session.folderId)
                if (folder != null) {
                    val golden = WorkspaceStore.goldenGuestPath(app, folder)
                    val guest = WorkspaceStore.guestPath(app, folder, session.workspaceId)
                    if (golden != null && guest != null) {
                        GitWorkspace.deletePartialWorkspace(app, golden, guest)
                    }
                    WorkspaceStore.remove(app, folder.id, session.workspaceId)
                }
            }
            withContext(Dispatchers.IO) {
                PmMemoryStore.deleteSession(app, sessionId)
                PmPaths.hostSessionDir(app, sessionId).deleteRecursively()
            }
            upsertPmSessionRemoval(sessionId)
        }
    }

    suspend fun pmDoneKind(sessionId: String): DoneKind {
        val app = getApplication<Application>()
        val session = pmSessions.value.session(sessionId) ?: return DoneKind.AlreadyGone
        val workspaceId = session.workspaceId ?: return DoneKind.AlreadyGone
        val folder = folders.value.folder(session.folderId) ?: return DoneKind.AlreadyGone
        val guest = WorkspaceStore.guestPath(app, folder, workspaceId) ?: return DoneKind.AlreadyGone
        val dirty = GitWorkspace.isDirty(app, guest)
        val unpushed = GitWorkspace.hasUnpushedCommits(app, guest)
        return PmBind.decideDone(copyExists = true, dirty = dirty, unpushed = unpushed)
    }

    fun applyPmDone(sessionId: String) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val session = pmSessions.value.session(sessionId) ?: return@launch
            val workspaceId = session.workspaceId
            val folder = folders.value.folder(session.folderId)
            if (workspaceId != null && folder != null) {
                val golden = WorkspaceStore.goldenGuestPath(app, folder)
                val guest = WorkspaceStore.guestPath(app, folder, workspaceId)
                if (golden != null && guest != null) {
                    GitWorkspace.deletePartialWorkspace(app, golden, guest)
                }
                WorkspaceStore.remove(app, folder.id, workspaceId)
            }
            upsertPmSession(session.copy(workspaceId = null))
            withContext(Dispatchers.IO) { PmPaths.ensureStub(app, sessionId) }
        }
    }

    private suspend fun upsertPmSession(session: PmSession) {
        pmSessionStore.update { it.upsert(session.copy(updatedAtMs = System.currentTimeMillis())) }
    }

    private suspend fun upsertPmSessionRemoval(sessionId: String) {
        pmSessionStore.update { it.without(sessionId) }
    }

    fun createPdfToolsSession(
        jobType: com.termfold.app.pdftools.PdfToolsJobType,
        name: String,
        onReady: (String) -> Unit = {},
    ) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val id = UUID.randomUUID().toString()
            val session = com.termfold.app.pdftools.PdfToolsSession(
                id = id,
                name = name.ifBlank {
                    if (jobType == com.termfold.app.pdftools.PdfToolsJobType.GENERATE) "Letter" else "PDF"
                },
                jobType = jobType,
                authzClass = if (jobType == com.termfold.app.pdftools.PdfToolsJobType.GENERATE) {
                    com.termfold.app.pdftools.PdfToolsAuthzClass.OWN_TEMPLATE
                } else {
                    com.termfold.app.pdftools.PdfToolsAuthzClass.OWN_DATA_DOC
                },
                tint = pdfTools.value.sessions.size % 6,
                createdAtMs = System.currentTimeMillis(),
                updatedAtMs = System.currentTimeMillis(),
            )
            withContext(Dispatchers.IO) {
                com.termfold.app.pdftools.PdfToolsPaths.ensureStub(app, id)
            }
            pdfToolsStore.update { it.upsert(session) }
            onReady(id)
        }
    }

    fun updatePdfToolsSession(session: com.termfold.app.pdftools.PdfToolsSession) {
        viewModelScope.launch {
            pdfToolsStore.update { it.upsert(session.copy(updatedAtMs = System.currentTimeMillis())) }
        }
    }

    fun removePdfToolsSession(sessionId: String) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            com.termfold.app.acp.AcpSessions.close(
                com.termfold.app.pdftools.PdfToolsKeys.acpSessionKey(sessionId),
            )
            withContext(Dispatchers.IO) {
                com.termfold.app.pdftools.PdfToolsPaths.hostSessionDir(app, sessionId).deleteRecursively()
            }
            pdfToolsStore.update { it.without(sessionId) }
        }
    }

    fun importPdfToSession(sessionId: String, source: File, password: String? = null) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            _pdfToolsJob.value = PdfToolsJob(title = "PDF Tools", step = "Importing…")
            val session = pdfTools.value.session(sessionId) ?: return@launch
            withContext(Dispatchers.IO) {
                val libs = com.termfold.app.pdftools.PdfToolsGuest.ensureBasicLibraries(app)
                if (libs.first == 0) _pdfToolsLibrariesReady.value = true
                val dir = com.termfold.app.pdftools.PdfToolsPaths.ensureStub(app, sessionId)
                val copied = com.termfold.app.pdftools.PdfToolsImport.copy(
                    source,
                    com.termfold.app.pdftools.PdfToolsPaths.inputDir(dir),
                )
                if (copied.error != null) {
                    _pdfToolsJob.value = PdfToolsJob(title = "PDF Tools", error = copied.error)
                    return@withContext
                }
                com.termfold.app.pdftools.PdfToolsFields.ensure(
                    com.termfold.app.pdftools.PdfToolsPaths.fieldsFile(dir),
                )
                val inspectOut = com.termfold.app.pdftools.PdfToolsPaths.inspectFile(dir)
                if (inspectOut.exists()) inspectOut.delete()
                val args = buildList {
                    add("inspect")
                    add(com.termfold.app.pdftools.PdfToolsPaths.sessionGuest(sessionId) + "/input/source.pdf")
                    add("--out")
                    add(com.termfold.app.pdftools.PdfToolsPaths.sessionGuest(sessionId) + "/inspect.json")
                    if (!password.isNullOrBlank()) {
                        add("--password")
                        add(password)
                    }
                }
                val inspectRun = com.termfold.app.pdftools.PdfToolsGuest.run(app, args)
                var inspectRaw = inspectOut.takeIf { it.isFile }?.readText()
                    .orEmpty()
                    .ifBlank { inspectRun.second }
                var summary = if (inspectRaw.isNotBlank()) {
                    com.termfold.app.pdftools.PdfToolsInspect.parse(inspectRaw)
                } else {
                    com.termfold.app.pdftools.PdfToolsInspectSummary(ok = false, error = "inspect missing")
                }
                if (!summary.ok || summary.code == "MISSING_DEPS") {
                    inspectRaw = com.termfold.app.pdftools.PdfToolsImport.fallbackInspectJson(copied.dest!!)
                    summary = com.termfold.app.pdftools.PdfToolsInspect.parse(inspectRaw)
                    inspectOut.writeText(inspectRaw)
                }
                pdfToolsStore.update {
                    it.upsert(
                        session.copy(
                            inputSha256 = copied.sha256,
                            inspectJson = inspectRaw.ifBlank { null },
                            encrypted = summary.encrypted,
                            updatedAtMs = System.currentTimeMillis(),
                        ),
                    )
                }
                _pdfToolsJob.value = if (summary.code == "ENCRYPTED") {
                    PdfToolsJob(title = "PDF Tools", error = "encrypted")
                } else {
                    PdfToolsJob(title = "PDF Tools", doneSessionId = sessionId, percent = 100)
                }
            }
        }
    }

    fun generatePdfSession(sessionId: String, fields: Map<String, String>) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val session = pdfTools.value.session(sessionId) ?: return@launch
            val blocked = com.termfold.app.pdftools.PdfToolsAuthz.blockReason(session.authzClass)
            if (blocked != null) {
                _pdfToolsJob.value = PdfToolsJob(title = "PDF Tools", error = blocked)
                return@launch
            }
            _pdfToolsJob.value = PdfToolsJob(title = "PDF Tools", step = "Generating…")
            if (!ensurePdfsysLibraries()) return@launch
            withContext(Dispatchers.IO) {
                val dir = com.termfold.app.pdftools.PdfToolsPaths.ensureStub(app, sessionId)
                com.termfold.app.pdftools.PdfToolsFields.write(
                    com.termfold.app.pdftools.PdfToolsPaths.fieldsFile(dir),
                    fields,
                )
                val guest = com.termfold.app.pdftools.PdfToolsPaths.sessionGuest(sessionId)
                val generated = com.termfold.app.pdftools.PdfToolsGuest.run(
                    app,
                    listOf(
                        "generate",
                        "--fields", "$guest/fields.json",
                        "--fonts", com.termfold.app.pdftools.PdfToolsPaths.FONTS_GUEST,
                        "-o", "$guest/out/output.pdf",
                    ),
                )
                if (generated.first != 0) {
                    _pdfToolsJob.value = PdfToolsJob(
                        title = "PDF Tools",
                        error = generated.second.takeLast(400).ifBlank { "Generate failed" },
                    )
                    return@withContext
                }
                val verified = com.termfold.app.pdftools.PdfToolsGuest.run(
                    app,
                    listOf(
                        "verify",
                        "$guest/out/output.pdf",
                        "--report", "$guest/report.md",
                    ),
                )
                if (verified.first != 0) {
                    _pdfToolsJob.value = PdfToolsJob(
                        title = "PDF Tools",
                        error = verified.second.takeLast(400).ifBlank { "Verify failed" },
                    )
                    return@withContext
                }
                val outFile = com.termfold.app.pdftools.PdfToolsPaths.outputPdf(dir)
                val sha = if (outFile.isFile) com.termfold.app.pdftools.PdfToolsImport.sha256(outFile) else null
                val report = com.termfold.app.pdftools.PdfToolsPaths.reportFile(dir).takeIf { it.isFile }?.readText()
                if (outFile.isFile.not() || report.isNullOrBlank()) {
                    _pdfToolsJob.value = PdfToolsJob(title = "PDF Tools", error = "Generate or verify failed")
                    return@withContext
                }
                pdfToolsStore.update {
                    it.upsert(
                        session.copy(
                            outputSha256 = sha,
                            lastReport = report,
                            updatedAtMs = System.currentTimeMillis(),
                        ),
                    )
                }
                _pdfToolsJob.value = PdfToolsJob(title = "PDF Tools", doneSessionId = sessionId, percent = 100)
            }
        }
    }

    fun runPdfToolkit(op: String, inputs: List<File>, extra: Map<String, String> = emptyMap(), authz: com.termfold.app.pdftools.PdfToolsAuthzClass) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val blocked = com.termfold.app.pdftools.PdfToolsAuthz.blockReason(authz)
            if (blocked != null) {
                _pdfToolsJob.value = PdfToolsJob(title = "PDF Tools", error = blocked)
                return@launch
            }
            if (inputs.isEmpty()) {
                _pdfToolsJob.value = PdfToolsJob(title = "PDF Tools", error = "Pick at least one file")
                return@launch
            }
            _pdfToolsJob.value = PdfToolsJob(title = "PDF Tools", step = op)
            if (!ensurePdfsysLibraries()) return@launch
            withContext(Dispatchers.IO) {
                val id = "toolkit-${System.currentTimeMillis()}"
                val dir = com.termfold.app.pdftools.PdfToolsPaths.ensureStub(app, id)
                val work = com.termfold.app.pdftools.PdfToolsPaths.workDir(dir).apply { mkdirs() }
                val copied = inputs.mapIndexed { i, f ->
                    val ext = f.extension.ifBlank { if (op == "images-to-pdf") "png" else "pdf" }
                    val c = File(work, "in$i.$ext")
                    f.copyTo(c, overwrite = true)
                    c
                }
                val before = copied.map { com.termfold.app.pdftools.PdfToolsImport.sha256(it) }
                val guest = com.termfold.app.pdftools.PdfToolsPaths.sessionGuest(id)
                val guestIns = copied.map { "$guest/work/${it.name}" }
                val outPdf = "$guest/out/out.pdf"
                val outTxt = "$guest/out/out.txt"
                val outImages = "$guest/out/images"
                val guestArgs = when (op) {
                    "merge" -> listOf("merge") + guestIns + listOf("-o", outPdf)
                    "rotate" -> listOf("rotate", guestIns.first(), "--degrees", extra["degrees"] ?: "90", "-o", outPdf)
                    "split" -> listOf(
                        "split",
                        guestIns.first(),
                        "--start", extra["start"] ?: "1",
                        "--end", extra["end"] ?: "1",
                        "-o",
                        outPdf,
                    )
                    "extract-text" -> listOf("extract-text", guestIns.first(), "-o", outTxt)
                    "extract-images" -> listOf("extract-images", guestIns.first(), "-o", outImages)
                    "images-to-pdf" -> listOf("images-to-pdf") + guestIns + listOf("-o", outPdf)
                    else -> emptyList()
                }
                if (guestArgs.isEmpty()) {
                    _pdfToolsJob.value = PdfToolsJob(title = "PDF Tools", error = "Unknown toolkit action")
                    return@withContext
                }
                val result = com.termfold.app.pdftools.PdfToolsGuest.run(app, guestArgs)
                val after = copied.map { com.termfold.app.pdftools.PdfToolsImport.sha256(it) }
                if (before != after) {
                    _pdfToolsJob.value = PdfToolsJob(title = "PDF Tools", error = "input mutated")
                    return@withContext
                }
                if (result.first != 0) {
                    _pdfToolsJob.value = PdfToolsJob(
                        title = "PDF Tools",
                        error = result.second.takeLast(400).ifBlank { "Toolkit failed" },
                    )
                    return@withContext
                }
                val outHost = when (op) {
                    "extract-text" -> File(com.termfold.app.pdftools.PdfToolsPaths.outDir(dir), "out.txt")
                    "extract-images" -> File(com.termfold.app.pdftools.PdfToolsPaths.outDir(dir), "images")
                    else -> File(com.termfold.app.pdftools.PdfToolsPaths.outDir(dir), "out.pdf")
                }
                if (!outHost.exists()) {
                    _pdfToolsJob.value = PdfToolsJob(title = "PDF Tools", error = "Toolkit produced no output")
                    return@withContext
                }
                val shareFiles = if (outHost.isDirectory) {
                    outHost.listFiles()?.toList().orEmpty()
                } else {
                    listOf(outHost)
                }
                if (shareFiles.isNotEmpty()) {
                    com.termfold.app.files.FileActions.share(app, shareFiles)
                }
                _pdfToolsJob.value = PdfToolsJob(title = "PDF Tools", percent = 100, doneSessionId = id)
            }
        }
    }

    private suspend fun ensurePdfsysLibraries(): Boolean {
        val app = getApplication<Application>()
        _pdfToolsJob.value = PdfToolsJob(title = "PDF Tools", step = "Installing PDF libraries…")
        val result = withContext(Dispatchers.IO) {
            com.termfold.app.pdftools.PdfToolsGuest.ensureBasicLibraries(app)
        }
        if (result.first != 0) {
            _pdfToolsLibrariesReady.value = false
            _pdfToolsJob.value = PdfToolsJob(
                title = "PDF Tools",
                error = "Could not install pypdf in the Linux environment. Check the connection and try again.",
            )
            return false
        }
        _pdfToolsLibrariesReady.value = true
        return true
    }

    fun installPdfToolsLibraries() {
        viewModelScope.launch {
            _pdfToolsJob.value = PdfToolsJob(title = "PDF Tools", step = "Installing recreate/QA libraries…")
            val app = getApplication<Application>()
            val result = withContext(Dispatchers.IO) {
                com.termfold.app.pdftools.PdfToolsGuest.ensurePathBLibraries(app)
            }
            if (result.first != 0) {
                _pdfToolsLibrariesReady.value = com.termfold.app.pdftools.PdfToolsGuest.librariesReady(app)
                _pdfToolsJob.value = PdfToolsJob(
                    title = "PDF Tools",
                    error = "Could not install Path B libraries (WeasyPrint/PyMuPDF). Check connection and try again.",
                )
                return@launch
            }
            _pdfToolsLibrariesReady.value = true
            _pdfToolsJob.value = PdfToolsJob(
                title = "PDF Tools",
                step = "Recreate/QA libraries installed",
                percent = 100,
            )
        }
    }
}
