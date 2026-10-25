package com.termfold.app.automation

import android.content.Context
import java.io.File

object AutomationStorage {

    private val docTemplates = mapOf(
        "prd.md" to "# Product requirements\n\nDescribe what this automation validates.\n",
        "requirements.md" to "# Requirements\n\nList functional requirements the agent must satisfy.\n",
        "constraints.md" to """
            # Constraints

            What the agent must never do during automation.

            TermFold enforces the block below at tool-permission time (browser navigation, edits).

            <!-- termfold-guardrails
            legal_sources_only: true
            protect_legal_policy: true
            -->
        """.trimIndent() + "\n",
    )

    fun ensureSessionTree(context: Context, projectId: String, sessionId: String) {
        val root = AutomationPaths.hostSessionDir(context, projectId, sessionId)
        val docs = File(root, "docs")
        val secrets = File(root, "secrets")
        val runs = File(root, "runs")
        val profile = File(root, "profile")
        listOf(docs, secrets, runs, profile).forEach { it.mkdirs() }
        docTemplates.forEach { (name, body) ->
            val file = File(docs, name)
            if (!file.exists()) file.writeText(body)
        }
        AutomationGuardrails.ensureGuardrailBlockInConstraints(File(docs, "constraints.md"))
    }

    fun readMandatoryDocs(context: Context, projectId: String, sessionId: String): String {
        val docs = File(AutomationPaths.hostSessionDir(context, projectId, sessionId), "docs")
        if (!docs.isDirectory) return ""
        return docTemplates.keys.mapNotNull { name ->
            val file = File(docs, name)
            if (!file.isFile) return@mapNotNull null
            "### $name\n${file.readText().trim()}"
        }.joinToString("\n\n").trim()
    }
}
