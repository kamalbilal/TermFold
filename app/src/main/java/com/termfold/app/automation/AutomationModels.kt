package com.termfold.app.automation

data class AutomationSession(
    val id: String,
    val name: String,
    val tint: Int = 0,
    /** ACP agent id (default Cursor). */
    val acpAgentId: String = "cursor",
)

data class AutomationProject(
    val id: String,
    val name: String,
    val tint: Int = 0,
    val linkedFolderId: String? = null,
    val sessions: List<AutomationSession> = emptyList(),
)

data class AutomationData(
    val projects: List<AutomationProject> = emptyList(),
) {
    fun project(id: String?): AutomationProject? = projects.firstOrNull { it.id == id }

    fun session(projectId: String, sessionId: String): Pair<AutomationProject, AutomationSession>? {
        val project = project(projectId) ?: return null
        val session = project.sessions.firstOrNull { it.id == sessionId } ?: return null
        return project to session
    }

    fun linkedFolderExists(folderId: String?, codingFolderIds: Set<String>): Boolean =
        folderId == null || folderId in codingFolderIds
}
