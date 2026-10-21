package com.termfold.app.acp.mcp

import android.content.Context
import org.json.JSONObject

/**
 * Per-server enable flags and secret overrides (env vars and HTTP headers).
 *
 * Disabled state is stored explicitly so newly discovered servers default to on.
 */
object McpPrefs {

    private const val PREFS = "mcp_servers"
    private const val KEY_DISABLED = "disabled"
    private const val KEY_FORCED_ON = "forced_on"
    private const val KEY_SECRETS = "secrets"

    fun isEnabled(context: Context, serverKey: String, configDefault: Boolean): Boolean {
        val disabled = prefs(context).getStringSet(KEY_DISABLED, emptySet()) ?: emptySet()
        val forcedOn = prefs(context).getStringSet(KEY_FORCED_ON, emptySet()) ?: emptySet()
        if (serverKey in disabled) return false
        if (serverKey in forcedOn) return true
        return configDefault
    }

    fun setEnabled(context: Context, serverKey: String, enabled: Boolean, configDefault: Boolean) {
        val disabled = (prefs(context).getStringSet(KEY_DISABLED, emptySet()) ?: emptySet()).toMutableSet()
        val forcedOn = (prefs(context).getStringSet(KEY_FORCED_ON, emptySet()) ?: emptySet()).toMutableSet()
        when {
            enabled == configDefault -> {
                disabled.remove(serverKey)
                forcedOn.remove(serverKey)
            }
            enabled -> {
                disabled.remove(serverKey)
                forcedOn.add(serverKey)
            }
            else -> {
                disabled.add(serverKey)
                forcedOn.remove(serverKey)
            }
        }
        prefs(context).edit()
            .putStringSet(KEY_DISABLED, disabled)
            .putStringSet(KEY_FORCED_ON, forcedOn)
            .apply()
    }

    fun secret(context: Context, serverKey: String, name: String): String? =
        secretsRoot(context).optJSONObject(serverKey)?.optString(name)?.takeIf { it.isNotEmpty() }

    fun setSecret(context: Context, serverKey: String, name: String, value: String) {
        val root = secretsRoot(context)
        val entry = root.optJSONObject(serverKey) ?: JSONObject()
        entry.put(name, value)
        root.put(serverKey, entry)
        prefs(context).edit().putString(KEY_SECRETS, root.toString()).apply()
    }

    fun secretsFor(context: Context, serverKey: String): Map<String, String> {
        val obj = secretsRoot(context).optJSONObject(serverKey) ?: return emptyMap()
        return buildMap {
            obj.keys().forEach { key ->
                val v = obj.optString(key)
                if (v.isNotEmpty()) put(key, v)
            }
        }
    }

    private fun secretsRoot(context: Context): JSONObject {
        val raw = prefs(context).getString(KEY_SECRETS, null) ?: return JSONObject()
        return runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
