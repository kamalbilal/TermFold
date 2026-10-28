package com.termfold.app.prreview

import android.os.Handler
import android.os.Looper
import org.json.JSONObject

object PrReviewReportBridge {
    const val SUBMIT_ACTION = "pr_review_submit_report"
    const val MCP_TOOL_NAME = "submit_pr_review_report"

    private val mainHandler: Handler by lazy { Handler(Looper.getMainLooper()) }

    /** Invoked on the main thread after a validated submit (session id, user message id, parse). */
    @Volatile
    var onSubmit: ((sessionId: String, userMessageId: Long, parse: PrReviewParse.Ok) -> Unit)? = null
        private set

    private var onSubmitOwner: Any? = null

    fun setOnSubmit(owner: Any, listener: (sessionId: String, userMessageId: Long, parse: PrReviewParse.Ok) -> Unit) {
        onSubmitOwner = owner
        onSubmit = listener
    }

    fun clearOnSubmit(owner: Any) {
        if (onSubmitOwner === owner) {
            onSubmitOwner = null
            onSubmit = null
        }
    }

    internal fun clearOnSubmitForTests() {
        onSubmitOwner = null
        onSubmit = null
    }

    fun handle(action: String, args: JSONObject): JSONObject {
        if (action != SUBMIT_ACTION) return err("Unknown PR review action")
        val sessionId = PrReviewSessionContext.activeSessionId
            ?: return err("No active PR Review session")
        val givenId = args.optString("session_id")
        if (givenId.isNotBlank() && givenId != sessionId) {
            return err("PR Review session mismatch")
        }
        val userMessageId = when {
            args.has("user_message_id") && !args.isNull("user_message_id") ->
                args.optLong("user_message_id")
            else -> PrReviewSessionContext.activeUserMessageId ?: return err("No active review turn")
        }
        if (userMessageId <= 0L) return err("Invalid user_message_id")
        val raw = reportPayload(args) ?: return err("report object required")
        val parse = PrReviewReportParser.parseJson(raw)
        return when (parse) {
            is PrReviewParse.Ok -> {
                val listener = onSubmit
                    ?: return err("No active PR Review UI listener")
                deliverSubmit { listener(sessionId, userMessageId, parse) }
                JSONObject().put("ok", true)
            }
            is PrReviewParse.Invalid -> err(parse.reason)
            else -> err("Report JSON is missing or invalid.")
        }
    }

    internal fun reportPayload(args: JSONObject): String? {
        args.optJSONObject("report")?.let { return it.toString() }
        args.optString("report_json").takeIf { it.isNotBlank() }?.let { return it }
        return null
    }

    private fun err(message: String) = JSONObject().put("error", message)

    private fun deliverSubmit(block: () -> Unit) {
        val posted = runCatching {
            val main = Looper.getMainLooper()
            if (Looper.myLooper() == main) {
                block()
            } else {
                mainHandler.post(block)
            }
        }
        if (posted.isFailure) {
            block()
        }
    }
}
