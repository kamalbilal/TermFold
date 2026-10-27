package com.termfold.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.termfold.app.pm.PmData
import com.termfold.app.pm.PmPersistence
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PmSessionStore(private val context: Context) {
    private val key = stringPreferencesKey("pm_sessions_json")

    val data: Flow<PmData> = context.termfoldDataStore.data.map { preferences ->
        PmPersistence.decode(preferences[key])
    }

    suspend fun update(transform: (PmData) -> PmData) {
        context.termfoldDataStore.edit { preferences ->
            val next = transform(PmPersistence.decode(preferences[key]))
            preferences[key] = PmPersistence.encode(next)
        }
    }
}
