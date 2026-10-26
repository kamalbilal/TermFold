package com.termfold.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.termfold.app.prreview.PrReviewData
import com.termfold.app.prreview.PrReviewPersistence
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PrReviewStore(private val context: Context) {
    private val key = stringPreferencesKey("pr_review_json")

    val data: Flow<PrReviewData> = context.termfoldDataStore.data.map { preferences ->
        PrReviewPersistence.decode(preferences[key])
    }

    suspend fun update(transform: (PrReviewData) -> PrReviewData) {
        context.termfoldDataStore.edit { preferences ->
            val next = transform(PrReviewPersistence.decode(preferences[key]))
            preferences[key] = PrReviewPersistence.encode(next)
        }
    }
}
