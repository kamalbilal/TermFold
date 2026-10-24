package com.termfold.app.acp

import com.termfold.app.acp.checkpoints.AcpCheckpointUiState
import com.termfold.app.acp.checkpoints.AcpRedoFrame
import org.json.JSONArray
import org.json.JSONObject

/** Chat rows plus durable undo/redo UI for one TermFold session tab. */
data class AcpPersistedTimeline(
    val items: List<AcpItem>,
    val checkpoints: AcpCheckpointUiState = AcpCheckpointUiState(),
    /** Injected on the next prompt after compress / wire resync. */
    val pendingWireContext: String? = null,
)

/** JSON for the TermFold-owned chat timeline, independent of ACP wire session ids. */
object AcpTimelineCodec {
    private const val ID_OFFSET = 1_000_000L

    fun nextCounter(maxItemId: Long): Long = (maxItemId - ID_OFFSET + 1).coerceAtLeast(1)

    fun durableCheckpoints(state: AcpCheckpointUiState): AcpCheckpointUiState =
        state.copy(wireResyncing = false, busy = null)

    fun encode(items: List<AcpItem>): String = encode(AcpPersistedTimeline(items))

    fun encode(snapshot: AcpPersistedTimeline): String {
        val durable = durableCheckpoints(snapshot.checkpoints)
        val obj = JSONObject()
            .put("items", encodeItems(snapshot.items))
            .put("checkpoints", encodeCheckpoints(durable))
        snapshot.pendingWireContext?.takeIf { it.isNotBlank() }?.let { obj.put("pendingWireContext", it) }
        return obj.toString()
    }

    fun decode(json: String): AcpPersistedTimeline {
        val root = JSONObject(json)
        return AcpPersistedTimeline(
            items = decodeItems(root.optJSONArray("items")),
            checkpoints = durableCheckpoints(decodeCheckpoints(root.optJSONObject("checkpoints"))),
            pendingWireContext = root.optString("pendingWireContext").takeIf { it.isNotBlank() },
        )
    }

    private fun encodeCheckpoints(state: AcpCheckpointUiState): JSONObject {
        val hashes = JSONObject()
        state.preTurnByMessageId.forEach { (id, hash) -> hashes.put(id.toString(), hash) }
        val undoable = JSONArray()
        state.undoableMessageIds.forEach { undoable.put(it) }
        val frames = JSONArray()
        state.redoStack.forEach { frames.put(encodeFrame(it)) }
        val obj = JSONObject()
            .put("preTurnByMessageId", hashes)
            .put("undoableMessageIds", undoable)
            .put("redoStack", frames)
        state.ghostFromItemId?.let { obj.put("ghostFromItemId", it) }
        state.compressedForUserMessageId?.let { obj.put("compressedForUserMessageId", it) }
        return obj
    }

    private fun decodeCheckpoints(obj: JSONObject?): AcpCheckpointUiState {
        if (obj == null) return AcpCheckpointUiState()
        val hashesObj = obj.optJSONObject("preTurnByMessageId")
        val hashes = mutableMapOf<Long, String>()
        if (hashesObj != null) {
            hashesObj.keys().forEach { key ->
                val id = key.toLongOrNull() ?: return@forEach
                val hash = hashesObj.optString(key)
                if (hash.isNotBlank()) hashes[id] = hash
            }
        }
        val undoableArr = obj.optJSONArray("undoableMessageIds")
        val undoable = mutableSetOf<Long>()
        if (undoableArr != null) {
            for (i in 0 until undoableArr.length()) undoable.add(undoableArr.optLong(i))
        }
        val framesArr = obj.optJSONArray("redoStack")
        val frames = buildList {
            if (framesArr != null) {
                for (i in 0 until framesArr.length()) {
                    val frame = framesArr.optJSONObject(i) ?: continue
                    add(decodeFrame(frame))
                }
            }
        }
        val ghost = if (obj.has("ghostFromItemId") && !obj.isNull("ghostFromItemId")) {
            obj.optLong("ghostFromItemId")
        } else {
            null
        }
        val compressed = if (obj.has("compressedForUserMessageId") && !obj.isNull("compressedForUserMessageId")) {
            obj.optLong("compressedForUserMessageId")
        } else {
            null
        }
        return AcpCheckpointUiState(
            preTurnByMessageId = hashes,
            undoableMessageIds = undoable,
            redoStack = frames,
            ghostFromItemId = ghost,
            compressedForUserMessageId = compressed,
        )
    }

    private fun encodeFrame(frame: AcpRedoFrame): JSONObject {
        val hashes = JSONObject()
        frame.preTurnByMessageId.forEach { (id, hash) -> hashes.put(id.toString(), hash) }
        val undoable = JSONArray()
        frame.undoableMessageIds.forEach { undoable.put(it) }
        val obj = JSONObject()
            .put("items", encodeItems(frame.items))
            .put("preTurnByMessageId", hashes)
            .put("undoableMessageIds", undoable)
        frame.ghostFromItemId?.let { obj.put("ghostFromItemId", it) }
        frame.worktreeTipCommit?.let { obj.put("worktreeTipCommit", it) }
        frame.wireSessionId?.let { obj.put("wireSessionId", it) }
        return obj
    }

    private fun decodeFrame(obj: JSONObject): AcpRedoFrame {
        val hashesObj = obj.optJSONObject("preTurnByMessageId")
        val hashes = mutableMapOf<Long, String>()
        if (hashesObj != null) {
            hashesObj.keys().forEach { key ->
                val id = key.toLongOrNull() ?: return@forEach
                val hash = hashesObj.optString(key)
                if (hash.isNotBlank()) hashes[id] = hash
            }
        }
        val undoableArr = obj.optJSONArray("undoableMessageIds")
        val undoable = mutableSetOf<Long>()
        if (undoableArr != null) {
            for (i in 0 until undoableArr.length()) undoable.add(undoableArr.optLong(i))
        }
        val ghost = if (obj.has("ghostFromItemId") && !obj.isNull("ghostFromItemId")) {
            obj.optLong("ghostFromItemId")
        } else {
            null
        }
        return AcpRedoFrame(
            items = decodeItems(obj.optJSONArray("items")),
            ghostFromItemId = ghost,
            preTurnByMessageId = hashes,
            undoableMessageIds = undoable,
            worktreeTipCommit = obj.optString("worktreeTipCommit").takeIf { it.isNotBlank() },
            wireSessionId = obj.optString("wireSessionId").takeIf { it.isNotBlank() },
        )
    }

    private fun encodeItems(items: List<AcpItem>): JSONArray {
        val array = JSONArray()
        items.forEach { array.put(encodeItem(it)) }
        return array
    }

    private fun decodeItems(array: JSONArray?): List<AcpItem> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                decodeItem(obj)?.let { add(it) }
            }
        }
    }

    private fun encodeItem(item: AcpItem): JSONObject = when (item) {
        is AcpItem.UserMessage -> JSONObject()
            .put("type", "user")
            .put("id", item.id)
            .put("text", item.text)
            .put("images", encodeImages(item.images))
            .put("files", encodeFiles(item.files))
        is AcpItem.AgentText -> JSONObject()
            .put("type", "agent")
            .put("id", item.id)
            .put("text", item.text)
        is AcpItem.Notice -> JSONObject()
            .put("type", "notice")
            .put("id", item.id)
            .put("text", item.text)
        is AcpItem.Thought -> JSONObject()
            .put("type", "thought")
            .put("id", item.id)
            .put("text", item.text)
        is AcpItem.ToolCall -> JSONObject()
            .put("type", "tool")
            .put("id", item.id)
            .put("toolCallId", item.toolCallId)
            .put("title", item.title)
            .put("kind", item.kind)
            .put("status", item.status)
            .put("detail", item.detail)
            .put("paths", JSONArray(item.paths))
            .put("fileDiffs", encodeFileDiffs(item.fileDiffs))
        is AcpItem.Plan -> JSONObject()
            .put("type", "plan")
            .put("id", item.id)
            .put(
                "entries",
                JSONArray().also { arr ->
                    item.entries.forEach { entry ->
                        arr.put(JSONObject().put("text", entry.text).put("status", entry.status))
                    }
                },
            )
    }

    private fun decodeItem(obj: JSONObject): AcpItem? {
        val id = obj.optLong("id")
        if (id == 0L) return null
        return when (obj.optString("type")) {
            "user" -> AcpItem.UserMessage(id, obj.optString("text"), decodeImages(obj.optJSONArray("images")), decodeFiles(obj.optJSONArray("files")))
            "agent" -> AcpItem.AgentText(id, obj.optString("text"))
            "notice" -> AcpItem.Notice(id, obj.optString("text"))
            "thought" -> AcpItem.Thought(id, obj.optString("text"))
            "tool" -> AcpItem.ToolCall(
                id = id,
                toolCallId = obj.optString("toolCallId"),
                title = obj.optString("title"),
                kind = obj.optString("kind"),
                status = obj.optString("status"),
                detail = obj.optString("detail"),
                paths = decodeStrings(obj.optJSONArray("paths")),
                fileDiffs = decodeFileDiffs(obj.optJSONArray("fileDiffs")),
            )
            "plan" -> {
                val entries = obj.optJSONArray("entries")
                AcpItem.Plan(
                    id,
                    buildList {
                        if (entries != null) {
                            for (i in 0 until entries.length()) {
                                val e = entries.optJSONObject(i) ?: continue
                                add(AcpItem.PlanEntry(e.optString("text"), e.optString("status")))
                            }
                        }
                    },
                )
            }
            else -> null
        }
    }

    private fun encodeImages(images: List<AcpBlock.Image>): JSONArray =
        JSONArray().also { arr ->
            images.forEach { arr.put(JSONObject().put("data", it.base64).put("mime", it.mimeType)) }
        }

    private fun encodeFiles(files: List<AcpBlock.TextFile>): JSONArray =
        JSONArray().also { arr ->
            files.forEach { arr.put(JSONObject().put("name", it.name).put("text", it.text)) }
        }

    private fun decodeImages(array: JSONArray?): List<AcpBlock.Image> = buildList {
        if (array == null) return@buildList
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            add(AcpBlock.Image(obj.optString("data"), obj.optString("mime").ifBlank { "image/jpeg" }))
        }
    }

    private fun decodeFiles(array: JSONArray?): List<AcpBlock.TextFile> = buildList {
        if (array == null) return@buildList
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            add(AcpBlock.TextFile(obj.optString("name"), obj.optString("text")))
        }
    }

    private fun decodeStrings(array: JSONArray?): List<String> = buildList {
        if (array == null) return@buildList
        for (i in 0 until array.length()) add(array.optString(i))
    }

    private fun encodeFileDiffs(diffs: List<AcpToolFileDiff>): JSONArray =
        JSONArray().also { arr ->
            diffs.forEach { diff ->
                arr.put(
                    JSONObject()
                        .put("path", diff.path)
                        .put("operation", diff.operation.name.lowercase())
                        .put("oldPath", diff.oldPath)
                        .put("patchText", diff.patchText),
                )
            }
        }

    private fun decodeFileDiffs(array: JSONArray?): List<AcpToolFileDiff> = buildList {
        if (array == null) return@buildList
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val path = obj.optString("path").takeIf { it.isNotBlank() } ?: continue
            val op = when (obj.optString("operation").lowercase()) {
                "add" -> AcpToolFileDiff.Operation.ADD
                "delete" -> AcpToolFileDiff.Operation.DELETE
                "modify" -> AcpToolFileDiff.Operation.MODIFY
                "move" -> AcpToolFileDiff.Operation.MOVE
                "copy" -> AcpToolFileDiff.Operation.COPY
                else -> AcpToolFileDiff.Operation.UNKNOWN
            }
            add(
                AcpToolFileDiff(
                    path = path,
                    operation = op,
                    oldPath = obj.optString("oldPath").takeIf { it.isNotBlank() },
                    patchText = obj.optString("patchText").takeIf { it.isNotBlank() },
                ),
            )
        }
    }
}
