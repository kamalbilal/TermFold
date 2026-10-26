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
import com.termfold.app.data.PrReviewStore
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

    val folders: StateFlow<AppData> = store.data
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppData())

    val automation: StateFlow<com.termfold.app.automation.AutomationData> = automationStore.data
        .stateIn(viewModelScope, SharingStarted.Eagerly, com.termfold.app.automation.AutomationData())

    val prReviews: StateFlow<PrReviewData> = prReviewStore.data
        .stateIn(viewModelScope, SharingStarted.Eagerly, PrReviewData())

    private val _shell = MutableStateFlow(ShellProgress())
    val shell: StateFlow<ShellProgress> = _shell.asStateFlow()

    private val _job = MutableStateFlow<ProjectJob?>(null)
    val job: StateFlow<ProjectJob?> = _job.asStateFlow()

    private val _backupJob = MutableStateFlow<BackupJob?>(null)
    val backupJob: StateFlow<BackupJob?> = _backupJob.asStateFlow()

    private val _prReviewJob = MutableStateFlow<PrReviewJob?>(null)
    val prReviewJob: StateFlow<PrReviewJob?> = _prReviewJob.asStateFlow()
    private var prReviewBindJob: Job? = null

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
            store.update { data ->
                data.copy(
                    folders = data.folders.map { folder ->
                        if (folder.id != folderId) return@map folder
                        folder.copy(sessions = folder.sessions.filterNot { it.id == sessionId })
                    }
                )
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

    fun saveReviewParse(sessionId: String, parse: PrReviewParse) {
        viewModelScope.launch {
            val session = prReviews.value.session(sessionId) ?: return@launch
            upsertReviewSession(PrReviewTurnSync.apply(session, parse))
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
}
