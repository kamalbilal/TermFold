package com.termfold.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.termfold.app.startup.StartupCommandsData
import com.termfold.app.startup.StartupCommandsPersistence
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class StartupCommandsStore(private val context: Context) {

    private val key = stringPreferencesKey("startup_commands_json")

    val data: Flow<StartupCommandsData> = context.termfoldDataStore.data.map { preferences ->
        StartupCommandsPersistence.decode(preferences[key])
    }

    suspend fun update(transform: (StartupCommandsData) -> StartupCommandsData) {
        context.termfoldDataStore.edit { preferences ->
            val next = transform(StartupCommandsPersistence.decode(preferences[key]))
            preferences[key] = StartupCommandsPersistence.encode(next)
        }
    }
}
