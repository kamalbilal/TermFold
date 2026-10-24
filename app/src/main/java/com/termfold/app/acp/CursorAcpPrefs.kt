package com.termfold.app.acp

import android.content.Context

/** User preferences for the Cursor ACP agent in TermFold. */
object CursorAcpPrefs {
    private const val PREFS = "cursor_acp"
    private const val SECRETS_PREFS = "cursor_acp_secrets"
    private const val KEY_ALLOW_EVERYTHING = "allow_everything"
    private const val KEY_API_KEY = "api_key"

    /** User API key from Cursor Dashboard → API Keys; passed as `CURSOR_API_KEY` to the agent CLI. */
    fun apiKey(context: Context): String? =
        context.getSharedPreferences(SECRETS_PREFS, Context.MODE_PRIVATE)
            .getString(KEY_API_KEY, null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    fun setApiKey(context: Context, value: String) {
        val trimmed = value.trim()
        context.getSharedPreferences(SECRETS_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_API_KEY, trimmed.ifEmpty { null })
            .apply()
    }

    fun hasApiKey(context: Context): Boolean = apiKey(context) != null

    fun agentEnvironment(context: Context): Map<String, String> = apiKeyEnvironment(apiKey(context))

    internal fun apiKeyEnvironment(apiKey: String?): Map<String, String> {
        val key = apiKey?.trim()?.takeIf { it.isNotEmpty() } ?: return emptyMap()
        return mapOf("CURSOR_API_KEY" to key)
    }

    fun allowEverything(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ALLOW_EVERYTHING, true)

    fun setAllowEverything(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ALLOW_EVERYTHING, enabled)
            .apply()
    }

    /** Picks the most permissive option Cursor offered for `session/request_permission`. */
    fun preferredAllowOptionId(options: List<AcpPermissionOption>): String? {
        if (options.isEmpty()) return null
        val ids = options.associateBy { it.id }
        val kinds = options.associateBy { it.kind }
        ids["allow-always"]?.let { return it.id }
        ids["allow_always"]?.let { return it.id }
        kinds["allow_always"]?.let { return it.id }
        ids["allow-once"]?.let { return it.id }
        ids["allow_once"]?.let { return it.id }
        kinds["allow_once"]?.let { return it.id }
        return options.firstOrNull { option ->
            val kind = option.kind.lowercase()
            val id = option.id.lowercase()
            !kind.contains("reject") && !kind.contains("deny") &&
                !id.contains("reject") && !id.contains("deny")
        }?.id ?: options.first().id
    }

    fun extraLaunchArgs(allowEverything: Boolean): List<String> =
        if (allowEverything) listOf("--force") else emptyList()
}
