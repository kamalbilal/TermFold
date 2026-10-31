package com.termfold.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.termfold.app.tasks.TaskPersistence
import com.termfold.app.tasks.TasksData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TasksStore(private val context: Context) {

    private val key = stringPreferencesKey("tasks_json")

    val data: Flow<TasksData> = context.termfoldDataStore.data.map { preferences ->
        TaskPersistence.decode(preferences[key])
    }

    suspend fun update(transform: (TasksData) -> TasksData) {
        context.termfoldDataStore.edit { preferences ->
            val next = transform(TaskPersistence.decode(preferences[key]))
            preferences[key] = TaskPersistence.encode(next)
        }
    }
}
