package com.termfold.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.termfold.app.automation.AutomationData
import com.termfold.app.automation.AutomationPersistence
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AutomationStore(private val context: Context) {

    private val key = stringPreferencesKey("automation_json")

    val data: Flow<AutomationData> = context.termfoldDataStore.data.map { preferences ->
        decode(preferences[key])
    }

    suspend fun update(transform: (AutomationData) -> AutomationData) {
        context.termfoldDataStore.edit { preferences ->
            val next = transform(decode(preferences[key]))
            preferences[key] = encode(next)
        }
    }

    private fun decode(raw: String?): AutomationData = AutomationPersistence.decode(raw)

    private fun encode(data: AutomationData): String = AutomationPersistence.encode(data)
}
