package com.termfold.app.automation.memory

import android.content.Context
import io.objectbox.BoxStore

object AutomationMemoryStore {
    @Volatile
    private var boxStore: BoxStore? = null

    fun get(context: Context): BoxStore {
        val existing = boxStore
        if (existing != null) return existing
        return synchronized(this) {
            boxStore ?: MyObjectBox.builder()
                .androidContext(context.applicationContext)
                .name("automation_memory")
                .build()
                .also { boxStore = it }
        }
    }
}
