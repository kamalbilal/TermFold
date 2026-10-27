package com.termfold.app.pm.memory

import android.content.Context
import com.termfold.app.automation.memory.MyObjectBox
import com.termfold.app.pm.PmPaths
import io.objectbox.BoxStore
import java.util.concurrent.ConcurrentHashMap

object PmMemoryStore {
    private val stores = ConcurrentHashMap<String, BoxStore>()

    fun get(context: Context, sessionId: String): BoxStore {
        return stores.getOrPut(sessionId) {
            MyObjectBox.builder()
                .androidContext(context.applicationContext)
                .name(PmPaths.memoryStoreName(sessionId))
                .build()
        }
    }

    fun close(sessionId: String) {
        stores.remove(sessionId)?.close()
    }

    fun deleteSession(context: Context, sessionId: String) {
        stores.remove(sessionId)?.let { store ->
            store.close()
            store.deleteAllFiles()
        }
    }
}
