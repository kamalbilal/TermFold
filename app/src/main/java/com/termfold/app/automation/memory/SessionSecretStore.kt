package com.termfold.app.automation.memory

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest

object SessionSecretStore {
    private fun prefs(context: Context) = EncryptedSharedPreferences.create(
        context,
        "automation_secrets",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private fun key(sessionKey: String, name: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$sessionKey:$name".toByteArray())
            .joinToString("") { "%02x".format(it) }
        return "s_$digest"
    }

    fun put(context: Context, sessionKey: String, name: String, description: String, payload: String) {
        prefs(context).edit()
            .putString(key(sessionKey, name), payload)
            .putString("${key(sessionKey, name)}_desc", description)
            .apply()
    }

    fun get(context: Context, sessionKey: String, name: String): String? =
        prefs(context).getString(key(sessionKey, name), null)
}
