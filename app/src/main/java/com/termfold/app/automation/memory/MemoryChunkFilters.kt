package com.termfold.app.automation.memory

internal object MemoryChunkFilters {
    fun filter(
        chunks: Iterable<MemoryChunkEntity>,
        global: Boolean,
        sessionKey: String?,
        siteKeyFilter: String = "",
        kindFilter: String = "",
        textFilter: String = "",
    ): List<MemoryChunkEntity> {
        val site = siteKeyFilter.trim().lowercase()
        val kind = kindFilter.trim().lowercase()
        val text = textFilter.trim().lowercase()
        return chunks
            .asSequence()
            .filter { chunk ->
                val inScope = if (global) {
                    chunk.automationSessionId.isEmpty()
                } else {
                    chunk.automationSessionId == sessionKey
                }
                inScope &&
                    matchesSiteKind(chunk, site, kind) &&
                    (
                        text.isEmpty() ||
                            chunk.title.lowercase().contains(text) ||
                            chunk.body.lowercase().contains(text) ||
                            chunk.searchText.lowercase().contains(text) ||
                            chunk.siteKey.lowercase().contains(text)
                    )
            }
            .sortedByDescending { it.updatedAtMs }
            .toList()
    }

    fun matchesSiteKind(
        chunk: MemoryChunkEntity,
        siteKeyFilter: String,
        kindFilter: String,
    ): Boolean {
        val site = siteKeyFilter.trim().lowercase()
        val kind = kindFilter.trim().lowercase()
        return (site.isEmpty() || chunk.siteKey.lowercase().contains(site)) &&
            (kind.isEmpty() || chunk.kind.lowercase().contains(kind))
    }
}
