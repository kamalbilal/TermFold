package com.termfold.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/** Single preferences DataStore for app metadata (folders, automation projects, etc.). */
internal val Context.termfoldDataStore: DataStore<Preferences> by preferencesDataStore(name = "termfold")
