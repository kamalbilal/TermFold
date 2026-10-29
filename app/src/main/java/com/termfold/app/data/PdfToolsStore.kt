package com.termfold.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.termfold.app.pdftools.PdfToolsData
import com.termfold.app.pdftools.PdfToolsPersistence
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PdfToolsStore(private val context: Context) {
    private val key = stringPreferencesKey("pdf_tools_json")

    val data: Flow<PdfToolsData> = context.termfoldDataStore.data.map { preferences ->
        PdfToolsPersistence.decode(preferences[key])
    }

    suspend fun update(transform: (PdfToolsData) -> PdfToolsData) {
        context.termfoldDataStore.edit { preferences ->
            val next = transform(PdfToolsPersistence.decode(preferences[key]))
            preferences[key] = PdfToolsPersistence.encode(next)
        }
    }
}
