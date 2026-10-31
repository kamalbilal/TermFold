package com.termfold.app.acp

import org.json.JSONArray
import org.json.JSONObject

/** Cursor synthetic option id for freeform "Other…" answers. */
const val CURSOR_ASK_FREEFORM_OPTION_ID = "__freeform_other__"

const val CURSOR_ASK_SKIPPED_REASON = "Questions skipped by user"

data class AskQuestionOption(val id: String, val label: String)

data class AskQuestionSpec(
    val id: String,
    val prompt: String,
    val options: List<AskQuestionOption>,
    val allowMultiple: Boolean,
)

enum class AskQuestionPhase {
    Pending,
    Answered,
    Skipped,
    Cancelled,
}

data class AskQuestionAnswer(
    val questionId: String,
    val selectedOptionIds: List<String>,
    /** User text when [CURSOR_ASK_FREEFORM_OPTION_ID] is selected. */
    val freeformText: String? = null,
    val skipped: Boolean = false,
)

data class AskQuestionTurnSnapshot(
    val toolCallId: String,
    val title: String?,
    val questions: List<AskQuestionSpec>,
    val phase: AskQuestionPhase,
    val answers: List<AskQuestionAnswer>? = null,
)

data class AcpPendingAskQuestion(
    /** Set for ACP `cursor/ask_question`; null for MCP `ask_user`. */
    val requestId: Any?,
    val toolCallId: String,
    val title: String?,
    val questions: List<AskQuestionSpec>,
    val userMessageId: Long,
)

object AcpAskQuestionParser {

    fun parseRequest(params: JSONObject?, allowGeneratedToolCallId: Boolean = false): ParsedAskRequest? {
        if (params == null) return null
        val toolCallId = params.optString("toolCallId").ifBlank { null }
            ?: if (allowGeneratedToolCallId) {
                "ask_${System.currentTimeMillis()}"
            } else {
                null
            }
            ?: return null
        val title = params.optString("title").ifBlank { null }
        val questionsArr = params.optJSONArray("questions") ?: return null
        val questions = buildList {
            for (i in 0 until questionsArr.length()) {
                parseQuestion(questionsArr.optJSONObject(i))?.let { add(it) }
            }
        }
        if (questions.isEmpty()) return null
        return ParsedAskRequest(toolCallId, title, questions)
    }

    private fun parseQuestion(obj: JSONObject?): AskQuestionSpec? {
        if (obj == null) return null
        val id = obj.optString("id").ifBlank { null } ?: return null
        val prompt = obj.optString("prompt").ifBlank { null } ?: return null
        val optionsArr = obj.optJSONArray("options") ?: return null
        val options = buildList {
            for (j in 0 until optionsArr.length()) {
                val opt = optionsArr.optJSONObject(j) ?: continue
                val optId = opt.optString("id").ifBlank { null } ?: continue
                val label = opt.optString("label").ifBlank { optId }
                add(AskQuestionOption(optId, label))
            }
        }
        if (options.isEmpty()) return null
        val allowMultiple = when {
            obj.has("allowMultiple") -> obj.optBoolean("allowMultiple", false)
            obj.has("allow_multiple") -> obj.optBoolean("allow_multiple", false)
            else -> false
        }
        return AskQuestionSpec(id, prompt, options, allowMultiple)
    }

    fun buildAnsweredResult(answers: List<AskQuestionAnswer>): JSONObject {
        val answersArr = JSONArray()
        answers.forEach { answer ->
            val entry = JSONObject()
                .put("questionId", answer.questionId)
                .put("selectedOptionIds", JSONArray(answer.selectedOptionIds))
            answer.freeformText?.takeIf { it.isNotBlank() }?.let { entry.put("freeformText", it) }
            if (answer.skipped) entry.put("skipped", true)
            answersArr.put(entry)
        }
        return JSONObject()
            .put("outcome", JSONObject().put("outcome", "answered").put("answers", answersArr))
    }

    fun buildSkippedResult(reason: String = CURSOR_ASK_SKIPPED_REASON): JSONObject =
        JSONObject().put("outcome", JSONObject().put("outcome", "skipped").put("reason", reason))

    fun buildCancelledResult(): JSONObject =
        JSONObject().put("outcome", JSONObject().put("outcome", "cancelled"))

    data class ParsedAskRequest(
        val toolCallId: String,
        val title: String?,
        val questions: List<AskQuestionSpec>,
    )
}

/** All ask cards anchored to a user message id (oldest first). */
typealias AskQuestionsByUserMessageId = Map<Long, List<AskQuestionTurnSnapshot>>

fun appendAskSnapshotForTurn(
    map: AskQuestionsByUserMessageId,
    userMessageId: Long,
    snapshot: AskQuestionTurnSnapshot,
): AskQuestionsByUserMessageId {
    val next = map[userMessageId].orEmpty() + snapshot
    return map + (userMessageId to next)
}

fun replaceAskSnapshotForTurn(
    map: AskQuestionsByUserMessageId,
    userMessageId: Long,
    toolCallId: String,
    snapshot: AskQuestionTurnSnapshot,
): AskQuestionsByUserMessageId {
    val list = map[userMessageId].orEmpty().map { existing ->
        if (existing.toolCallId == toolCallId) snapshot else existing
    }
    return map + (userMessageId to list)
}

object AskQuestionSnapshotCodec {

    fun encodeMap(map: AskQuestionsByUserMessageId): JSONObject {
        val root = JSONObject()
        map.forEach { (userMessageId, snapshots) ->
            val terminal = snapshots.filter { it.phase != AskQuestionPhase.Pending }
            if (terminal.isEmpty()) return@forEach
            val arr = JSONArray()
            terminal.forEach { arr.put(encodeSnapshot(it)) }
            root.put(userMessageId.toString(), arr)
        }
        return root
    }

    fun decodeMap(obj: JSONObject?): AskQuestionsByUserMessageId {
        if (obj == null) return emptyMap()
        return buildMap {
            obj.keys().forEach { key ->
                val userId = key.toLongOrNull() ?: return@forEach
                val arr = obj.optJSONArray(key)
                val snapshots = when {
                    arr != null -> decodeSnapshotList(arr)
                    else -> decodeSnapshot(obj.optJSONObject(key))?.let { listOf(it) }.orEmpty()
                }
                if (snapshots.isNotEmpty()) put(userId, snapshots)
            }
        }
    }

    private fun decodeSnapshotList(arr: JSONArray): List<AskQuestionTurnSnapshot> = buildList {
        for (i in 0 until arr.length()) {
            decodeSnapshot(arr.optJSONObject(i))?.let { add(it) }
        }
    }

    /** Legacy single-snapshot encoding (tests). */
    fun encodeSingleTurnMap(map: Map<Long, AskQuestionTurnSnapshot>): JSONObject =
        encodeMap(map.mapValues { (_, s) -> listOf(s) })

    private fun encodeSnapshot(snapshot: AskQuestionTurnSnapshot): JSONObject {
        val questions = JSONArray()
        snapshot.questions.forEach { q ->
            val options = JSONArray()
            q.options.forEach { opt ->
                options.put(JSONObject().put("id", opt.id).put("label", opt.label))
            }
            questions.put(
                JSONObject()
                    .put("id", q.id)
                    .put("prompt", q.prompt)
                    .put("allowMultiple", q.allowMultiple)
                    .put("options", options),
            )
        }
        val obj = JSONObject()
            .put("toolCallId", snapshot.toolCallId)
            .put("phase", snapshot.phase.name)
            .put("questions", questions)
        snapshot.title?.let { obj.put("title", it) }
        snapshot.answers?.let { answers ->
            val arr = JSONArray()
            answers.forEach { a ->
                val entry = JSONObject()
                    .put("questionId", a.questionId)
                    .put("selectedOptionIds", JSONArray(a.selectedOptionIds))
                a.freeformText?.takeIf { it.isNotBlank() }?.let { entry.put("freeformText", it) }
                if (a.skipped) entry.put("skipped", true)
                arr.put(entry)
            }
            obj.put("answers", arr)
        }
        return obj
    }

    private fun decodeSnapshot(obj: JSONObject?): AskQuestionTurnSnapshot? {
        if (obj == null) return null
        val toolCallId = obj.optString("toolCallId").ifBlank { return null }
        val phase = runCatching { AskQuestionPhase.valueOf(obj.optString("phase")) }.getOrNull() ?: return null
        if (phase == AskQuestionPhase.Pending) return null
        val title = obj.optString("title").ifBlank { null }
        val questionsArr = obj.optJSONArray("questions") ?: return null
        val questions = buildList {
            for (i in 0 until questionsArr.length()) {
                val qObj = questionsArr.optJSONObject(i) ?: continue
                val id = qObj.optString("id").ifBlank { continue }
                val prompt = qObj.optString("prompt").ifBlank { continue }
                val optionsArr = qObj.optJSONArray("options") ?: continue
                val options = buildList {
                    for (j in 0 until optionsArr.length()) {
                        val o = optionsArr.optJSONObject(j) ?: continue
                        val oid = o.optString("id").ifBlank { continue }
                        add(AskQuestionOption(oid, o.optString("label").ifBlank { oid }))
                    }
                }
                if (options.isEmpty()) continue
                add(
                    AskQuestionSpec(
                        id = id,
                        prompt = prompt,
                        options = options,
                allowMultiple = when {
                    qObj.has("allowMultiple") -> qObj.optBoolean("allowMultiple", false)
                    qObj.has("allow_multiple") -> qObj.optBoolean("allow_multiple", false)
                    else -> false
                },
                    ),
                )
            }
        }
        if (questions.isEmpty()) return null
        val answersArr = obj.optJSONArray("answers")
        val answers = if (answersArr != null) {
            buildList {
                for (i in 0 until answersArr.length()) {
                    val a = answersArr.optJSONObject(i) ?: continue
                    val qid = a.optString("questionId").ifBlank { continue }
                    val skipped = a.optBoolean("skipped", false)
                    val idsArr = a.optJSONArray("selectedOptionIds")
                    val ids = if (idsArr == null) {
                        emptyList()
                    } else {
                        buildList {
                            for (j in 0 until idsArr.length()) {
                                idsArr.optString(j).takeIf { it.isNotBlank() }?.let { add(it) }
                            }
                        }
                    }
                    if (!skipped && ids.isEmpty()) continue
                    add(
                        AskQuestionAnswer(
                            questionId = qid,
                            selectedOptionIds = ids,
                            freeformText = a.optString("freeformText").ifBlank { null },
                            skipped = a.optBoolean("skipped", false),
                        ),
                    )
                }
            }.takeIf { it.isNotEmpty() }
        } else {
            null
        }
        return AskQuestionTurnSnapshot(
            toolCallId = toolCallId,
            title = title,
            questions = questions,
            phase = phase,
            answers = answers,
        )
    }
}

fun askQuestionsPrunedToTimeline(
    items: List<AcpItem>,
    map: AskQuestionsByUserMessageId,
): AskQuestionsByUserMessageId {
    val live = items.filterIsInstance<AcpItem.UserMessage>().mapTo(HashSet()) { it.id }
    return map.filterKeys { it in live }
}

/** Pending snapshots without a live RPC are stale (restart, crash); show as cancelled in UI. */
fun askQuestionsScrubStalePending(
    map: AskQuestionsByUserMessageId,
    pending: AcpPendingAskQuestion?,
): AskQuestionsByUserMessageId {
    if (map.isEmpty()) return map
    var changed = false
    val out = map.mapValues { (userMessageId, snapshots) ->
        snapshots.map { snapshot ->
            if (snapshot.phase == AskQuestionPhase.Pending &&
                (pending == null ||
                    pending.userMessageId != userMessageId ||
                    pending.toolCallId != snapshot.toolCallId)
            ) {
                changed = true
                snapshot.copy(phase = AskQuestionPhase.Cancelled, answers = null)
            } else {
                snapshot
            }
        }
    }
    return if (changed) out else map
}

fun askQuestionAnswersValid(
    questions: List<AskQuestionSpec>,
    answers: List<AskQuestionAnswer>,
): Boolean {
    if (answers.size != questions.size) return false
    val byId = answers.associateBy { it.questionId }
    return questions.all { q ->
        val answer = byId[q.id] ?: return false
        if (answer.skipped) return@all answer.selectedOptionIds.isEmpty()
        val ids = answer.selectedOptionIds
        when {
            q.allowMultiple -> ids.isNotEmpty() &&
                ids.all { id ->
                    id != CURSOR_ASK_FREEFORM_OPTION_ID ||
                        !answer.freeformText.isNullOrBlank()
                }
            ids.singleOrNull() == CURSOR_ASK_FREEFORM_OPTION_ID ->
                !answer.freeformText.isNullOrBlank()
            else -> ids.size == 1
        }
    }
}
