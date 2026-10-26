package com.termfold.app.preview.network

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import com.termfold.app.preview.BrowserBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
/** Session-scoped HTTP capture for native Network UI and agent list/detail tools. */
object NetworkCaptureStore {
    /** Ring buffer size; keep modest on mobile to avoid OOM under Preserve log. */
    const val MAX_ENTRIES = 120
    const val MAX_BODY_BYTES = 128 * 1024
    private const val MAX_HEADER_CHARS = 8 * 1024
    private const val REVISION_DEBOUNCE_MS = 150L
    const val MCP_BODY_TRUNCATE = 6 * 1024
    const val LIST_DEFAULT_LIMIT = 40
    const val LIST_MAX_LIMIT = 80

    data class Entry(
        val id: Long,
        val requestId: String,
        val method: String,
        val url: String,
        val resourceType: String,
        var status: Int,
        var outcome: String,
        var detail: String,
        val startedAt: Long,
        var endedAt: Long,
        var requestHeaders: String,
        var responseHeaders: String,
        var requestBodyPath: String?,
        var responseBodyPath: String?,
        var requestBodySize: Int,
        var responseBodySize: Int,
        val captureGeneration: Int = BrowserBridge.networkGeneration,
    ) {
        val durationMs: Long
            get() = if (endedAt > startedAt) endedAt - startedAt else 0L

        val failed: Boolean
            get() = outcome == "failed" || outcome == "blocked" || (status >= 400 && status != 0)

        val isXHR: Boolean
            get() = resourceType == "xmlhttprequest" || resourceType == "fetch" || resourceType == "xhr"

        fun summaryLine(): String {
            val path = runCatching {
                val u = java.net.URI(url)
                u.path + (u.query?.let { "?$it" } ?: "")
            }.getOrDefault(url.take(120))
            val st = when {
                status > 0 -> status.toString()
                outcome == "blocked" -> "(blocked:${detail.ifBlank { "devtools" }})"
                else -> outcome.take(12)
            }
            return "#$id $method $st $path (${durationMs}ms) [$resourceType]"
        }
    }

    private val nextId = AtomicLong(1)
    private val preserveBySession = ConcurrentHashMap<String, Boolean>()
    private val bySession = ConcurrentHashMap<String, MutableList<Entry>>()
    private val byRequestId = ConcurrentHashMap<String, ConcurrentHashMap<String, Entry>>()
    private val listeners = ConcurrentHashMap<String, CopyOnWriteArrayList<(Entry) -> Unit>>()
    private val revision = MutableStateFlow(0)
    val revisionFlow: StateFlow<Int> = revision

    private val ingestScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ingestMutex = Mutex()
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val bumpRunnable = Runnable { revision.update { it + 1 } }

    @Volatile
    private var appContext: Context? = null

    /** Unit tests run ingest on the calling thread and apply revisions immediately. */
    @Volatile
    var synchronousIngestForTests: Boolean = false

    fun attach(context: Context) {
        appContext = context.applicationContext
    }

    fun setPreserve(sessionKey: String, enabled: Boolean) {
        if (sessionKey.isBlank()) return
        preserveBySession[sessionKey] = enabled
        NetworkCaptureHub.pushCaptureOptions(sessionKey)
    }

    fun isPreserve(sessionKey: String): Boolean = preserveBySession[sessionKey] == true

    fun preserveStatus(sessionKey: String): String =
        if (isPreserve(sessionKey)) "on" else "off"

    fun onMainFrameNavigation(sessionKey: String) {
        if (sessionKey.isBlank() || isPreserve(sessionKey)) return
        clear(sessionKey)
    }

    fun clear(sessionKey: String) {
        if (sessionKey.isBlank()) return
        bySession.remove(sessionKey)
        byRequestId.remove(sessionKey)
        listeners.remove(sessionKey)
        deleteBodies(sessionKey)
        flushRevision()
    }

    fun addListener(sessionKey: String, handler: (Entry) -> Unit): () -> Unit {
        val list = listeners.getOrPut(sessionKey) { CopyOnWriteArrayList() }
        list.add(handler)
        return { list.remove(handler) }
    }

    fun entries(sessionKey: String): List<Entry> {
        val list = bySession[sessionKey] ?: return emptyList()
        return synchronized(list) { list.toList() }
    }

    fun entry(sessionKey: String, id: Long): Entry? =
        entries(sessionKey).firstOrNull { it.id == id }

    fun failedCount(sessionKey: String): Int = entries(sessionKey).count { it.failed }

    fun ingest(json: JSONObject) {
        if (synchronousIngestForTests) {
            ingestOnWorker(json)
            flushRevision()
            return
        }
        ingestScope.launch {
            ingestMutex.withLock {
                runCatching { ingestOnWorker(json) }
            }
        }
    }

    private fun ingestOnWorker(json: JSONObject) {
        val sessionKey = json.optString("sessionKey")
        if (sessionKey.isBlank()) return
        val phase = json.optString("phase", "legacy")
        when (phase) {
            "start" -> ingestStart(sessionKey, json)
            "headers" -> ingestHeaders(sessionKey, json)
            "body" -> ingestBody(sessionKey, json)
            "done", "legacy" -> ingestDone(sessionKey, json)
            else -> ingestDone(sessionKey, json)
        }
    }

    /** Back-compat for simple outcome-only events. */
    fun recordLegacy(sessionKey: String, method: String, url: String, outcome: String, detail: String = "") {
        ingest(
            JSONObject()
                .put("sessionKey", sessionKey)
                .put("phase", "legacy")
                .put("requestId", "legacy-${System.nanoTime()}")
                .put("method", method)
                .put("url", url)
                .put("outcome", outcome)
                .put("detail", detail)
                .put("resourceType", "other"),
        )
    }

    fun listText(
        sessionKey: String,
        sinceCursor: Long,
        failedOnly: Boolean,
        xhrOnly: Boolean,
        urlContains: String?,
        limit: Int,
        sinceGeneration: Int = 0,
    ): Pair<String, Long> {
        var list = entries(sessionKey).filter { it.id > sinceCursor }
        if (sinceGeneration > 0) list = list.filter { it.captureGeneration >= sinceGeneration }
        if (failedOnly) list = list.filter { it.failed }
        if (xhrOnly) list = list.filter { it.isXHR }
        if (!urlContains.isNullOrBlank()) {
            val q = urlContains.lowercase()
            list = list.filter { it.url.lowercase().contains(q) }
        }
        val cap = limit.coerceIn(1, LIST_MAX_LIMIT)
        val slice = list.takeLast(cap)
        val maxCursor = slice.maxOfOrNull { it.id } ?: sinceCursor
        if (slice.isEmpty()) {
            val empty = if (failedOnly) "No failed requests." else "No requests recorded yet."
            return Pair(empty, sinceCursor)
        }
        val header = "Network (${slice.size} rows, cursor=$maxCursor, preserve=${preserveStatus(sessionKey)}):\n"
        return Pair(header + slice.joinToString("\n") { it.summaryLine() }, maxCursor)
    }

    fun detailText(sessionKey: String, entryId: Long, part: String): String {
        val e = entry(sessionKey, entryId) ?: return "No entry #$entryId."
        return when (part.lowercase()) {
            "overview" -> buildString {
                append("id=").append(e.id).append('\n')
                append("method=").append(e.method).append('\n')
                append("url=").append(e.url).append('\n')
                append("status=").append(e.status).append('\n')
                append("type=").append(e.resourceType).append('\n')
                append("outcome=").append(e.outcome).append('\n')
                if (e.detail.isNotBlank()) append("detail=").append(e.detail).append('\n')
                append("duration_ms=").append(e.durationMs).append('\n')
            }
            "headers" -> buildString {
                append("Request headers:\n").append(e.requestHeaders.ifBlank { "(none)" }).append("\n\n")
                append("Response headers:\n").append(e.responseHeaders.ifBlank { "(none)" })
            }
            "request_body" -> readBodyForMcp(e.requestBodyPath, e.requestBodySize)
            "response_body" -> readBodyForMcp(e.responseBodyPath, e.responseBodySize)
            else -> "Unknown part. Use overview, headers, request_body, or response_body."
        }
    }

    private fun ingestStart(sessionKey: String, json: JSONObject) {
        val requestId = json.optString("requestId")
        if (requestId.isBlank()) return
        if (findEntry(sessionKey, requestId) != null) return
        val entry = Entry(
            id = nextId.getAndIncrement(),
            requestId = requestId,
            method = json.optString("method", "GET"),
            url = json.optString("url"),
            resourceType = json.optString("resourceType", "other"),
            status = 0,
            outcome = "pending",
            detail = "",
            startedAt = json.optLong("time", System.currentTimeMillis()),
            endedAt = 0L,
            requestHeaders = "",
            responseHeaders = "",
            requestBodyPath = null,
            responseBodyPath = null,
            requestBodySize = 0,
            responseBodySize = 0,
        )
        putEntry(sessionKey, entry)
        json.optString("requestBody").takeIf { it.isNotBlank() }?.let { body ->
            saveBody(sessionKey, entry, "req", body, append = false)?.let { (path, size) ->
                entry.requestBodyPath = path
                entry.requestBodySize = size
            }
        }
    }

    private fun ingestHeaders(sessionKey: String, json: JSONObject) {
        val entry = findEntry(sessionKey, json.optString("requestId")) ?: return
        if (json.has("requestHeaders")) entry.requestHeaders = truncateHeaders(json.optString("requestHeaders"))
        if (json.has("responseHeaders")) entry.responseHeaders = truncateHeaders(json.optString("responseHeaders"))
        if (json.has("status")) entry.status = json.optInt("status")
        bumpDebounced()
    }

    private fun ingestBody(sessionKey: String, json: JSONObject) {
        if (!NetworkCaptureHub.bodyCaptureEnabled(sessionKey)) return
        val entry = findEntry(sessionKey, json.optString("requestId")) ?: return
        val which = json.optString("which", "response")
        val text = json.optString("text")
        if (text.isBlank()) return
        val prefix = if (which == "request") "req" else "resp"
        val append = if (which == "request") entry.requestBodyPath != null else entry.responseBodyPath != null
        saveBody(sessionKey, entry, prefix, text, append = append)?.let { (path, size) ->
            if (which == "request") {
                entry.requestBodyPath = path
                entry.requestBodySize = size
            } else {
                entry.responseBodyPath = path
                entry.responseBodySize = size
            }
        }
        bumpDebounced()
    }

    private fun ingestDone(sessionKey: String, json: JSONObject) {
        val requestId = json.optString("requestId")
        val entry = if (requestId.isNotBlank()) {
            findEntry(sessionKey, requestId)
        } else null
        val target = entry ?: Entry(
            id = nextId.getAndIncrement(),
            requestId = requestId.ifBlank { "legacy-${System.nanoTime()}" },
            method = json.optString("method", "GET"),
            url = json.optString("url"),
            resourceType = json.optString("resourceType", "other"),
            status = json.optInt("status", 0),
            outcome = json.optString("outcome", "ok"),
            detail = json.optString("detail", ""),
            startedAt = json.optLong("time", System.currentTimeMillis()),
            endedAt = json.optLong("endedAt", System.currentTimeMillis()),
            requestHeaders = json.optString("requestHeaders", ""),
            responseHeaders = json.optString("responseHeaders", ""),
            requestBodyPath = null,
            responseBodyPath = null,
            requestBodySize = 0,
            responseBodySize = 0,
        ).also { putEntry(sessionKey, it) }

        target.status = json.optInt("status", target.status)
        target.outcome = when {
            json.has("outcome") -> json.optString("outcome")
            target.outcome == "pending" -> "ok"
            else -> target.outcome
        }
        target.detail = if (json.has("detail")) json.optString("detail") else target.detail
        target.endedAt = json.optLong("endedAt", System.currentTimeMillis())
        if (json.has("requestHeaders") && target.requestHeaders.isBlank()) {
            target.requestHeaders = truncateHeaders(json.optString("requestHeaders"))
        }
        if (json.has("responseHeaders") && target.responseHeaders.isBlank()) {
            target.responseHeaders = truncateHeaders(json.optString("responseHeaders"))
        }
        listeners[sessionKey]?.forEach { runCatching { it(target) } }
        bumpDebounced()
    }

    private fun findEntry(sessionKey: String, requestId: String): Entry? {
        if (requestId.isBlank()) return null
        return byRequestId[sessionKey]?.get(requestId)
    }

    private fun putEntry(sessionKey: String, entry: Entry) {
        val list = bySession.getOrPut(sessionKey) { java.util.Collections.synchronizedList(ArrayList()) }
        synchronized(list) {
            list.add(entry)
            while (list.size > MAX_ENTRIES) {
                val removed = list.removeAt(0)
                byRequestId[sessionKey]?.remove(removed.requestId)
                deleteEntryBodies(sessionKey, removed)
            }
        }
        byRequestId.getOrPut(sessionKey) { ConcurrentHashMap() }[entry.requestId] = entry
        listeners[sessionKey]?.forEach { runCatching { it(entry) } }
        bumpDebounced()
    }

    private fun bumpDebounced() {
        if (synchronousIngestForTests) {
            revision.update { it + 1 }
            return
        }
        runCatching {
            mainHandler.removeCallbacks(bumpRunnable)
            mainHandler.postDelayed(bumpRunnable, REVISION_DEBOUNCE_MS)
        }.onFailure {
            revision.update { it + 1 }
        }
    }

    fun flushRevision() {
        runCatching {
            mainHandler.removeCallbacks(bumpRunnable)
        }
        revision.update { it + 1 }
    }

    private fun truncateHeaders(raw: String): String {
        if (raw.length <= MAX_HEADER_CHARS) return raw
        return raw.take(MAX_HEADER_CHARS) + "\n...(headers truncated)"
    }

    private fun baseDir(sessionKey: String): File? {
        val ctx = appContext ?: return null
        val safe = Base64.encodeToString(sessionKey.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP)
        return File(ctx.filesDir, "network-capture/$safe").apply { mkdirs() }
    }

    private fun saveBody(
        sessionKey: String,
        entry: Entry,
        prefix: String,
        text: String,
        append: Boolean,
    ): Pair<String, Int>? {
        val dir = baseDir(sessionKey) ?: return null
        val file = File(dir, "${entry.id}-$prefix.dat")
        val existingSize = if (append && file.isFile) file.length().toInt() else 0
        if (existingSize >= MAX_BODY_BYTES) return Pair(file.absolutePath, existingSize)
        val room = MAX_BODY_BYTES - existingSize
        val chunk = text.toByteArray(Charsets.UTF_8)
        val toAdd = if (chunk.size > room) chunk.copyOf(room) else chunk
        runCatching {
            FileOutputStream(file, append && file.isFile).use { out ->
                out.write(toAdd)
            }
        }
        return Pair(file.absolutePath, existingSize + toAdd.size)
    }

    private fun readBodyForMcp(path: String?, totalSize: Int): String {
        if (path.isNullOrBlank()) return "(empty)"
        val file = File(path)
        if (!file.isFile) return "(missing)"
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return "(read error)"
        val text = bytes.toString(Charsets.UTF_8)
        if (text.length <= MCP_BODY_TRUNCATE) return text
        val bytesTotal = totalSize.coerceAtLeast(bytes.size)
        return text.take(MCP_BODY_TRUNCATE) + "\n...(truncated for agent, $bytesTotal bytes total)"
    }

    fun readBodyFull(path: String?): String {
        if (path.isNullOrBlank()) return ""
        return runCatching { File(path).readText() }.getOrDefault("")
    }

    private fun deleteBodies(sessionKey: String) {
        baseDir(sessionKey)?.let { dir ->
            runCatching { dir.deleteRecursively() }
        }
    }

    private fun deleteEntryBodies(sessionKey: String, entry: Entry) {
        entry.requestBodyPath?.let { runCatching { File(it).delete() } }
        entry.responseBodyPath?.let { runCatching { File(it).delete() } }
    }

    /** WebView preview fallback when capture store empty. */
    fun textFromLegacyLines(lines: String): String = lines
}
