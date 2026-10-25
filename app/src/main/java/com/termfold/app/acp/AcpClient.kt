package com.termfold.app.acp

import android.content.Context
import android.util.Log
import com.termfold.app.R
import com.termfold.app.automation.AutomationReadOnlyPolicy
import com.termfold.app.automation.AutomationWebToolPolicy
import com.termfold.app.acp.checkpoints.AcpCheckpointUiState
import com.termfold.app.acp.checkpoints.AcpRedoFrame
import com.termfold.app.acp.checkpoints.CheckpointBusy
import com.termfold.app.acp.checkpoints.ConversationSummary
import com.termfold.app.automation.AutomationGuardrailVerdict
import com.termfold.app.automation.AutomationGuardrails
import com.termfold.app.automation.AutomationGuardrailContext
import com.termfold.app.automation.AutomationSpecCommand
import com.termfold.app.acp.checkpoints.ShadowCheckpointStore
import com.termfold.app.shell.ProotCommand
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * A string field that may be missing or JSON `null`. `optString` turns `null` into the text
 * "null", which then shows up in the UI as if it were a real name or description.
 */
internal fun JSONObject.text(key: String): String =
    if (isNull(key)) "" else optString(key)

/**
 * Params for ACP `initialize`. Cursor reads `clientCapabilities._meta.parameterizedModelPicker`;
 * without it the agent returns one exploded variant per model and hides separate fast, effort,
 * and context controls.
 */
internal fun acpInitializeParams(): JSONObject = JSONObject()
    .put("protocolVersion", 1)
    .put(
        "clientCapabilities",
        JSONObject()
            .put("fs", JSONObject().put("readTextFile", false).put("writeTextFile", false))
            .put("terminal", false)
            .put("_meta", JSONObject().put("parameterizedModelPicker", true)),
    )

/** One content block inside a prompt or an agent update. */
sealed interface AcpBlock {
    data class Text(val text: String) : AcpBlock
    data class Image(val base64: String, val mimeType: String) : AcpBlock

    /**
     * A long paste sent as a file rather than inline prose. [name] is what the chip and the
     * agent see (`pasted-1.txt`).
     */
    data class TextFile(val name: String, val text: String) : AcpBlock
}

/** The visible timeline of an agent session. */
sealed interface AcpItem {
    abstract val id: Long

    data class UserMessage(
        override val id: Long,
        val text: String,
        val images: List<AcpBlock.Image>,
        val files: List<AcpBlock.TextFile> = emptyList(),
    ) : AcpItem

    /** Something that went wrong with a turn, shown inline where it happened. */
    data class Notice(override val id: Long, val text: String) : AcpItem

    /** Accumulated agent output; grows one chunk at a time. */
    data class AgentText(override val id: Long, val text: String) : AcpItem

    /**
     * The agent's thinking. Rendered collapsed — the newest line as a ticker — and expanded on
     * tap. Consecutive thought chunks merge into one block until a different kind of update
     * arrives, so one reasoning phase reads as one card.
     */
    data class Thought(override val id: Long, val text: String) : AcpItem

    data class ToolCall(
        override val id: Long,
        val toolCallId: String,
        val title: String,
        val kind: String,
        val status: String,
        val detail: String,
        /**
         * The files the tool touches, as the agent reports them: its `locations`, or failing that
         * the path-like fields of its `rawInput`. Titles alone are often just "Read" or "Write".
         */
        val paths: List<String> = emptyList(),
        /** Structured per-file patches from ACP `diff` content entries. */
        val fileDiffs: List<AcpToolFileDiff> = emptyList(),
    ) : AcpItem

    /** The agent's plan (todo list), replaced wholesale on every update. */
    data class Plan(
        override val id: Long,
        val entries: List<PlanEntry>,
    ) : AcpItem

    data class PlanEntry(val text: String, val status: String)
}

data class AcpPermissionOption(val id: String, val name: String, val kind: String)

/**
 * A slash command the agent offers (its skills, /init, /review, ...). Sent back as ordinary
 * prompt text starting with "/name"; [hint] describes the argument, when it takes one.
 */
data class AcpCommand(val name: String, val description: String, val hint: String)

/**
 * A session setting the agent lets the user change: its model, reasoning level ("thought_level"),
 * mode, or anything else it publishes. Agents describe these either as `configOptions` (the
 * current protocol) or through the older `models` / `modes` lists; [source] records which, since
 * each is changed with its own method.
 */
data class AcpSetting(
    val id: String,
    val name: String,
    /** "model", "thought_level", "mode", "model_config", or an agent-specific "_..." category. */
    val category: String,
    /** A boolean option has no [choices]; its value is "true" or "false". */
    val isToggle: Boolean,
    val current: String,
    val choices: List<AcpChoice>,
    val source: Source,
) {
    enum class Source { CONFIG_OPTION, MODEL, MODE }

    val currentLabel: String get() = choices.firstOrNull { it.value == current }?.name ?: current
}

data class AcpChoice(val value: String, val name: String, val description: String = "")

/** An earlier conversation the agent can reopen (from `session/list`). */
data class AcpPastSession(
    val sessionId: String,
    val title: String,
    /** ISO-8601 as the agent reported it, or empty. */
    val updatedAt: String,
)

data class AcpPendingPermission(
    /** The agent's JSON-RPC id, echoed back verbatim: a number or a string. */
    val requestId: Any,
    val toolCallId: String?,
    val options: List<AcpPermissionOption>,
)

enum class AcpPhase { CONNECTING, INSTALLING, READY, AUTH_REQUIRED, ERROR, CLOSED }

data class AcpUiState(
    val phase: AcpPhase = AcpPhase.CONNECTING,
    val statusText: String = "",
    val items: List<AcpItem> = emptyList(),
    /** True while a prompt is being worked on — drives the thinking indicators. */
    val agentBusy: Boolean = false,
    val pendingPermission: AcpPendingPermission? = null,
    val authMethods: List<String> = emptyList(),
    val errorDetail: String = "",
    /** What the agent said it accepts in prompts; unsupported content is sent as file links. */
    val acceptsImages: Boolean = false,
    val acceptsEmbeddedContext: Boolean = false,
    /** The agent's slash commands, from its latest available_commands_update. */
    val commands: List<AcpCommand> = emptyList(),
    /** Model, reasoning level, mode and other options the agent offers for this session. */
    val settings: List<AcpSetting> = emptyList(),
    /** The agent can list its earlier sessions and reopen one (`session/list` + load/resume). */
    val canResumeSessions: Boolean = false,
    /** True while an earlier session is being reopened and its history replayed. */
    val restoring: Boolean = false,
    /** Messages written while the agent was working, sent in order as each turn ends. */
    val queued: List<AcpQueuedPrompt> = emptyList(),
    val checkpoints: AcpCheckpointUiState = AcpCheckpointUiState(),
)

/** A message waiting for the current turn to end (or to be sent now, stopping it). */
data class AcpQueuedPrompt(
    val id: Long,
    val text: String,
    val images: List<AcpBlock.Image>,
    val files: List<AcpBlock.TextFile>,
)

/**
 * Drives one ACP agent over the Agent Client Protocol: spawns the agent inside the bundled
 * Debian environment, speaks JSON-RPC over its stdio, and exposes the resulting timeline as
 * [state] for a Compose surface.
 *
 * The client is deliberately small — initialize, session/new, session/prompt, session/cancel,
 * authenticate, plus the session/update stream and request_permission — which is the whole
 * surface a GUI needs to render an agent natively.
 */
class AcpClient(
    private val context: Context,
    private val sessionKey: String,
    private val agent: AcpAgent,
    private val workspaceGuestDir: String,
    private val automationPromptPrefix: (() -> String)? = null,
    private val automationReadOnlyGuestRoot: String? = null,
    private val automationGuardrails: (() -> AutomationGuardrailContext?)? = null,
) {

    private val automationMode: Boolean = automationPromptPrefix != null

    private val _state = MutableStateFlow(AcpUiState())
    val state: StateFlow<AcpUiState> = _state.asStateFlow()

    /** For notifications: who is talking, and in which folder. */
    val agentName: String get() = agent.name
    val folderLabel: String get() = workspaceGuestDir.trim('/').ifBlank { workspaceGuestDir }

    private val checkpointStore = ShadowCheckpointStore(sessionKey, workspaceGuestDir)

    /** Prepended to the next user prompt after a wire-session resync. */
    @Volatile
    private var pendingWireContext: String? = null

    private val nextId = AtomicLong(1)
    private val pending = ConcurrentHashMap<Long, CompletableDeferred<JSONObject>>()
    private val writeLock = Any()

    @Volatile
    private var process: Process? = null

    @Volatile
    private var writer: java.io.BufferedWriter? = null

    @Volatile
    private var agentSessionId: String? = null

    /** Wire session used for prompts (may be a hidden background rotation after compress). */
    val currentSessionId: String? get() = agentSessionId

    /** Wire session shown in “Sessions in this folder” as the conversation the user is in. */
    val listedSessionId: String? get() = rememberedSessionId()

    @Volatile
    private var closedByUser = false

    /** True from [start] until the attempt ends in an error, so re-entering the screen is a no-op. */
    private val running = java.util.concurrent.atomic.AtomicBoolean(false)

    private val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())

    /**
     * Starts installation (if needed) and the agent process.
     *
     * The screen calls this every time it is entered. While an attempt is in flight or the
     * session is live it does nothing, so returning to a session keeps the same agent and
     * conversation. After an error or a refused sign-in it starts a fresh attempt, which is how
     * "log in from a Shell, then reopen this session" works.
     */
    fun start() {
        val retryable = _state.value.phase in setOf(AcpPhase.ERROR, AcpPhase.AUTH_REQUIRED, AcpPhase.CLOSED)
        if (running.get() && !retryable) return
        running.set(true)
        closedByUser = false
        runCatching { process?.destroy() }
        process = null
        agentSessionId = null
        failPending("Agent restarted")
        _state.update { it.copy(phase = AcpPhase.CONNECTING, errorDetail = "", pendingPermission = null) }
        scope.launch {
            try {
                begin()
            } catch (failure: Throwable) {
                Log.e(TAG, "ACP startup failed", failure)
                _state.update {
                    it.copy(
                        phase = AcpPhase.ERROR,
                        errorDetail = withAgentOutput(failure.message ?: failure.javaClass.simpleName),
                    )
                }
            }
        }
    }

    /** Kills the agent and starts it again, keeping the visible conversation. */
    fun restart() {
        _state.update { it.copy(phase = AcpPhase.CLOSED, agentBusy = false, queued = emptyList()) }
        start()
    }

    /**
     * Deletes the Cursor CLI from the guest and runs a full install again. Chat history on screen
     * is kept; sign-in (Settings API key or `agent login` in Shell) may be required again afterward.
     */
    fun reinstallCursor() {
        if (agent.id != "cursor") return
        if (_state.value.phase == AcpPhase.INSTALLING) return
        val retryable = _state.value.phase in setOf(AcpPhase.ERROR, AcpPhase.AUTH_REQUIRED, AcpPhase.CLOSED)
        if (running.get() && !retryable) return
        runCatching { process?.destroy() }
        process = null
        agentSessionId = null
        failPending("Agent restarted")
        closedByUser = false
        running.set(true)
        scope.launch {
            try {
                _state.update {
                    it.copy(
                        phase = AcpPhase.INSTALLING,
                        statusText = context.getString(R.string.acp_reinstalling_cursor),
                        errorDetail = "",
                    )
                }
                withContext(Dispatchers.IO) {
                    AcpInstaller.purgeCursor(context)
                }
                begin()
            } catch (failure: Throwable) {
                Log.e(TAG, "Cursor reinstall failed", failure)
                _state.update {
                    it.copy(
                        phase = AcpPhase.ERROR,
                        errorDetail = withAgentOutput(failure.message ?: failure.javaClass.simpleName),
                    )
                }
            }
        }
    }

    /** The agent's latest stderr lines, for error reports. */
    private val stderrTail = ArrayDeque<String>()

    private fun withAgentOutput(message: String): String {
        val said = recentStderr()
        return if (said.isBlank() || message.contains(said)) message else "$message\n\n$said"
    }

    private fun recentStderr(): String = synchronized(stderrTail) { stderrTail.joinToString("\n") }.trim()

    private fun failPending(reason: String) {
        pending.keys.toList().forEach { id ->
            pending.remove(id)?.completeExceptionally(IllegalStateException(reason))
        }
    }

    private suspend fun begin() {
        _state.update { it.copy(phase = AcpPhase.INSTALLING, statusText = "Preparing ${agent.name}") }
        // The installer decides how to launch: a native binary, Node's npx, or Bun's runner for
        // npx packages — whichever survives this particular guest.
        val launch = withContext(Dispatchers.IO) {
            AcpInstaller.ensureInstalled(context, agent) { step ->
                _state.update { it.copy(statusText = step) }
            }
        }
        if (agent.id == "cursor") {
            AcpInstaller.verifyCursorLaunch(context, launch)
        }

        _state.update { it.copy(statusText = "Starting ${agent.name}") }
        val guestArgv = listOf(launch.command) + guestLaunchArgs(launch.args)
        val agentEnv = if (agent.id == "cursor") {
            launch.env + CursorAcpPrefs.agentEnvironment(context)
        } else {
            launch.env
        }
        val command = ProotCommand.build(
            context = context,
            argv = guestArgv,
            guestCwd = workspaceGuestDir,
            pathPrefix = AcpInstaller.pathPrefix(context, agent),
            extraEnv = agentEnv,
        )

        val process = ProcessBuilder(command).apply {
            environment().clear()
            environment().putAll(ProotCommand.hostEnvironment(context))
            redirectErrorStream(false)
        }.start()
        this.process = process
        writer = process.outputStream.bufferedWriter()

        // stderr carries agent diagnostics; stdout must stay pure JSON-RPC.
        // Both readers swallow IOExceptions: when the process exits Android closes its pipes
        // under a blocked read (InterruptedIOException), and an uncaught exception on these
        // threads would take the whole app down. Either way the stream has simply ended.
        Thread {
            runCatching {
                process.errorStream.bufferedReader().use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        Log.i("AcpAgent", line.take(500))
                        synchronized(stderrTail) {
                            stderrTail.addLast(line.take(400))
                            while (stderrTail.size > 40) stderrTail.removeFirst()
                        }
                    }
                }
            }
        }.start()

        val reader = BufferedReader(InputStreamReader(process.inputStream))
        Thread {
            runCatching {
                reader.forEachLine { line ->
                    runCatching { handleLine(line) }
                        .onFailure { Log.w(TAG, "Bad message", it) }
                }
            }
            // A retry replaced this process; its end says nothing about the current attempt.
            if (this.process !== process) return@Thread
            // The stream ending means the agent process is gone, and nothing will answer the
            // requests still waiting.
            failPending("Agent process exited")
            // What the agent said on its way out is usually the actual reason.
            val said = recentStderr()
            val code = runCatching { process.waitFor(); process.exitValue() }.getOrNull()
            _state.update {
                if (!closedByUser && it.phase != AcpPhase.ERROR && it.phase != AcpPhase.AUTH_REQUIRED) {
                    it.copy(
                        phase = AcpPhase.ERROR,
                        errorDetail = "Agent process exited" + (code?.let { c -> " (code $c)" } ?: "") +
                            if (said.isNotBlank()) ".\n\n$said" else "",
                    )
                } else {
                    it
                }
            }
        }.start()

        // Handshake. The generous timeout absorbs a first spawn that is still finishing
        // package-environment setup inside the guest (uvx resolving an interpreter, bun x
        // finishing an install the installer's prewarm could not complete in time).
        val initResponse = request(
            "initialize",
            acpInitializeParams(),
            timeoutMs = 300_000,
        )
        val result = initResponse.optJSONObject("result")
        val authMethods = result
            ?.optJSONArray("authMethods")
            ?.let { methods -> List(methods.length()) { methods.getJSONObject(it).optString("name") } }
            ?: emptyList()
        val capabilities = result?.optJSONObject("agentCapabilities")
        val prompt = capabilities?.optJSONObject("promptCapabilities")
        val sessionCaps = capabilities?.optJSONObject("sessionCapabilities")
        canLoad = capabilities?.optBoolean("loadSession") == true
        canResume = sessionCaps?.has("resume") == true
        canList = sessionCaps?.has("list") == true
        _state.update {
            it.copy(
                authMethods = authMethods,
                acceptsImages = prompt?.optBoolean("image") == true,
                acceptsEmbeddedContext = prompt?.optBoolean("embeddedContext") == true,
                canResumeSessions = canList && (canLoad || canResume),
            )
        }

        // Stay on this TermFold chat. A wire resync may have rotated the ACP session id;
        // restore the local timeline and attach the latest wire session without replaying
        // the agent's summary-first clone into a second listed conversation.
        val listed = rememberedSessionId()
        val local = withContext(Dispatchers.IO) {
            listed?.let { AcpTimelineStore.load(context, sessionKey, it) }
                ?: AcpTimelineStore.load(context, sessionKey)
        }
        if (local != null) adoptTimeline(local)
        val activeWire = rememberedActiveWireId()
        val resume = AcpSessionResume.plan(
            hasLocalTimeline = local != null,
            rememberedWireId = activeWire,
            canReopen = canLoad || canResume,
        )
        if (local != null) {
            val preferred = if (resume.useRememberedWireSession) activeWire else null
            val opened = preferred != null && attachWireKeepingTimeline(preferred, local)
            if (!opened && listed != null && listed != preferred) {
                if (attachWireKeepingTimeline(listed, local)) {
                    bindConversation(listed, listed)
                    persistTimeline()
                    return
                }
            } else if (opened) {
                persistTimeline()
                return
            }
            suppressReplayForMcpReload = true
            try {
                openSession(updateListed = false)
            } finally {
                suppressReplayForMcpReload = false
            }
            adoptTimeline(local)
            val newActive = agentSessionId
            val keepListed = rememberedSessionId() ?: listed
            if (newActive != null && keepListed != null) {
                bindConversation(keepListed, newActive)
                hideFromPastList(newActive)
                suppressReplayForWireId = newActive
            }
            persistTimeline()
            return
        }
        if (resume.useRememberedWireSession && activeWire != null) {
            if (reopen(activeWire, replay = true)) {
                persistTimeline()
                return
            }
        }
        openSession()
    }

    @Volatile private var canLoad = false
    @Volatile private var canResume = false
    @Volatile private var canList = false

    /** Drops replayed chat chunks while `session/load` reconnects MCP on the same session. */
    @Volatile private var suppressReplayForMcpReload = false

    /**
     * When set, drop chat replay chunks that belong to this background wire only.
     * User-initiated session/load must clear this so reopening a thread can replay.
     */
    @Volatile private var suppressReplayForWireId: String? = null

    private val prefs get() = context.getSharedPreferences("acp_sessions", Context.MODE_PRIVATE)

    /** Where the agents keep their conversations, under the guest home. */
    private val sessionStores = listOf(
        ".claude/projects", ".codex/sessions", ".codex/archived_sessions", ".pi/agent/sessions",
        ".gemini/tmp", ".local/share/opencode/storage", ".qwen/tmp",
    )

    /** Listed wire id for this tab (user-visible session in session/list). */
    private fun rememberedSessionId(): String? = prefs.getString(sessionKey, null)

    private fun activeWirePrefsKey(): String = "$sessionKey|active"

    private fun wireMapKey(): String = "$sessionKey|wireMap"

    /** Wire id that receives prompts; may differ from [rememberedSessionId] after compress. */
    private fun rememberedActiveWireId(): String? =
        prefs.getString(activeWirePrefsKey(), null) ?: rememberedSessionId()

    private fun wireMap(): Map<String, String> = AcpConversationWires.parse(prefs.getString(wireMapKey(), null))

    private fun bindConversation(listed: String, active: String) {
        val next = AcpConversationWires.bind(wireMap(), listed, active)
        prefs.edit()
            .putString(sessionKey, listed)
            .putString(activeWirePrefsKey(), active)
            .putString(wireMapKey(), AcpConversationWires.encode(next))
            .apply()
    }

    private fun activeWireFor(listed: String): String = AcpConversationWires.activeFor(wireMap(), listed)

    private fun clearCurrentWirePointers() {
        prefs.edit().remove(sessionKey).remove(activeWirePrefsKey()).apply()
    }

    /**
     * The agent's earlier sessions for this folder, newest first as the agent orders them.
     * Pages through `session/list` up to a sane cap.
     */
    suspend fun listPastSessions(): List<AcpPastSession> = withContext(Dispatchers.IO) {
        if (!canList) return@withContext emptyList()
        var cursor: String? = null
        val pages = mutableListOf<List<AcpPastSession>>()
        for (page in 0 until AcpPastSessions.MAX_LIST_PAGES) {
            val params = JSONObject().put("cwd", workspaceGuestDir)
            cursor?.let { params.put("cursor", it) }
            val result = runCatching { request("session/list", params) }.getOrNull()?.optJSONObject("result") ?: break
            val list = result.optJSONArray("sessions") ?: JSONArray()
            val pageSessions = mutableListOf<AcpPastSession>()
            for (i in 0 until list.length()) {
                val entry = list.optJSONObject(i) ?: continue
                val id = entry.text("sessionId")
                if (id.isNotBlank()) {
                    pageSessions += AcpPastSession(id, entry.text("title"), entry.text("updatedAt"))
                }
            }
            pages += pageSessions
            cursor = result.text("nextCursor").ifBlank { null } ?: break
        }
        val deleted = deletedIds()
        AcpPastSessions.visible(AcpPastSessions.mergeListed(pages), deleted)
    }

    // ---- Deleting earlier sessions ----------------------------------------------------------

    private val deletedPrefs get() = context.getSharedPreferences("acp_deleted_sessions", Context.MODE_PRIVATE)

    private fun deletedKey() = AcpPastSessions.deletedKey(agent.id, workspaceGuestDir, sessionKey)

    private fun legacyDeletedKey() = AcpPastSessions.legacyDeletedKey(agent.id, workspaceGuestDir)

    private fun deletedIds(): Set<String> {
        migrateDeletedHiddenIfNeeded()
        return deletedPrefs.getStringSet(deletedKey(), emptySet()).orEmpty()
    }

    private fun migrateDeletedHiddenIfNeeded() {
        val tabKey = deletedKey()
        if (deletedPrefs.contains(tabKey)) return
        val migrationDone = deletedPrefs.getBoolean(AcpPastSessions.PREFS_MIGRATION_FLAG, false)
        val legacy = deletedPrefs.getStringSet(legacyDeletedKey(), null)
        val (hidden, done) = AcpPastSessions.migrateHiddenIds(
            perTabHidden = null,
            legacyHidden = legacy,
            globalMigrationDone = migrationDone,
        )
        if (hidden.isNotEmpty() || done) {
            deletedPrefs.edit()
                .putStringSet(tabKey, hidden)
                .putBoolean(AcpPastSessions.PREFS_MIGRATION_FLAG, done)
                .apply()
        }
    }

    /**
     * Deletes an earlier session: it leaves this list for good, and its saved conversation is
     * removed. ACP has no standard delete, so the agent is asked (some support it) and the
     * transcript files the known agents keep in the guest are removed, matched by the full
     * session id. The session in use cannot be deleted.
     */
    suspend fun deletePastSession(sessionId: String): Boolean = withContext(Dispatchers.IO) {
        if (sessionId.isBlank() || sessionId == agentSessionId || sessionId == listedSessionId) {
            return@withContext false
        }
        deletedPrefs.edit().putStringSet(deletedKey(), deletedIds() + sessionId).apply()
        val nextMap = AcpConversationWires.without(wireMap(), sessionId)
        prefs.edit().putString(wireMapKey(), AcpConversationWires.encode(nextMap)).apply()
        AcpTimelineStore.clear(context, sessionKey, sessionId)
        runCatching { request("session/delete", JSONObject().put("sessionId", sessionId), timeoutMs = 5_000) }
        // Only ids long enough to be unique (UUIDs and the like) are matched against file names.
        if (sessionId.length >= 12) {
            val home = java.io.File(com.termfold.app.shell.ShellPaths.rootfsDir(context), "root")
            sessionStores.forEach { store -> removeMatching(java.io.File(home, store), sessionId) }
        }
        true
    }

    private fun removeMatching(root: java.io.File, id: String) {
        if (!root.isDirectory) return
        var seen = 0
        val stack = ArrayDeque<Pair<java.io.File, Int>>()
        stack.add(root to 0)
        while (stack.isNotEmpty() && seen < 50_000) {
            val (dir, depth) = stack.removeLast()
            val kids = dir.listFiles() ?: continue
            for (f in kids) {
                seen++
                val link = java.nio.file.Files.isSymbolicLink(f.toPath())
                if (f.name.contains(id)) {
                    if (f.isDirectory && !link) f.deleteRecursively() else f.delete()
                } else if (f.isDirectory && !link && depth < 7) {
                    stack.add(f to depth + 1)
                }
            }
        }
    }

    /** Reopens an earlier session chosen by the user, replacing the current conversation. */
    fun openPastSession(sessionId: String) {
        if (sessionId.isBlank() || sessionId == rememberedSessionId()) return
        scope.launch {
            persistTimeline()
            suppressReplayForWireId = null
            pendingWireContext = null
            val local = AcpTimelineStore.load(context, sessionKey, sessionId)
            val active = activeWireFor(sessionId)
            if (local != null) {
                bindConversation(sessionId, active)
                val opened = attachWireKeepingTimeline(active, local)
                if (!opened && active != sessionId) {
                    if (attachWireKeepingTimeline(sessionId, local)) {
                        bindConversation(sessionId, sessionId)
                    } else {
                        appendSystemError("That session could not be reopened.")
                        return@launch
                    }
                } else if (!opened) {
                    appendSystemError("That session could not be reopened.")
                    return@launch
                }
                persistTimeline()
                return@launch
            }
            bindConversation(sessionId, sessionId)
            if (!reopen(sessionId, replay = true)) {
                appendSystemError("That session could not be reopened.")
            } else {
                persistTimeline()
            }
        }
    }

    /** Leaves the current conversation and starts an empty one with the same agent. */
    fun startNewSession() {
        scope.launch {
            persistTimeline()
            suppressReplayForWireId = null
            pendingWireContext = null
            agentSessionId = null
            _state.update {
                it.copy(
                    items = emptyList(),
                    agentBusy = false,
                    pendingPermission = null,
                    queued = emptyList(),
                    checkpoints = AcpCheckpointUiState(),
                )
            }
            clearCurrentWirePointers()
            AcpTimelineStore.clear(context, sessionKey)
            openSession()
        }
    }

    /**
     * Reconnects MCP on the active session with a fresh `mcpServers` list.
     * Cursor uses `session/load`; agents with only `session/resume` use that instead.
     */
    fun reloadMcpServers() {
        scope.launch { reloadMcpServersNow() }
    }

    private suspend fun reloadMcpServersNow(): Boolean {
        val sessionId = agentSessionId
        if (sessionId.isNullOrBlank()) return false
        val ui = _state.value
        if (ui.phase != AcpPhase.READY) return false
        if (turnInFlight || ui.agentBusy || ui.pendingPermission != null) {
            appendSystemError(context.getString(R.string.mcp_reload_busy))
            return false
        }
        val method = when {
            canLoad -> "session/load"
            canResume -> "session/resume"
            else -> {
                appendSystemError(context.getString(R.string.mcp_reload_unsupported))
                return false
            }
        }
        _state.update { it.copy(statusText = context.getString(R.string.mcp_reload_status)) }
        suppressReplayForMcpReload = method == "session/load"
        val response = try {
            runCatching {
                request(
                    method,
                    JSONObject()
                        .put("sessionId", sessionId)
                        .put("cwd", workspaceGuestDir)
                        .put("mcpServers", mcpServers()),
                    timeoutMs = 120_000,
                )
            }.getOrNull()
        } finally {
            suppressReplayForMcpReload = false
        }
        if (response == null || response.has("error")) {
            val detail = response?.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
            appendSystemError(
                detail?.let { context.getString(R.string.mcp_reload_failed, it) }
                    ?: context.getString(R.string.mcp_reload_failed_generic),
            )
            _state.update { it.copy(statusText = "") }
            Log.w(TAG, "$method (MCP reload) failed: ${response?.optJSONObject("error")}")
            return false
        }
        val settings = parseSettings(response.optJSONObject("result"))
        _state.update { state ->
            state.copy(
                statusText = "",
                settings = settings.ifEmpty { state.settings },
                items = state.items + AcpItem.Notice(nextItemId(), context.getString(R.string.mcp_reload_ok)),
            )
        }
        return true
    }

    /**
     * Attaches an ACP wire session without replacing TermFold's timeline or checkpoint maps.
     * Cursor may replay history on `session/load`; that replay is dropped and [snapshot] is reapplied.
     */
    private fun attachWireKeepingTimeline(wireId: String, snapshot: AcpPersistedTimeline): Boolean {
        adoptTimeline(snapshot)
        suppressReplayForMcpReload = true
        suppressReplayForWireId = wireId
        return try {
            val opened = reopen(wireId, replay = false)
            adoptTimeline(snapshot)
            opened
        } finally {
            suppressReplayForMcpReload = false
            suppressReplayForWireId = null
        }
    }

    /**
     * Opens [sessionId] with `session/load` (or `session/resume`). [replay] true clears the
     * timeline so the agent can fill it; false keeps TermFold's items.
     */
    private fun reopen(sessionId: String, replay: Boolean): Boolean {
        val method = if (canLoad) "session/load" else if (canResume) "session/resume" else return false
        val previousItems = _state.value.items
        val previousCheckpoints = _state.value.checkpoints
        val previousId = agentSessionId
        // Replayed updates are addressed to this session; accept them from the start.
        agentSessionId = sessionId
        _state.update { it.copy(items = if (replay) emptyList() else it.items, restoring = true) }
        val response = runCatching {
            request(
                method,
                JSONObject()
                    .put("sessionId", sessionId)
                    .put("cwd", workspaceGuestDir)
                    .put("mcpServers", mcpServers()),
                timeoutMs = 120_000,
            )
        }.getOrNull()
        if (response == null || response.has("error")) {
            agentSessionId = previousId
            _state.update { it.copy(items = previousItems, checkpoints = previousCheckpoints, restoring = false) }
            Log.w(TAG, "$method failed: ${response?.optJSONObject("error")}")
            return false
        }
        val result = response.optJSONObject("result")
        val settings = parseSettings(result)
        _state.update { state ->
            state.copy(
                phase = AcpPhase.READY,
                statusText = "",
                restoring = false,
                agentBusy = false,
                // load/resume may not repeat the options; keep what we had rather than blanking.
                settings = settings.ifEmpty { state.settings },
                items = if (method == "session/resume" && state.items.isEmpty()) {
                    listOf(AcpItem.Notice(nextItemId(), "Resumed an earlier session. This agent does not send its history, but it remembers the conversation."))
                } else {
                    state.items
                },
            )
        }
        return true
    }

    /**
     * Tools TermFold gives every agent: its browser (open, read, click, type, screenshot...), as
     * an MCP server the agent starts inside the guest. Only offered once Node is installed.
     */
    private fun mcpServers(): JSONArray =
        com.termfold.app.acp.mcp.McpCatalog.acpServers(
            context,
            agent.id,
            workspaceGuestDir,
            automationMode = automationMode,
        )

    private suspend fun openSession(updateListed: Boolean = true) {
        val cwd = workspaceGuestDir
        val response = try {
            request(
                "session/new",
                JSONObject().put("cwd", cwd).put("mcpServers", mcpServers()),
            )
        } catch (failure: Throwable) {
            // The stream is gone or the agent stopped answering — a transport failure, not a
            // sign-in problem, unless the process is still alive and says so.
            reportSessionFailure(
                failure.message ?: "session/new failed",
                agentSpeaking = process?.isAlive == true,
            )
            return
        }
        val error = response.optJSONObject("error")
        if (error != null) {
            // Agents refuse session/new when they need login credentials; that is the one
            // failure the user can act on, so it gets the auth screen rather than an error card.
            reportSessionFailure(
                error.optString("message").ifBlank { "session/new was refused" },
                agentSpeaking = true,
            )
            return
        }
        val sessionId = response.optJSONObject("result")?.optString("sessionId")
        if (sessionId.isNullOrBlank()) {
            _state.update { it.copy(phase = AcpPhase.ERROR, errorDetail = "No session id returned") }
            return
        }
        agentSessionId = sessionId
        if (updateListed) {
            bindConversation(sessionId, sessionId)
        } else {
            val listed = rememberedSessionId() ?: sessionId
            bindConversation(listed, sessionId)
        }
        val settings = parseSettings(response.optJSONObject("result"))
        _state.update { it.copy(phase = AcpPhase.READY, statusText = "", settings = settings) }
    }

    /** Routes a session/new failure to either the sign-in screen or the error card. */
    private fun reportSessionFailure(message: String, agentSpeaking: Boolean) {
        val looksAuthRelated = AUTH_HINTS.any { message.contains(it, ignoreCase = true) }
        val phase = if (agentSpeaking && looksAuthRelated) AcpPhase.AUTH_REQUIRED else AcpPhase.ERROR
        Log.w(TAG, "session/new failed ($phase): $message")
        _state.update { it.copy(phase = phase, errorDetail = if (phase == AcpPhase.ERROR) withAgentOutput(message) else message) }
    }

    /**
     * Sends a user prompt: plain text plus optional images. Returns immediately; the reply
     * arrives as timeline updates.
     */
    fun send(
        text: String,
        images: List<AcpBlock.Image>,
        files: List<AcpBlock.TextFile> = emptyList(),
    ) {
        // A turn still ending (just stopped, say) must answer before the next prompt goes.
        if (turnInFlight) enqueue(text, images, files) else sendNow(text, images, files)
    }

    private fun sendNow(
        text: String,
        images: List<AcpBlock.Image>,
        files: List<AcpBlock.TextFile>,
    ) {
        scope.launch { sendNowSuspend(text, images, files) }
    }

    private suspend fun sendNowSuspend(
        text: String,
        images: List<AcpBlock.Image>,
        files: List<AcpBlock.TextFile>,
    ) {
        if (agentSessionId == null) return
        if (_state.value.checkpoints.wireResyncing) {
            enqueue(text, images, files)
            return
        }
        if (automationMode && AutomationSpecCommand.isBareSpecPrompt(text)) {
            appendSystemError(context.getString(R.string.automation_spec_need_request))
            return
        }
        val messageId = beginUserTurn(text, images, files) ?: return
        completeUserTurn(messageId, text, images, files)
    }

    /**
     * Appends the user row and marks the turn in flight. Call [completeUserTurn] after any wire
     * prep (for example edit-resend resync) to send the prompt.
     */
    private suspend fun beginUserTurn(
        text: String,
        images: List<AcpBlock.Image>,
        files: List<AcpBlock.TextFile>,
    ): Long? {
        suppressReplayForWireId = null
        if (_state.value.checkpoints.ghostFromItemId != null) {
            commitGhostBranch(clearRedo = true)
        }
        if (!hasPromptPayload(pendingWireContext, text, images.size, files.size)) return null
        val messageId = nextItemId()
        val preTurnDeferred = scope.async { checkpointStore.commit(context) }
        pendingPreTurnCommits[messageId] = preTurnDeferred
        scope.launch {
            val preHash = runCatching { preTurnDeferred.await() }.getOrNull()
            pendingPreTurnCommits.remove(messageId)
            if (preHash != null) {
                _state.update { state ->
                    state.copy(
                        checkpoints = state.checkpoints.copy(
                            preTurnByMessageId = state.checkpoints.preTurnByMessageId + (messageId to preHash),
                        ),
                    )
                }
            } else {
                _state.update { state ->
                    if (state.items.none { it.id == messageId }) return@update state
                    state.copy(
                        items = state.items + AcpItem.Notice(
                            nextItemId(),
                            "Workspace checkpoint could not be saved (git in Linux guest). Open Terminal and run termfold-setup if you have not, then try again. Undo is unavailable for this message.",
                        ),
                    )
                }
            }
        }
        val newItems = _state.value.items + AcpItem.UserMessage(messageId, text, images, files)
        _state.update { state ->
            state.copy(
                agentBusy = true,
                items = newItems,
            )
        }
        turnInFlight = true
        stoppedByUser = false
        persistTimeline()
        return messageId
    }

    private suspend fun completeUserTurn(
        messageId: Long,
        text: String,
        images: List<AcpBlock.Image>,
        files: List<AcpBlock.TextFile>,
    ) {
        val sessionId = agentSessionId
        if (sessionId == null) {
            abortInFlightUserTurn()
            return
        }
        val content = buildPromptContent(text, images, files)
        if (content == null || content.length() == 0) {
            abortInFlightUserTurn()
            return
        }
        var compressAfterSummarize = false
        try {
            val response = request(
                "session/prompt",
                JSONObject().put("sessionId", sessionId).put("prompt", content),
                timeoutMs = PROMPT_TIMEOUT_MS,
            )
            val hadError = response.optJSONObject("error") != null
            response.optJSONObject("error")?.let { error ->
                appendSystemError(
                    error.optJSONObject("data")?.optString("message")?.ifBlank { null }
                        ?: error.optString("message").ifBlank { "The agent could not answer." },
                )
            }
            val stopReason = response.optJSONObject("result")?.optString("stopReason")
            when (stopReason) {
                "max_tokens" -> appendSystemError("The reply was cut off at the model's length limit.")
                "max_turn_requests" -> appendSystemError("The agent stopped after too many steps.")
                "refusal" -> appendSystemError("The agent declined to continue.")
            }
            compressAfterSummarize = !hadError &&
                stopReason != "refusal" &&
                !stoppedByUser &&
                agent.id == "cursor" &&
                AcpContextCompress.isSummarizePrompt(text)
        } catch (failure: Throwable) {
            Log.w(TAG, "prompt failed", failure)
            appendSystemError(failure.message ?: "prompt failed")
        } finally {
            finalizeUndoableForTurn(messageId)
            if (compressAfterSummarize) {
                maybeCompressContextAfterSummarize(messageId)
            }
            _state.update { it.copy(agentBusy = false) }
            turnInFlight = false
            sendNextQueued()
            persistTimeline()
        }
    }

    private fun buildPromptContent(
        text: String,
        images: List<AcpBlock.Image>,
        files: List<AcpBlock.TextFile>,
    ): JSONArray? {
        val caps = _state.value
        val wireText = buildString {
            pendingWireContext?.let { ctx ->
                append(ctx)
                append("\n\n---\n\n")
                pendingWireContext = null
            }
            automationPromptPrefix?.invoke()?.takeIf { it.isNotBlank() }?.let { prefix ->
                append(prefix)
                append("\n\n---\n\n")
            }
            val body = if (automationMode) {
                AutomationSpecCommand.requestFromPrompt(text)?.let(AutomationSpecCommand::toWirePrompt) ?: text
            } else {
                text
            }
            append(body)
        }
        val content = JSONArray()
        if (wireText.isNotBlank()) content.put(JSONObject().put("type", "text").put("text", wireText))

        files.forEach { file ->
            val guestPath = saveToGuest(file.name, file.text.toByteArray())
            if (caps.acceptsEmbeddedContext) {
                content.put(
                    JSONObject().put("type", "resource").put(
                        "resource",
                        JSONObject()
                            .put("uri", "file://$guestPath")
                            .put("mimeType", "text/plain")
                            .put("text", file.text),
                    ),
                )
            } else {
                content.put(resourceLink(file.name, guestPath, "text/plain", file.text.length.toLong()))
            }
        }

        images.forEachIndexed { index, image ->
            if (caps.acceptsImages) {
                content.put(
                    JSONObject()
                        .put("type", "image")
                        .put("data", image.base64)
                        .put("mimeType", image.mimeType),
                )
            } else {
                val bytes = android.util.Base64.decode(image.base64, android.util.Base64.DEFAULT)
                val name = "image-${System.currentTimeMillis()}-${index + 1}.${extensionFor(image.mimeType)}"
                content.put(resourceLink(name, saveToGuest(name, bytes), image.mimeType, bytes.size.toLong()))
            }
        }
        return if (content.length() == 0) null else content
    }

    private fun abortInFlightUserTurn(optimisticUserMessageId: Long? = null) {
        _state.update { state ->
            val items = if (optimisticUserMessageId != null) {
                state.items.filter { it.id != optimisticUserMessageId }
            } else {
                state.items
            }
            state.copy(agentBusy = false, items = items)
        }
        turnInFlight = false
        if (optimisticUserMessageId != null) persistTimeline()
    }

    private suspend fun awaitPreTurnHash(messageId: Long): String? {
        pendingPreTurnCommits[messageId]?.await()?.let { return it }
        return _state.value.checkpoints.preTurnByMessageId[messageId]
    }

    private suspend fun finalizeUndoableForTurn(messageId: Long) {
        val preHash = awaitPreTurnHash(messageId) ?: return
        _state.update { state ->
            if (state.checkpoints.preTurnByMessageId[messageId] == null) {
                state.copy(
                    checkpoints = state.checkpoints.copy(
                        preTurnByMessageId = state.checkpoints.preTurnByMessageId + (messageId to preHash),
                    ),
                )
            } else {
                state
            }
        }
        val changed = withContext(Dispatchers.IO) {
            checkpointStore.workspaceDiffersFrom(context, preHash)
        }
        if (!changed) return
        _state.update { state ->
            state.copy(
                checkpoints = state.checkpoints.copy(
                    undoableMessageIds = state.checkpoints.undoableMessageIds + messageId,
                ),
            )
        }
    }

    /** A prompt from session/prompt is still waiting for its answer (the turn has not ended). */
    @Volatile private var turnInFlight = false

    private val pendingPreTurnCommits = ConcurrentHashMap<Long, Deferred<String?>>()

    /**
     * True while the agent needs CPU and network to stay up in the background (a turn, a tool, or
     * a permission prompt). Used by [com.termfold.app.notify.SessionWatcher] for wake locks.
     */
    fun keepsCpuAwake(): Boolean {
        val ui = _state.value
        if (turnInFlight || ui.agentBusy || ui.pendingPermission != null) return true
        return ui.items.any { item ->
            item is AcpItem.ToolCall && item.status == "in_progress"
        }
    }

    /** The user pressed Stop: what is queued waits instead of going out on its own. */
    @Volatile private var stoppedByUser = false

    /** A queued message the user chose to send now; it goes as soon as the stopped turn ends. */
    @Volatile private var steerId: Long? = null

    private var queueCounter = 0L

    /** Keeps a message for when the current turn ends. */
    fun enqueue(text: String, images: List<AcpBlock.Image>, files: List<AcpBlock.TextFile>) {
        val item = AcpQueuedPrompt(++queueCounter, text, images, files)
        _state.update { it.copy(queued = it.queued + item) }
        // Writing a new message after Stop means carrying on: the queue runs again.
        stoppedByUser = false
        // The turn may have ended between the tap and now.
        if (!turnInFlight) sendNextQueued()
    }

    fun removeQueued(id: Long) {
        if (steerId == id) steerId = null
        _state.update { state -> state.copy(queued = state.queued.filterNot { it.id == id }) }
    }

    /**
     * Sends a queued message now: the agent's current turn is stopped and this message goes
     * out as soon as the agent confirms, ahead of anything else in the queue.
     */
    fun steer(id: Long) {
        if (_state.value.queued.none { it.id == id }) return
        if (!turnInFlight) {
            sendQueued(id)
            return
        }
        steerId = id
        val sessionId = agentSessionId ?: return
        scope.launch { notify("session/cancel", JSONObject().put("sessionId", sessionId)) }
    }

    private fun sendNextQueued() {
        val steer = steerId
        steerId = null
        when {
            steer != null -> sendQueued(steer)
            stoppedByUser -> Unit
            else -> _state.value.queued.firstOrNull()?.let { sendQueued(it.id) }
        }
    }

    private fun sendQueued(id: Long) {
        val item = _state.value.queued.firstOrNull { it.id == id } ?: return
        _state.update { state -> state.copy(queued = state.queued.filterNot { it.id == id }) }
        sendNow(item.text, item.images, item.files)
    }

    private fun resourceLink(name: String, guestPath: String, mime: String, size: Long): JSONObject =
        JSONObject()
            .put("type", "resource_link")
            .put("uri", "file://$guestPath")
            .put("name", name)
            .put("mimeType", mime)
            .put("size", size)

    /** Writes an attachment into the guest's /tmp and returns its guest path. */
    private fun saveToGuest(name: String, bytes: ByteArray): String {
        val dir = java.io.File(com.termfold.app.shell.ShellPaths.rootfsDir(context), "tmp/termfold")
        dir.mkdirs()
        java.io.File(dir, name).writeBytes(bytes)
        return "/tmp/termfold/$name"
    }

    private fun extensionFor(mime: String): String = when (mime.lowercase()) {
        "image/jpeg", "image/jpg" -> "jpg"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        else -> "png"
    }

    /**
     * Changes a session setting. The new value shows immediately; if the agent refuses, the old
     * one is put back and the reason is shown in the conversation.
     */
    fun changeSetting(setting: AcpSetting, value: String) {
        val sessionId = agentSessionId ?: return
        if (value == setting.current) return
        val previous = setting.current
        replaceSetting(setting.copy(current = value))
        scope.launch {
            val (method, params) = when (setting.source) {
                AcpSetting.Source.CONFIG_OPTION -> "session/set_config_option" to JSONObject()
                    .put("sessionId", sessionId)
                    .put("configId", setting.id)
                    .put("value", if (setting.isToggle) value.toBoolean() else value)
                AcpSetting.Source.MODEL -> "session/set_model" to JSONObject()
                    .put("sessionId", sessionId)
                    .put("modelId", value)
                AcpSetting.Source.MODE -> "session/set_mode" to JSONObject()
                    .put("sessionId", sessionId)
                    .put("modeId", value)
            }
            val response = runCatching { request(method, params) }.getOrElse { failure ->
                JSONObject().put("error", JSONObject().put("message", failure.message ?: "failed"))
            }
            val error = response.optJSONObject("error")
            if (error != null) {
                replaceSetting(setting.copy(current = previous))
                appendSystemError("Could not change ${setting.name}: ${error.optString("message")}")
                return@launch
            }
            // set_config_option answers with the complete, possibly re-shaped option set (a new
            // model can bring different reasoning levels), which replaces ours.
            response.optJSONObject("result")?.optJSONArray("configOptions")?.let { array ->
                val options = parseConfigOptions(array)
                _state.update { state ->
                    state.copy(settings = options + state.settings.filter { it.source != AcpSetting.Source.CONFIG_OPTION })
                }
            }
        }
    }

    private fun replaceSetting(updated: AcpSetting) {
        _state.update { state ->
            state.copy(
                settings = state.settings.map {
                    if (it.id == updated.id && it.source == updated.source) updated else it
                },
            )
        }
    }

    /**
     * The settings published in a session/new result. `configOptions` wins where present; the
     * older `models` / `modes` lists are used only for what it does not already cover.
     */
    private fun parseSettings(result: JSONObject?): List<AcpSetting> {
        result ?: return emptyList()
        val config = parseConfigOptions(result.optJSONArray("configOptions"))
        // An agent that publishes configOptions describes everything there; per the protocol its
        // `models` / `modes` are then only a fallback for older clients. Pi, for one, repeats its
        // thinking levels as "modes", which would otherwise show up twice.
        if (config.isNotEmpty()) return config
        val legacy = buildList {
            result.optJSONObject("models")?.let { models ->
                val list = models.optJSONArray("availableModels") ?: JSONArray()
                val choices = List(list.length()) { list.optJSONObject(it) }.filterNotNull().map {
                    AcpChoice(it.text("modelId"), it.text("name").ifBlank { it.text("modelId") }, it.text("description"))
                }
                if (choices.isNotEmpty()) {
                    add(AcpSetting("model", "Model", "model", false, models.text("currentModelId"), choices, AcpSetting.Source.MODEL))
                }
            }
            result.optJSONObject("modes")?.let { modes ->
                val list = modes.optJSONArray("availableModes") ?: JSONArray()
                val choices = List(list.length()) { list.optJSONObject(it) }.filterNotNull().map {
                    AcpChoice(it.text("id"), it.text("name").ifBlank { it.text("id") }, it.text("description"))
                }
                if (choices.isNotEmpty()) {
                    add(AcpSetting("mode", "Mode", "mode", false, modes.text("currentModeId"), choices, AcpSetting.Source.MODE))
                }
            }
        }
        return legacy.map { it.copy(choices = withoutSharedPrefix(it.choices)) }
    }

    /**
     * Drops a "Label: " prefix every choice shares ("Thinking: off", "Thinking: low", ...): the
     * setting's own name already says it, and in a row of chips it is just noise.
     */
    private fun withoutSharedPrefix(choices: List<AcpChoice>): List<AcpChoice> {
        if (choices.size < 2) return choices
        val prefix = Regex("""^([^:]{1,24}):\s+""").find(choices.first().name)?.value ?: return choices
        if (!choices.all { it.name.startsWith(prefix) && it.name.length > prefix.length }) return choices
        return choices.map { it.copy(name = it.name.removePrefix(prefix)) }
    }

    private fun parseConfigOptions(array: JSONArray?): List<AcpSetting> {
        array ?: return emptyList()
        return List(array.length()) { array.optJSONObject(it) }.filterNotNull().mapNotNull { option ->
            val id = option.text("id").ifBlank { option.text("configId") }.ifBlank { return@mapNotNull null }
            val toggle = option.optString("type") == "boolean"
            // Select values may be flat or grouped ({group, name, options:[...]}); groups are
            // flattened, which is all a phone-sized picker needs.
            val choices = mutableListOf<AcpChoice>()
            fun collect(values: JSONArray?) {
                values ?: return
                for (i in 0 until values.length()) {
                    val entry = values.optJSONObject(i) ?: continue
                    if (entry.has("options")) {
                        collect(entry.optJSONArray("options"))
                    } else {
                        val value = entry.text("value").ifBlank { entry.text("id") }
                        if (value.isNotBlank()) {
                            choices += AcpChoice(value, entry.text("name").ifBlank { value }, entry.text("description"))
                        }
                    }
                }
            }
            collect(option.optJSONArray("options"))
            val cleaned = withoutSharedPrefix(choices)
            AcpSetting(
                id = id,
                name = option.text("name").ifBlank { id },
                category = option.optString("category"),
                isToggle = toggle,
                current = option.text("currentValue"),
                choices = cleaned,
                source = AcpSetting.Source.CONFIG_OPTION,
            )
        }
    }

    fun cancel() {
        val sessionId = agentSessionId
        stoppedByUser = true
        steerId = null
        if (!sessionId.isNullOrBlank()) {
            scope.launch { notify("session/cancel", JSONObject().put("sessionId", sessionId)) }
        }
        // session/prompt blocks until the turn ends; cancel must unblock it or edit/resend waits forever.
        failPending("Turn cancelled")
        abortInFlightUserTurn()
    }

    /** Restores files to before [userMessageId] was sent; dims later chat; rotates wire session. */
    fun restoreCheckpoint(userMessageId: Long) {
        scope.launch {
            _state.update {
                it.copy(checkpoints = it.checkpoints.copy(busy = CheckpointBusy.Undo(userMessageId)))
            }
            try {
                awaitTurnIdle()
                val snapshot = _state.value
                val hash = awaitPreTurnHash(userMessageId)
                    ?: snapshot.checkpoints.preTurnByMessageId[userMessageId]
                if (hash == null) {
                    appendSystemError("No checkpoint for this message.")
                    return@launch
                }
                val tip = withContext(Dispatchers.IO) { checkpointStore.commit(context) }
                val frame = AcpRedoFrame(
                    items = snapshot.items,
                    ghostFromItemId = snapshot.checkpoints.ghostFromItemId,
                    preTurnByMessageId = snapshot.checkpoints.preTurnByMessageId,
                    undoableMessageIds = snapshot.checkpoints.undoableMessageIds,
                    worktreeTipCommit = tip,
                    wireSessionId = agentSessionId,
                )
                val ok = withContext(Dispatchers.IO) { checkpointStore.restore(context, hash) }
                if (!ok) {
                    appendSystemError("Could not restore workspace files.")
                    return@launch
                }
                val stack = (snapshot.checkpoints.redoStack + frame).takeLast(MAX_REDO_FRAMES)
                _state.update {
                    it.copy(
                        checkpoints = it.checkpoints.copy(
                            ghostFromItemId = userMessageId,
                            redoStack = stack,
                        ),
                    )
                }
                wireResync(ghostFromItemId = userMessageId)
            } finally {
                _state.update {
                    it.copy(checkpoints = it.checkpoints.copy(busy = null))
                }
                persistTimeline()
            }
        }
    }

    fun redoCheckpoint(userMessageId: Long) {
        scope.launch {
            _state.update {
                it.copy(checkpoints = it.checkpoints.copy(busy = CheckpointBusy.Redo(userMessageId)))
            }
            try {
                val snapshot = _state.value
                val frame = snapshot.checkpoints.redoStack.lastOrNull() ?: return@launch
                val tip = frame.worktreeTipCommit
                if (tip != null) {
                    val ok = withContext(Dispatchers.IO) { checkpointStore.restore(context, tip) }
                    if (!ok) {
                        appendSystemError("Could not redo file changes.")
                        return@launch
                    }
                }
                _state.update {
                    it.copy(
                        items = frame.items,
                        checkpoints = it.checkpoints.copy(
                            ghostFromItemId = frame.ghostFromItemId,
                            preTurnByMessageId = frame.preTurnByMessageId,
                            undoableMessageIds = frame.undoableMessageIds,
                            redoStack = it.checkpoints.redoStack.dropLast(1),
                        ),
                    )
                }
                persistTimeline()
            } finally {
                _state.update {
                    it.copy(checkpoints = it.checkpoints.copy(busy = null))
                }
            }
        }
    }

    /**
     * Truncates from [fromUserMessageId], optionally restores files, then resubmits [text].
     * [revertFiles] triggers shadow restore and wire resync; keep-files only truncates chat.
     */
    fun editAndResend(
        fromUserMessageId: Long,
        text: String,
        images: List<AcpBlock.Image>,
        files: List<AcpBlock.TextFile>,
        revertFiles: Boolean,
    ) {
        scope.launch {
            awaitTurnIdle()
            var optimisticMessageId: Long? = null
            var turnCompleted = false
            try {
                for (step in editResendSteps(revertFiles)) {
                    when (step) {
                        EditResendStep.Restore -> {
                            val hash = awaitPreTurnHash(fromUserMessageId)
                                ?: _state.value.checkpoints.preTurnByMessageId[fromUserMessageId]
                            if (hash != null) {
                                val ok = withContext(Dispatchers.IO) { checkpointStore.restore(context, hash) }
                                if (!ok) {
                                    appendSystemError("Could not restore workspace files.")
                                    return@launch
                                }
                            }
                        }
                        EditResendStep.Truncate -> truncateFromUserMessage(fromUserMessageId, clearRedo = true)
                        EditResendStep.WireResync -> {
                            wireResync(ghostFromItemId = null)
                            if (agentSessionId == null) {
                                appendSystemError("Agent session could not be refreshed after restore.")
                                return@launch
                            }
                        }
                        EditResendStep.BeginTurn -> {
                            optimisticMessageId = beginUserTurn(text, images, files) ?: return@launch
                        }
                        EditResendStep.CompleteTurn -> {
                            val messageId = optimisticMessageId ?: return@launch
                            completeUserTurn(messageId, text, images, files)
                            turnCompleted = true
                        }
                    }
                }
            } finally {
                if (optimisticMessageId != null && !turnCompleted) {
                    abortInFlightUserTurn(optimisticMessageId)
                }
            }
        }
    }

    private suspend fun awaitTurnIdle() {
        if (!turnInFlight) return
        agentSessionId?.let { notify("session/cancel", JSONObject().put("sessionId", it)) }
        failPending("Turn cancelled")
        var spins = 0
        while (turnInFlight && spins < 600) {
            kotlinx.coroutines.delay(100)
            spins++
        }
        if (turnInFlight) {
            abortInFlightUserTurn()
        }
    }

    private fun commitGhostBranch(clearRedo: Boolean) {
        val ghostFrom = _state.value.checkpoints.ghostFromItemId ?: return
        _state.update { state ->
            val kept = state.items.filter { it.id < ghostFrom }
            state.copy(
                items = kept,
                checkpoints = state.checkpoints.copy(
                    ghostFromItemId = null,
                    preTurnByMessageId = prunePreTurnMap(kept, state.checkpoints.preTurnByMessageId),
                    undoableMessageIds = pruneUndoableIds(kept, state.checkpoints.undoableMessageIds),
                    redoStack = if (clearRedo) emptyList() else state.checkpoints.redoStack,
                ),
            )
        }
        persistTimeline()
    }

    private fun truncateFromUserMessage(userMessageId: Long, clearRedo: Boolean = false) {
        _state.update { state ->
            val kept = state.items.filter { it.id < userMessageId }
            state.copy(
                items = kept,
                checkpoints = state.checkpoints.copy(
                    ghostFromItemId = null,
                    preTurnByMessageId = prunePreTurnMap(kept, state.checkpoints.preTurnByMessageId),
                    undoableMessageIds = pruneUndoableIds(kept, state.checkpoints.undoableMessageIds),
                    redoStack = if (clearRedo) emptyList() else state.checkpoints.redoStack,
                ),
            )
        }
        persistTimeline()
    }

    private fun prunePreTurnMap(items: List<AcpItem>, map: Map<Long, String>): Map<Long, String> {
        val userIds = items.filterIsInstance<AcpItem.UserMessage>().mapTo(HashSet()) { it.id }
        return map.filterKeys { it in userIds }
    }

    private fun pruneUndoableIds(items: List<AcpItem>, ids: Set<Long>): Set<Long> {
        val userIds = items.filterIsInstance<AcpItem.UserMessage>().mapTo(HashSet()) { it.id }
        return ids.filterTo(HashSet()) { it in userIds }
    }

    private suspend fun retireWireSession(sessionId: String?) {
        if (sessionId.isNullOrBlank()) return
        hideFromPastList(sessionId)
        runCatching { notify("session/cancel", JSONObject().put("sessionId", sessionId)) }
        runCatching {
            request("session/delete", JSONObject().put("sessionId", sessionId), timeoutMs = 5_000)
        }
    }

    /** Stops a wire session without removing it from session/list (listed conversation stays). */
    private suspend fun detachActiveWire(sessionId: String?) {
        if (sessionId.isNullOrBlank()) return
        runCatching { notify("session/cancel", JSONObject().put("sessionId", sessionId)) }
    }

    private fun hideFromPastList(sessionId: String) {
        deletedPrefs.edit().putStringSet(deletedKey(), deletedIds() + sessionId).apply()
    }

    private suspend fun maybeCompressContextAfterSummarize(userMessageId: Long) {
        val checkpoints = _state.value.checkpoints
        if (checkpoints.compressedForUserMessageId == userMessageId) return
        val items = _state.value.items
        val body = AcpContextCompress.agentSummaryAfterUser(items, userMessageId)
            ?: ConversationSummary.fromItems(items, null)
        compressContextAfterSummarize(body, userMessageId)
    }

    private suspend fun compressContextAfterSummarize(summaryBody: String, anchorUserMessageId: Long) {
        val formatted = AcpContextCompress.formatWireContext(summaryBody)
        _state.update {
            it.copy(
                checkpoints = it.checkpoints.copy(compressedForUserMessageId = anchorUserMessageId),
            )
        }
        wireResync(
            ghostFromItemId = null,
            summaryOverride = formatted,
            keepListedSessionVisible = true,
        )
        persistTimeline()
    }

    private suspend fun wireResync(
        ghostFromItemId: Long?,
        summaryOverride: String? = null,
        keepListedSessionVisible: Boolean = false,
    ) {
        val timelineBefore = AcpPersistedTimeline(
            items = _state.value.items,
            checkpoints = _state.value.checkpoints,
            pendingWireContext = pendingWireContext,
        )
        _state.update { it.copy(checkpoints = timelineBefore.checkpoints.copy(wireResyncing = true)) }
        suppressReplayForMcpReload = true
        try {
            val items = timelineBefore.items
            val summary = ConversationSummary.resolveWireSummary(items, ghostFromItemId, summaryOverride)
            val listed = rememberedSessionId()
            val oldActive = agentSessionId
            when {
                keepListedSessionVisible && !oldActive.isNullOrBlank() && oldActive == listed ->
                    detachActiveWire(oldActive)
                else -> retireWireSession(oldActive)
            }
            agentSessionId = null
            openSession(updateListed = false)
            val newSession = agentSessionId
            if (newSession == null) {
                pendingWireContext = null
                appendSystemError("Agent session could not be refreshed after restore.")
                return
            }
            hideFromPastList(newSession)
            val listedAfter = rememberedSessionId()
            if (listedAfter != null) bindConversation(listedAfter, newSession)
            suppressReplayForWireId = newSession
            pendingWireContext = summary
            adoptTimeline(
                timelineBefore.copy(
                    checkpoints = timelineBefore.checkpoints.copy(
                        wireResyncing = false,
                        compressedForUserMessageId = _state.value.checkpoints.compressedForUserMessageId,
                    ),
                    pendingWireContext = summary,
                ),
            )
            persistTimeline()
        } catch (failure: Throwable) {
            Log.w(TAG, "wire resync failed", failure)
            pendingWireContext = null
            appendSystemError("Agent session could not be refreshed after restore.")
        } finally {
            suppressReplayForMcpReload = false
            _state.update { state ->
                state.copy(
                    checkpoints = timelineBefore.checkpoints.copy(
                        wireResyncing = false,
                        compressedForUserMessageId = state.checkpoints.compressedForUserMessageId,
                    ),
                )
            }
        }
    }

    /**
     * Answers the agent's `session/request_permission`. That is a request *from* the agent, so
     * the answer is a JSON-RPC response carrying the agent's own id; it has nothing to do with
     * [pending], which holds this client's outgoing requests.
     */
    fun respondPermission(requestId: Any, optionId: String?) {
        val outcome = if (optionId == null) {
            JSONObject().put("outcome", JSONObject().put("outcome", "cancelled"))
        } else {
            JSONObject().put("outcome", JSONObject().put("outcome", "selected").put("optionId", optionId))
        }
        writeLine(
            JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", requestId)
                .put("result", outcome)
                .toString(),
        )
        _state.update { it.copy(pendingPermission = null) }
    }

    /** Ends the agent process. The stored agent install survives; only the process tree dies. */
    fun close() {
        closedByUser = true
        running.set(false)
        failPending("Session closed")
        runCatching { process?.destroy() }
        process = null
        scope.launch {
            _state.update { it.copy(phase = AcpPhase.CLOSED, agentBusy = false) }
        }
    }

    // --- Wire protocol --------------------------------------------------------------------------

    private fun request(method: String, params: JSONObject, timeoutMs: Long = 120_000): JSONObject {
        val id = nextId.getAndIncrement()
        val deferred = CompletableDeferred<JSONObject>()
        pending[id] = deferred
        writeLine(
            JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", id)
                .put("method", method)
                .put("params", params)
                .toString(),
        )
        return kotlinx.coroutines.runBlocking {
            try {
                withTimeout(timeoutMs) { deferred.await() }
            } catch (failure: Throwable) {
                pending.remove(id)
                throw failure
            }
        }
    }

    private fun notify(method: String, params: JSONObject) {
        writeLine(
            JSONObject()
                .put("jsonrpc", "2.0")
                .put("method", method)
                .put("params", params)
                .toString(),
        )
    }

    private fun writeLine(line: String) {
        synchronized(writeLock) {
            writer?.let { writer ->
                writer.write(line)
                writer.write("\n")
                writer.flush()
            }
        }
    }

    private fun handleLine(line: String) {
        if (line.isBlank()) return
        val message = JSONObject(line)
        val method = message.optString("method").ifBlank { null }

        when {
            // A request from the agent: it expects a response.
            method != null && message.has("id") -> {
                // JSON-RPC ids may be numbers or strings; either is echoed back unchanged.
                val id = message.get("id")
                when (method) {
                    "session/request_permission" -> handlePermissionRequest(id, message.optJSONObject("params"))
                    "fs/read_text_file", "fs/write_text_file" -> respondError(
                        id,
                        "TermFold does not expose the host filesystem",
                    )

                    else -> respondError(id, "Unknown method")
                }
            }

            method != null -> when (method) {
                "session/update" -> handleUpdate(message.optJSONObject("params"))
                else -> Log.d(TAG, "Ignored notification: $method")
            }

            // A response to one of our requests.
            message.has("id") -> {
                val id = message.optLong("id", -1)
                pending.remove(id)?.complete(message)
            }
        }
    }

    private fun respondError(id: Any, message: String) {
        writeLine(
            JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", id)
                .put("error", JSONObject().put("code", -32_000).put("message", message))
                .toString(),
        )
    }

    private fun handlePermissionRequest(id: Any, params: JSONObject?) {
        val blocked = AutomationReadOnlyPolicy.firstBlockedPath(
            automationReadOnlyGuestRoot,
            AutomationReadOnlyPolicy.pathsFromPermissionParams(params),
        )
        if (blocked != null) {
            appendSystemError(
                context.getString(
                    com.termfold.app.R.string.automation_read_only_linked,
                ) + " ($blocked)",
            )
            respondPermission(id, null)
            return
        }
        if (automationMode && AutomationWebToolPolicy.shouldBlockPermission(params)) {
            appendSystemError(context.getString(R.string.automation_use_browser_not_web))
            respondPermission(id, null)
            return
        }
        if (automationMode) {
            val guardCtx = automationGuardrails?.invoke()
            if (guardCtx != null) {
                when (
                    val verdict = AutomationGuardrails.evaluatePermission(
                        params,
                        guardCtx.policy,
                        guardCtx.constraintsFile,
                    )
                ) {
                    is AutomationGuardrailVerdict.Deny -> {
                        appendSystemError(verdict.reason)
                        respondPermission(id, null)
                        return
                    }
                    AutomationGuardrailVerdict.Allow -> Unit
                }
            }
        }
        val options = params?.optJSONArray("options") ?: JSONArray()
        val parsed = List(options.length()) { index ->
            val option = options.getJSONObject(index)
            AcpPermissionOption(
                id = option.optString("optionId"),
                name = option.text("name"),
                kind = option.optString("kind"),
            )
        }
        if (agent.id == "cursor" && CursorAcpPrefs.allowEverything(context)) {
            val toolPaths = _state.value.items.filterIsInstance<AcpItem.ToolCall>()
                .lastOrNull { it.toolCallId == params?.optJSONObject("toolCall")?.optString("toolCallId") }
                ?.paths
                ?: emptyList()
            val blockedTool = AutomationReadOnlyPolicy.firstBlockedPath(automationReadOnlyGuestRoot, toolPaths)
            val guardBlocked = automationMode && automationGuardrails?.invoke()?.let { guardCtx ->
                val verdict = AutomationGuardrails.evaluatePermission(
                    params,
                    guardCtx.policy,
                    guardCtx.constraintsFile,
                )
                verdict is AutomationGuardrailVerdict.Deny
            } == true
            if (
                blockedTool == null &&
                !guardBlocked &&
                !(automationMode && AutomationWebToolPolicy.shouldBlockPermission(params))
            ) {
                CursorAcpPrefs.preferredAllowOptionId(parsed)?.let { optionId ->
                    respondPermission(id, optionId)
                    return
                }
            }
        }
        val toolCallId = params?.optJSONObject("toolCall")?.optString("toolCallId")
        _state.update {
            it.copy(
                pendingPermission = AcpPendingPermission(
                    requestId = id,
                    toolCallId = toolCallId,
                    options = parsed,
                ),
            )
        }
    }

    private fun guestLaunchArgs(base: List<String>): List<String> {
        if (agent.id != "cursor") return base
        val extras = CursorAcpPrefs.extraLaunchArgs(CursorAcpPrefs.allowEverything(context))
        if (extras.isEmpty()) return base
        if (base.any { it == "--force" || it == "--yolo" }) return base
        return base + extras
    }

    private fun handleUpdate(params: JSONObject?) {
        val wireSession = params?.optString("sessionId")?.takeIf { it.isNotBlank() }
        if (wireSession != null && wireSession != agentSessionId) {
            Log.d(TAG, "Ignoring session/update for inactive session $wireSession")
            return
        }
        val update = params?.optJSONObject("update") ?: return
        val suppressWire = suppressReplayForWireId
        val dropPassiveReplay = suppressReplayForMcpReload ||
            (suppressWire != null && (wireSession == null || wireSession == suppressWire))
        if (dropPassiveReplay) {
            when (update.optString("sessionUpdate")) {
                "agent_message_chunk",
                "user_message_chunk",
                "agent_thought_chunk",
                "tool_call",
                "tool_call_update",
                -> return
            }
        }
        when (update.optString("sessionUpdate")) {
            "agent_message_chunk" -> blocks(update.opt("content")).forEach { block ->
                when (block) {
                    is AcpBlock.Text -> appendText(block.text, asThought = false)
                    is AcpBlock.Image -> appendImageRow(block)
                    is AcpBlock.TextFile -> Unit // only ever produced by the user's composer
                }
            }

            "user_message_chunk" -> blocks(update.opt("content")).forEach { block ->
                when (block) {
                    is AcpBlock.Text -> appendUserText(block.text)
                    is AcpBlock.Image -> _state.update { state ->
                        state.copy(items = state.items + AcpItem.UserMessage(nextItemId(), "", listOf(block)))
                    }
                    is AcpBlock.TextFile -> Unit
                }
            }

            "agent_thought_chunk" -> blocks(update.opt("content")).forEach { block ->
                if (block is AcpBlock.Text) appendText(block.text, asThought = true)
            }

            "tool_call" -> {
                val content = update.optJSONArray("content")
                val parsed = AcpToolContentParser.parseContent(content)
                val item = AcpItem.ToolCall(
                    id = nextItemId(),
                    toolCallId = update.optString("toolCallId"),
                    title = update.optString("title").ifBlank { "Tool call" },
                    kind = update.optString("kind").ifBlank { "other" },
                    status = update.optString("status").ifBlank { "in_progress" },
                    detail = parsed.detail,
                    paths = toolPaths(update),
                    fileDiffs = parsed.fileDiffs,
                )
                _state.update { state ->
                    val dupIdx = state.items.indexOfLast {
                        it is AcpItem.ToolCall && it.toolCallId == item.toolCallId
                    }
                    if (dupIdx < 0) {
                        state.copy(items = state.items + item)
                    } else {
                        val prev = state.items[dupIdx] as AcpItem.ToolCall
                        val merged = item.copy(
                            id = prev.id,
                            detail = item.detail.ifBlank { prev.detail },
                            paths = item.paths.ifEmpty { prev.paths },
                            fileDiffs = mergeToolFileDiffUpdates(prev.fileDiffs, item.fileDiffs),
                        )
                        state.copy(
                            items = state.items.toMutableList().also { it[dupIdx] = merged },
                        )
                    }
                }
            }

            "tool_call_update" -> {
                val toolCallId = update.optString("toolCallId")
                _state.update { state ->
                    state.copy(
                        items = state.items.map { item ->
                            if (item !is AcpItem.ToolCall || item.toolCallId != toolCallId) {
                                item
                            } else {
                                val content = update.optJSONArray("content")
                                val parsed = if (content != null && content.length() > 0) {
                                    AcpToolContentParser.parseContent(content)
                                } else {
                                    null
                                }
                                item.copy(
                                    title = update.optString("title").ifBlank { item.title },
                                    status = update.optString("status").ifBlank { item.status },
                                    // Updates carry the tool's content so far, so it replaces
                                    // rather than appends; an update without content keeps it.
                                    detail = parsed?.detail?.ifBlank { item.detail } ?: item.detail,
                                    paths = toolPaths(update).ifEmpty { item.paths },
                                    fileDiffs = if (parsed?.fileDiffs?.isNotEmpty() == true) {
                                        mergeToolFileDiffUpdates(item.fileDiffs, parsed.fileDiffs)
                                    } else {
                                        item.fileDiffs
                                    },
                                )
                            }
                        },
                    )
                }
            }

            "config_option_update" -> {
                val options = parseConfigOptions(update.optJSONArray("configOptions"))
                _state.update { state ->
                    state.copy(settings = options + state.settings.filter { it.source != AcpSetting.Source.CONFIG_OPTION })
                }
            }

            "current_mode_update" -> {
                val modeId = update.text("currentModeId")
                if (modeId.isNotBlank()) {
                    _state.update { state ->
                        state.copy(
                            settings = state.settings.map {
                                if (it.source == AcpSetting.Source.MODE) it.copy(current = modeId) else it
                            },
                        )
                    }
                }
            }

            "available_commands_update" -> {
                val list = update.optJSONArray("availableCommands") ?: JSONArray()
                val commands = List(list.length()) { list.optJSONObject(it) }.filterNotNull().mapNotNull { cmd ->
                    val name = cmd.text("name").trim().removePrefix("/")
                    if (name.isBlank()) return@mapNotNull null
                    AcpCommand(
                        name = name,
                        description = cmd.text("description"),
                        hint = cmd.optJSONObject("input")?.optString("hint").orEmpty(),
                    )
                }
                _state.update { it.copy(commands = commands) }
            }

            "plan" -> {
                val entries = update.optJSONArray("entries") ?: JSONArray()
                val item = AcpItem.Plan(
                    id = nextItemId(),
                    entries = List(entries.length()) { index ->
                        val entry = entries.getJSONObject(index)
                        AcpItem.PlanEntry(
                            text = entry.optString("content"),
                            status = entry.optString("status"),
                        )
                    },
                )
                _state.update { state ->
                    val withoutOld = state.items.filterNot { it is AcpItem.Plan }
                    state.copy(items = withoutOld + item)
                }
            }
        }
    }

    /**
     * Merges a text chunk into the timeline. Consecutive chunks of the same kind — message into
     * message, thought into thought — append to the trailing item, and a chunk of a different
     * kind starts a new one, which is what keeps one reasoning phase one card.
     */
    private fun appendText(text: String, asThought: Boolean) {
        if (text.isEmpty()) return
        _state.update { state ->
            val items = state.items.toMutableList()
            val last = items.lastOrNull()
            val continues = last is AcpItem.Thought && asThought ||
                last is AcpItem.AgentText && !asThought
            if (continues) {
                items[items.size - 1] = if (last is AcpItem.Thought) {
                    last.copy(text = last.text + text)
                } else {
                    (last as AcpItem.AgentText).copy(text = last.text + text)
                }
            } else if (asThought) {
                items.add(AcpItem.Thought(nextItemId(), text))
            } else {
                items.add(AcpItem.AgentText(nextItemId(), text))
            }
            state.copy(items = items)
        }
    }

    /**
     * A user message chunk. These arrive when an earlier session is replayed; consecutive
     * chunks join one bubble. A live echo of the prompt just sent is skipped, since the
     * composer already added it.
     */
    private fun appendUserText(text: String) {
        if (text.isEmpty()) return
        _state.update { state ->
            val items = state.items.toMutableList()
            val last = items.lastOrNull()
            if (!state.restoring && last is AcpItem.UserMessage) {
                if (state.checkpoints.preTurnByMessageId.containsKey(last.id)) return@update state
                if (last.text.trim() == text.trim()) return@update state
            }
            if (last is AcpItem.UserMessage && state.restoring && last.images.isEmpty() && last.files.isEmpty()) {
                items[items.lastIndex] = last.copy(text = last.text + text)
            } else {
                items += AcpItem.UserMessage(nextItemId(), text, emptyList())
            }
            state.copy(items = items)
        }
    }

    private fun appendImageRow(block: AcpBlock.Image) {
        _state.update { state ->
            state.copy(items = state.items + AcpItem.UserMessage(nextItemId(), "", listOf(block)))
        }
    }

    /**
     * Content blocks from an update. ACP sends a single block for message and thought chunks;
     * an array is accepted too, since some agents batch them.
     */
    private fun blocks(content: Any?): List<AcpBlock> {
        val list = when (content) {
            is JSONObject -> listOf(content)
            is JSONArray -> List(content.length()) { content.optJSONObject(it) }.filterNotNull()
            else -> return emptyList()
        }
        return list.map { block ->
            when (block.optString("type")) {
                "image" -> AcpBlock.Image(
                    base64 = block.optString("data"),
                    mimeType = block.optString("mimeType", "image/png"),
                )

                else -> AcpBlock.Text(block.optString("text"))
            }
        }.filterNot { it is AcpBlock.Text && it.text.isEmpty() }
    }

    /**
     * The files a tool call touches. `locations` is the protocol's field for it; agents that
     * leave it out usually still name the file in `rawInput`, under one of a few common keys.
     * Diff content also names its file.
     */
    private fun toolPaths(update: JSONObject): List<String> {
        val paths = linkedSetOf<String>()
        update.optJSONArray("locations")?.let { locations ->
            for (i in 0 until locations.length()) {
                locations.optJSONObject(i)?.optString("path")?.takeIf { it.isNotBlank() }?.let(paths::add)
            }
        }
        update.optJSONObject("rawInput")?.let { input ->
            PATH_KEYS
                .mapNotNull { key -> input.optString(key).takeIf { it.isNotBlank() } }
                .forEach(paths::add)
            input.optJSONArray("paths")?.let { list ->
                for (i in 0 until list.length()) list.optString(i).takeIf { it.isNotBlank() }?.let(paths::add)
            }
        }
        update.optJSONArray("content")?.let { content ->
            AcpToolContentParser.pathsFromContent(content).forEach(paths::add)
        }
        // A replayed session (session/load) often has no input at all: agents such as pi-acp
        // replay only the tool's result. The path can still be recovered from that result,
        // either as a field of the raw output or from its message ("Successfully wrote 120
        // bytes to snake.html").
        if (paths.isEmpty()) {
            update.opt("rawOutput")?.let { collectPathFields(it, paths, depth = 0) }
        }
        if (paths.isEmpty()) {
            val text = AcpToolContentParser.parseContent(update.optJSONArray("content")).detail +
                "\n" + rawOutputText(update.opt("rawOutput"))
            RESULT_PATH.find(text)?.groupValues?.get(1)?.trimEnd('.', ',', ':', ')', '\'', '"')
                ?.takeIf { it.isNotBlank() && it.length < 300 }
                ?.let(paths::add)
        }
        return paths.toList()
    }

    private fun collectPathFields(value: Any?, out: MutableSet<String>, depth: Int) {
        if (depth > 3 || out.size >= 4) return
        when (value) {
            is JSONObject -> {
                PATH_KEYS.forEach { key -> value.optString(key).takeIf { it.isNotBlank() && '\n' !in it }?.let(out::add) }
                value.keys().forEach { key -> collectPathFields(value.opt(key), out, depth + 1) }
            }
            is JSONArray -> for (i in 0 until minOf(value.length(), 8)) collectPathFields(value.opt(i), out, depth + 1)
        }
    }

    /** The text parts of a raw tool result (pi-style `{content:[{type:"text",text}]}`). */
    private fun rawOutputText(value: Any?): String = when (value) {
        is JSONObject -> value.optJSONArray("content")?.let { arr ->
            List(arr.length()) { arr.optJSONObject(it)?.optString("text").orEmpty() }.joinToString("\n")
        }.orEmpty()
        is String -> value
        else -> ""
    }

    private fun appendSystemError(message: String) {
        _state.update { state ->
            state.copy(
                items = state.items + AcpItem.Notice(nextItemId(), message.take(500)),
            )
        }
    }

    private fun nextItemId(): Long = nextId.getAndIncrement() + 1_000_000

    private fun adoptTimeline(snapshot: AcpPersistedTimeline) {
        val maxId = snapshot.items.maxOfOrNull { it.id } ?: return
        nextId.set(AcpTimelineCodec.nextCounter(maxId))
        pendingWireContext = snapshot.pendingWireContext
        _state.update {
            it.copy(
                items = snapshot.items,
                checkpoints = AcpTimelineCodec.durableCheckpoints(snapshot.checkpoints),
            )
        }
    }

    private fun persistTimeline() {
        val state = _state.value
        val snapshot = AcpPersistedTimeline(
            items = state.items,
            checkpoints = AcpTimelineCodec.durableCheckpoints(state.checkpoints),
            pendingWireContext = pendingWireContext,
        )
        runCatching {
            AcpTimelineStore.save(context, sessionKey, snapshot)
            rememberedSessionId()?.let { listed ->
                AcpTimelineStore.save(context, sessionKey, snapshot, listed)
            }
        }
    }

    private companion object {
        const val TAG = "AcpClient"
        const val MAX_REDO_FRAMES = 20

        /** session/prompt answers only when the turn ends, which can take a long while. */
        const val PROMPT_TIMEOUT_MS = 6L * 60 * 60 * 1000

        /** Message fragments that mean "credentials missing", the one error the auth screen fits. */
        val AUTH_HINTS = listOf(
            "auth", "login", "log in", "log-in", "sign in", "sign-in", "credential",
            "api key", "apikey", "api-key", "token", "unauthorised", "unauthorized",
        )
    }
}

/** Field names agents use for the file a tool works on. */
private val PATH_KEYS = listOf("path", "file_path", "filePath", "file", "filename", "target_file", "notebook_path")

/**
 * A file named in a tool's result message: "Successfully wrote 120 bytes to snake.html",
 * "Successfully replaced text in src/app.ts", "Created docs/notes.md", "Updated file: a.py".
 */
internal val RESULT_PATH = Regex(
    """(?:wrote(?:\s+\d+\s+\w+)?\s+to|replaced\s+\S+(?:\s+\S+)?\s+in|(?:created|updated|edited|modified|deleted|wrote)(?:\s+file)?:?)\s+[`'"]?([^\s`'"]+\.[A-Za-z0-9]{1,8}|/[^\s`'"]+)""",
    RegexOption.IGNORE_CASE,
)
