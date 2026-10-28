package com.termfold.app.preview

import org.json.JSONObject
import org.json.JSONTokener

/** Parses JavaScript return values from WebView and Gecko extension eval bridges. */
internal object BrowserJsResults {
    fun parseTfJson(json: String): JSONObject {
        val trimmed = json.trim()
        if (trimmed.isEmpty() || trimmed == "null") return JSONObject().put("error", "empty_response")
        return runCatching { JSONObject(trimmed) }.getOrElse {
            when (trimmed) {
                "true" -> JSONObject().put("value", true)
                "false" -> JSONObject().put("value", false)
                else -> JSONObject().put("value", trimmed)
            }
        }
    }

    /** Parses the JSON object returned by a `window.__tf.*` helper call. */
    fun parseHelperCall(raw: String): JSONObject? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed == "null") return null
        runCatching {
            when (val token = JSONTokener(trimmed).nextValue()) {
                is JSONObject -> return token
                is String -> return runCatching { JSONObject(token) }.getOrNull()
            }
        }
        return runCatching { JSONObject(trimmed) }.getOrNull()
    }

    /** Reads a string returned from `evaluateJavascript` / extension eval. */
    fun parseEvalString(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed == "null") return ""
        runCatching {
            when (val token = JSONTokener(trimmed).nextValue()) {
                is String -> return token
                null, JSONObject.NULL -> return ""
                else -> return token.toString()
            }
        }
        return trimmed.removeSurrounding("\"")
    }
}
