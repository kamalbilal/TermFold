package com.termfold.app.automation.memory

import android.content.Context
import com.termfold.app.automation.memory.MemoryChunkEntity_
import com.termfold.app.automation.memory.SessionEventEntity_
import io.objectbox.BoxStore
import io.objectbox.kotlin.boxFor

class AutomationMemoryRepository(
    context: Context,
    private val embeddings: EmbeddingService = EmbeddingService.get(context),
) {
    private val store: BoxStore = AutomationMemoryStore.get(context)

    fun appendEvent(
        automationSessionId: String,
        type: String,
        url: String?,
        summary: String,
        outcome: String,
    ) {
        val box = store.boxFor<SessionEventEntity>()
        box.put(
            SessionEventEntity(
                automationSessionId = automationSessionId,
                type = type,
                url = url.orEmpty(),
                summary = summary,
                outcome = outcome,
                createdAtMs = System.currentTimeMillis(),
            ),
        )
    }

    fun recentEvents(automationSessionId: String, limit: Int = 8): List<SessionEventEntity> =
        store.boxFor<SessionEventEntity>().query(
            SessionEventEntity_.automationSessionId.equal(automationSessionId),
        )
            .orderDesc(SessionEventEntity_.createdAtMs)
            .build()
            .find(0, limit.toLong())

    fun upsertChunk(
        automationSessionId: String,
        siteKey: String,
        kind: String,
        title: String,
        searchText: String,
        body: String,
        global: Boolean = false,
    ) {
        val sessionId = if (global) "" else automationSessionId
        val vector = embeddings.embed(searchText)
        val chunkBox = store.boxFor<MemoryChunkEntity>()
        val existing = chunkBox.query(
            MemoryChunkEntity_.automationSessionId.equal(sessionId)
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
            automationSessionId = sessionId,
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

    fun searchSessionMemory(automationSessionId: String, query: String, limit: Int = 5): List<MemoryChunkEntity> {
        val vector = embeddings.embed(query)
        val chunkBox = store.boxFor<MemoryChunkEntity>()
        return runCatching {
            chunkBox.query(
                MemoryChunkEntity_.embedding.nearestNeighbors(vector, limit)
                    .and(MemoryChunkEntity_.automationSessionId.equal(automationSessionId)),
            ).build().find()
        }.getOrElse {
            textFallback(chunkBox, automationSessionId, query, limit, global = false)
        }
    }

    fun searchGlobalNav(siteKey: String, query: String, limit: Int = 5): List<MemoryChunkEntity> {
        val vector = embeddings.embed(query)
        val chunkBox = store.boxFor<MemoryChunkEntity>()
        return runCatching {
            chunkBox.query(
                MemoryChunkEntity_.embedding.nearestNeighbors(vector, limit)
                    .and(MemoryChunkEntity_.automationSessionId.equal(""))
                    .and(MemoryChunkEntity_.siteKey.equal(siteKey)),
            ).build().find()
        }.getOrElse {
            textFallback(chunkBox, "", query, limit, global = true, siteKey = siteKey)
        }
    }

    private fun textFallback(
        chunkBox: io.objectbox.Box<MemoryChunkEntity>,
        automationSessionId: String,
        query: String,
        limit: Int,
        global: Boolean,
        siteKey: String? = null,
    ): List<MemoryChunkEntity> {
        val needle = query.lowercase()
        return chunkBox.all
            .asSequence()
            .filter { chunk ->
                when {
                    global -> chunk.automationSessionId.isEmpty() &&
                        (siteKey.isNullOrBlank() || chunk.siteKey == siteKey)
                    else -> chunk.automationSessionId == automationSessionId
                }
            }
            .filter {
                it.searchText.lowercase().contains(needle) || it.title.lowercase().contains(needle)
            }
            .sortedByDescending { it.updatedAtMs }
            .take(limit)
            .toList()
    }

    fun listMemoryChunks(
        global: Boolean,
        sessionKey: String?,
        siteKeyFilter: String = "",
        kindFilter: String = "",
        textFilter: String = "",
    ): List<MemoryChunkEntity> =
        MemoryChunkFilters.filter(
            store.boxFor<MemoryChunkEntity>().all,
            global = global,
            sessionKey = sessionKey,
            siteKeyFilter = siteKeyFilter,
            kindFilter = kindFilter,
            textFilter = textFilter,
        )

    fun applySiteKindFilters(
        chunks: List<MemoryChunkEntity>,
        siteKeyFilter: String,
        kindFilter: String,
    ): List<MemoryChunkEntity> =
        chunks.filter { MemoryChunkFilters.matchesSiteKind(it, siteKeyFilter, kindFilter) }

    fun getMemoryChunk(id: Long): MemoryChunkEntity? =
        store.boxFor<MemoryChunkEntity>().get(id)

    fun saveMemoryChunk(chunk: MemoryChunkEntity): Result<Unit> {
        val global = chunk.automationSessionId.isEmpty()
        val blob = "${chunk.title} ${chunk.body} ${chunk.searchText}"
        if (global && looksLikeSecret(blob)) {
            return Result.failure(IllegalArgumentException("Global navigation memory cannot contain credentials"))
        }
        if (!global && looksLikeSecret(blob)) {
            return Result.failure(IllegalArgumentException("Avoid storing credentials in memory chunks; use session secrets"))
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

    fun listSessionEvents(
        automationSessionId: String,
        textFilter: String = "",
        limit: Int = 500,
    ): List<SessionEventEntity> {
        val needle = textFilter.trim().lowercase()
        return store.boxFor<SessionEventEntity>().query(
            SessionEventEntity_.automationSessionId.equal(automationSessionId),
        )
            .orderDesc(SessionEventEntity_.createdAtMs)
            .build()
            .find(0, limit.toLong())
            .filter { event ->
                if (needle.isEmpty()) return@filter true
                event.url.lowercase().contains(needle) ||
                    event.summary.lowercase().contains(needle) ||
                    event.type.lowercase().contains(needle) ||
                    event.outcome.lowercase().contains(needle)
            }
    }

    fun deleteSessionEvent(id: Long) {
        store.boxFor<SessionEventEntity>().remove(id)
    }

    fun writeSessionMemory(
        automationSessionId: String,
        kind: String,
        title: String,
        body: String,
        searchText: String,
        siteKey: String = "",
    ): Result<Unit> {
        val blob = "$title $body $searchText"
        if (looksLikeSecret(blob)) {
            return Result.failure(IllegalArgumentException("Avoid credentials in memory; use session secrets"))
        }
        upsertChunk(
            automationSessionId = automationSessionId,
            siteKey = siteKey,
            kind = kind,
            title = title,
            searchText = searchText,
            body = body,
            global = false,
        )
        return Result.success(Unit)
    }

    fun writeGlobalNav(siteKey: String, kind: String, title: String, body: String, searchText: String): Result<Unit> {
        if (Companion.looksLikeSecret("$title $body $searchText")) {
            return Result.failure(IllegalArgumentException("Global navigation memory cannot contain credentials"))
        }
        upsertChunk(
            automationSessionId = "",
            siteKey = siteKey,
            kind = kind,
            title = title,
            searchText = searchText,
            body = body,
            global = true,
        )
        return Result.success(Unit)
    }

    companion object {
        fun looksLikeSecret(text: String): Boolean {
            val t = text.lowercase()
            return listOf("password=", "api_key", "bearer ", "secret=", "token=").any { t.contains(it) }
        }
    }
}
