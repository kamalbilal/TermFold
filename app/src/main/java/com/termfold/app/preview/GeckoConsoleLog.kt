package com.termfold.app.preview

import android.webkit.ConsoleMessage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

/** Session-scoped browser console lines from Gecko + agent eval (headless and preview). */
object GeckoConsoleLog {
    data class Entry(
        val id: Long,
        val level: ConsoleMessage.MessageLevel,
        val text: String,
        val where: String,
    )

    private val nextId = AtomicLong(0)
    private val bySession = ConcurrentHashMap<String, MutableList<Entry>>()
    private val listeners = ConcurrentHashMap<String, CopyOnWriteArrayList<(Entry) -> Unit>>()

    fun record(sessionKey: String, level: String, text: String, where: String = "") {
        if (sessionKey.isBlank() || text.isBlank()) return
        val entry = Entry(nextId.getAndIncrement(), mapLevel(level), text, where)
        val list = bySession.getOrPut(sessionKey) { java.util.Collections.synchronizedList(ArrayList()) }
        synchronized(list) {
            list.add(entry)
            if (list.size > 400) list.removeAt(0)
        }
        listeners[sessionKey]?.forEach { runCatching { it(entry) } }
    }

    fun clear(sessionKey: String) {
        bySession.remove(sessionKey)
    }

    fun addListener(sessionKey: String, handler: (Entry) -> Unit): () -> Unit {
        val list = listeners.getOrPut(sessionKey) { CopyOnWriteArrayList() }
        list.add(handler)
        return { list.remove(handler) }
    }

    fun size(sessionKey: String): Int = bySession[sessionKey]?.size ?: 0

    /** Bookmark for [errorsSince]; matches preview [DriverUi.consoleSize] semantics. */
    fun marker(sessionKey: String): Long = nextId.get()

    fun errorsSince(sessionKey: String, since: Long): Int {
        val list = bySession[sessionKey] ?: return 0
        return synchronized(list) {
            list.count { it.id >= since && it.level == ConsoleMessage.MessageLevel.ERROR }
        }
    }

    fun text(sessionKey: String, errorsOnly: Boolean, since: Long): String {
        val list = bySession[sessionKey] ?: return if (errorsOnly) "No console errors." else "The console is empty."
        val snapshot = synchronized(list) {
            list.filter { it.id >= since && (!errorsOnly || it.level == ConsoleMessage.MessageLevel.ERROR) }
        }
        if (snapshot.isEmpty()) return if (errorsOnly) "No console errors." else "The console is empty."
        return "Console (${snapshot.size}):\n" + snapshot.joinToString("\n") { line ->
            "[" + line.level.name.lowercase() + "] " + line.text +
                if (line.where.isNotEmpty()) "  (${line.where})" else ""
        }
    }

    private fun mapLevel(raw: String): ConsoleMessage.MessageLevel = when (raw.lowercase()) {
        "error" -> ConsoleMessage.MessageLevel.ERROR
        "warn", "warning" -> ConsoleMessage.MessageLevel.WARNING
        "debug" -> ConsoleMessage.MessageLevel.DEBUG
        "tip" -> ConsoleMessage.MessageLevel.TIP
        else -> ConsoleMessage.MessageLevel.LOG
    }
}
