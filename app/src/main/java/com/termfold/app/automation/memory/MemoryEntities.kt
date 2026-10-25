package com.termfold.app.automation.memory

import io.objectbox.annotation.Entity
import io.objectbox.annotation.HnswIndex
import io.objectbox.annotation.Id

@Entity
data class MemoryChunkEntity(
    @Id var id: Long = 0,
    /** Empty string means global navigation memory. */
    var automationSessionId: String = "",
    var siteKey: String = "",
    var kind: String = "",
    var title: String = "",
    var searchText: String = "",
    var body: String = "",
    var successCount: Int = 0,
    var failCount: Int = 0,
    var createdAtMs: Long = 0L,
    var updatedAtMs: Long = 0L,
    @HnswIndex(dimensions = 384)
    var embedding: FloatArray? = null,
)

@Entity
data class SessionEventEntity(
    @Id var id: Long = 0,
    var automationSessionId: String = "",
    var type: String = "",
    var url: String = "",
    var summary: String = "",
    var outcome: String = "",
    var createdAtMs: Long = 0L,
)

@Entity
data class SessionSecretEntity(
    @Id var id: Long = 0,
    var automationSessionId: String = "",
    var name: String = "",
    var description: String = "",
    var encryptedPayload: ByteArray = byteArrayOf(),
    var searchText: String = "",
    var updatedAtMs: Long = 0L,
)

object EmbeddingDimensions {
    const val SIZE = 384
}
