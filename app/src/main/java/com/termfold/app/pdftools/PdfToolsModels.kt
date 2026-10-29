package com.termfold.app.pdftools

enum class PdfToolsJobType {
    INSPECT,
    GENERATE,
}

enum class PdfToolsAuthzClass {
    OWN_TEMPLATE,
    OWN_DATA_DOC,
    THIRD_PARTY_ISSUED,
    UNKNOWN,
}

data class PdfToolsSession(
    val id: String,
    val name: String,
    val jobType: PdfToolsJobType,
    val authzClass: PdfToolsAuthzClass = PdfToolsAuthzClass.UNKNOWN,
    val inputSha256: String? = null,
    val outputSha256: String? = null,
    val approvedPlanId: String? = null,
    val lastReport: String? = null,
    val inspectJson: String? = null,
    val encrypted: Boolean = false,
    val tint: Int = 0,
    val acpAgentId: String = "cursor",
    val createdAtMs: Long = 0L,
    val updatedAtMs: Long = 0L,
)

data class PdfToolsData(
    val sessions: List<PdfToolsSession> = emptyList(),
) {
    fun session(id: String?): PdfToolsSession? = sessions.firstOrNull { it.id == id }

    fun upsert(session: PdfToolsSession): PdfToolsData {
        val index = sessions.indexOfFirst { it.id == session.id }
        if (index < 0) return copy(sessions = sessions + session)
        val next = sessions.toMutableList()
        next[index] = session
        return copy(sessions = next)
    }

    fun without(sessionId: String): PdfToolsData =
        copy(sessions = sessions.filterNot { it.id == sessionId })
}
