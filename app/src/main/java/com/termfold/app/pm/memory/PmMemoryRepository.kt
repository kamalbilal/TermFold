package com.termfold.app.pm.memory

import android.content.Context
import com.termfold.app.automation.memory.EmbeddingService
import com.termfold.app.automation.memory.MemoryChunkEntity
import com.termfold.app.automation.memory.MemoryChunkEntity_
import com.termfold.app.automation.memory.MemoryChunkFilters
import io.objectbox.BoxStore
import io.objectbox.kotlin.boxFor

class PmMemoryRepository(
    context: Context,
    private val sessionId: String,
    private val embeddings: EmbeddingService = EmbeddingService.get(context),
) {
    private val store: BoxStore = PmMemoryStore.get(context, sessionId)

    private fun scopedId(): String = sessionId

    fun upsertChunk(
        kind: String,
        title: String,
        searchText: String,
        body: String,
        siteKey: String = "pm",
    ) {
        val sessionKey = scopedId()
        val vector = embeddings.embed(searchText)
        val chunkBox = store.boxFor<MemoryChunkEntity>()
        val existing = chunkBox.query(
            MemoryChunkEntity_.automationSessionId.equal(sessionKey)
                .and(MemoryChunkEntity_.siteKey.equal(siteKey))
                .and(MemoryChunkEntity_.kind.equal(kind))
                .and(MemoryChunkEntity_.title.equal(title)),
        ).build().findFirst()
        val now = System.currentTimeMillis()
        val entity = existing?.copy(
            searchText = searchText,
            body = body,
            embedding = vector,
            updatedAtMs = now,
            successCount = existing.successCount + 1,
        ) ?: MemoryChunkEntity(
            automationSessionId = sessionKey,
            siteKey = siteKey,
            kind = kind,
            title = title,
            searchText = searchText,
            body = body,
            embedding = vector,
            createdAtMs = now,
            updatedAtMs = now,
            successCount = 1,
        )
        chunkBox.put(entity)
    }

    fun search(query: String, limit: Int = 5): List<MemoryChunkEntity> {
        val sessionKey = scopedId()
        val vector = embeddings.embed(query)
        val chunkBox = store.boxFor<MemoryChunkEntity>()
        return runCatching {
            chunkBox.query(
                MemoryChunkEntity_.embedding.nearestNeighbors(vector, limit)
                    .and(MemoryChunkEntity_.automationSessionId.equal(sessionKey)),
            ).build().find()
        }.getOrElse {
            textFallback(chunkBox, sessionKey, query, limit)
        }
    }

    fun topChunks(limit: Int = 6): List<MemoryChunkEntity> =
        store.boxFor<MemoryChunkEntity>().query(
            MemoryChunkEntity_.automationSessionId.equal(scopedId()),
        )
            .orderDesc(MemoryChunkEntity_.updatedAtMs)
            .build()
            .find(0, limit.toLong())

    fun listMemoryChunks(
        kindFilter: String = "",
        textFilter: String = "",
    ): List<MemoryChunkEntity> =
        MemoryChunkFilters.filter(
            store.boxFor<MemoryChunkEntity>().all,
            global = false,
            sessionKey = scopedId(),
            siteKeyFilter = "",
            kindFilter = kindFilter,
            textFilter = textFilter,
        )

    fun getMemoryChunk(id: Long): MemoryChunkEntity? =
        store.boxFor<MemoryChunkEntity>().get(id)

    fun saveMemoryChunk(chunk: MemoryChunkEntity): Result<Unit> {
        if (chunk.automationSessionId != scopedId()) {
            return Result.failure(IllegalArgumentException("PM memory chunk session mismatch"))
        }
        val searchText = chunk.searchText.ifBlank { "${chunk.title} ${chunk.body}".trim() }
        val vector = embeddings.embed(searchText)
        val now = System.currentTimeMillis()
        val box = store.boxFor<MemoryChunkEntity>()
        val toPut = if (chunk.id == 0L) {
            chunk.copy(
                searchText = searchText,
                embedding = vector,
                createdAtMs = now,
                updatedAtMs = now,
            )
        } else {
            chunk.copy(
                searchText = searchText,
                embedding = vector,
                updatedAtMs = now,
            )
        }
        box.put(toPut)
        return Result.success(Unit)
    }

    fun deleteMemoryChunk(id: Long) {
        store.boxFor<MemoryChunkEntity>().remove(id)
    }

    private fun textFallback(
        chunkBox: io.objectbox.Box<MemoryChunkEntity>,
        sessionKey: String,
        query: String,
        limit: Int,
    ): List<MemoryChunkEntity> {
        val needle = query.lowercase()
        return chunkBox.all
            .asSequence()
            .filter { it.automationSessionId == sessionKey }
            .filter {
                it.searchText.lowercase().contains(needle) || it.title.lowercase().contains(needle)
            }
            .sortedByDescending { it.updatedAtMs }
            .take(limit)
            .toList()
    }
}
