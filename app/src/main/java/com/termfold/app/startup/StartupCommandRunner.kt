package com.termfold.app.startup

import android.content.Context
import com.termfold.app.core.Folder
import com.termfold.app.shell.ProotCommand
import com.termfold.app.shell.Projects
import com.termfold.app.shell.ShellPaths
import com.termfold.app.shell.ShellRuntime
import com.termfold.app.shell.ShellSetup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

/**
 * Runs saved startup commands inside the guest, rooted at the linked project's directory.
 *
 * Each entry is a single PRoot process (typically `bash -lc` running npm, vite, etc.). Stopping
 * destroys that process; PRoot's `--kill-on-exit` tears down the guest tree with it.
 * Output is tailed from a host log file for the live terminal pane in the UI.
 */
object StartupCommandRunner {

    private const val MAX_LOG_CHARS = 48_000

    private val lock = Any()
    private val jobs = mutableMapOf<String, RunningJob>()
    private val _runningIds = MutableStateFlow<Set<String>>(emptySet())
    val runningIds: StateFlow<Set<String>> = _runningIds.asStateFlow()

    private val _logs = MutableStateFlow<Map<String, String>>(emptyMap())
    val logs: StateFlow<Map<String, String>> = _logs.asStateFlow()

    private data class RunningJob(
        val process: Process,
        val log: File,
    )

    fun isRunning(id: String): Boolean = synchronized(lock) {
        jobs[id]?.process?.isAlive == true
    }

    fun logText(id: String): String = _logs.value[id].orEmpty()

    /** Starts [entry] in [folder]'s guest path. Returns an error message or null on success. */
    fun start(context: Context, entry: StartupCommand, folder: Folder): String? {
        if (!ShellRuntime.isReady(context)) return "Linux environment is not ready yet."
        val guestCwd = Projects.guestPath(context, folder.path)
            ?: return "That project is not inside the Linux environment."

        if (!ShellSetup.isDone(context)) {
            ShellSetup.install(context, ShellPaths.rootfsDir(context))
        }

        stopInternal(entry.id, keepLog = false)

        val log = File(ShellPaths.tempDir(context), "startup-${entry.id}-${System.nanoTime()}.log")
        publishLog(entry.id, "")

        val command = ProotCommand.build(
            context = context,
            argv = listOf("/bin/bash", "-lc", entry.command.trim()),
            guestCwd = guestCwd,
        )

        val process = runCatching {
            ProcessBuilder(command).apply {
                environment().clear()
                environment().putAll(ProotCommand.hostEnvironment(context))
                redirectErrorStream(true)
                redirectOutput(log)
            }.start()
        }.getOrElse { return it.message ?: "Could not start command." }

        Thread.sleep(350)
        tailOnce(entry.id, log)
        if (!process.isAlive) {
            val tail = logText(entry.id).trim()
            log.delete()
            publishLog(entry.id, tail)
            return tail.ifBlank { "Command exited immediately." }
        }

        synchronized(lock) {
            jobs[entry.id] = RunningJob(process, log)
            refreshRunningLocked()
        }

        startTailThread(entry.id, process, log)

        return null
    }

    fun stop(id: String) {
        stopInternal(id, keepLog = true)
    }

    /** Drops cached output after the command is removed from settings. */
    fun forget(id: String) {
        stop(id)
        _logs.update { it - id }
    }

    private fun stopInternal(id: String, keepLog: Boolean) {
        val job = synchronized(lock) {
            jobs.remove(id)?.also { refreshRunningLocked() }
        } ?: return
        job.process.destroyForcibly()
        tailOnce(id, job.log)
        if (!keepLog) {
            publishLog(id, "")
        } else {
            appendLogLine(id, "\n[stopped]")
        }
        job.log.delete()
    }

    private fun startTailThread(id: String, process: Process, log: File) {
        Thread {
            while (true) {
                val alive = runCatching { process.isAlive }.getOrDefault(false)
                tailOnce(id, log)
                if (!alive) break
                Thread.sleep(250)
            }
            val code = runCatching { process.waitFor(); process.exitValue() }.getOrNull()
            tailOnce(id, log)
            synchronized(lock) {
                if (jobs[id]?.process === process) {
                    jobs.remove(id)
                    refreshRunningLocked()
                }
            }
            appendLogLine(id, "\n[exited${code?.let { " with code $it" }.orEmpty()}]")
            log.delete()
        }.start()
    }

    private fun tailOnce(id: String, log: File) {
        if (!log.isFile) return
        val text = runCatching { log.readText() }.getOrDefault("")
        publishLog(id, text)
    }

    private fun publishLog(id: String, text: String) {
        val trimmed = if (text.length > MAX_LOG_CHARS) text.takeLast(MAX_LOG_CHARS) else text
        _logs.update { current -> current + (id to trimmed) }
    }

    private fun appendLogLine(id: String, line: String) {
        _logs.update { current ->
            val prev = current[id].orEmpty()
            val combined = prev + line
            val trimmed = if (combined.length > MAX_LOG_CHARS) combined.takeLast(MAX_LOG_CHARS) else combined
            current + (id to trimmed)
        }
    }

    private fun refreshRunningLocked() {
        _runningIds.value = jobs.filterValues { it.process.isAlive }.keys.toSet()
    }
}
