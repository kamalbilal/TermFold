package com.termfold.app.pm

import android.content.Context
import java.io.File

object PmBriefFiles {
    fun sessionDir(context: Context, sessionId: String): File =
        PmPaths.hostSessionDir(context, sessionId).resolve("brief")

    fun visionFile(context: Context, sessionId: String): File =
        sessionDir(context, sessionId).resolve("vision.md")

    fun constraintsFile(context: Context, sessionId: String): File =
        sessionDir(context, sessionId).resolve("constraints.md")

    fun releaseBriefFile(context: Context, sessionId: String): File =
        sessionDir(context, sessionId).resolve("release.md")

    fun competitorsFile(context: Context, sessionId: String): File =
        sessionDir(context, sessionId).resolve("competitors.md")

    fun ensureDefaults(context: Context, session: PmSession) {
        val dir = sessionDir(context, session.id)
        dir.mkdirs()
        if (!visionFile(context, session.id).isFile) {
            visionFile(context, session.id).writeText(
                "# Product vision\n\n${session.productOneLiner}\n",
            )
        }
        if (!constraintsFile(context, session.id).isFile) {
            constraintsFile(context, session.id).writeText("# Constraints\n\n")
        }
        if (session.mode == PmMode.RELEASE && !releaseBriefFile(context, session.id).isFile) {
            val label = session.releaseLabel.ifBlank { session.branch }
            releaseBriefFile(context, session.id).writeText("# Release planning\n\nTarget: $label\n")
        }
        syncCompetitorsMarkdown(context, session)
    }

    fun syncCompetitorsMarkdown(context: Context, session: PmSession) {
        val lines = buildString {
            appendLine("# Competitors")
            appendLine()
            if (session.competitors.isEmpty()) {
                appendLine("_No competitors listed._")
            } else {
                session.competitors.forEach { c ->
                    appendLine("## ${c.name}")
                    if (c.homepageUrl.isNotBlank()) appendLine("- Homepage: ${c.homepageUrl}")
                    c.extraUrls.forEach { appendLine("- URL: $it") }
                    appendLine()
                }
            }
        }
        competitorsFile(context, session.id).parentFile?.mkdirs()
        competitorsFile(context, session.id).writeText(lines)
    }

    fun readBriefBundle(context: Context, sessionId: String, mode: PmMode): String = buildString {
        fun appendFile(label: String, file: File) {
            if (!file.isFile) return
            val body = file.readText().trim()
            if (body.isBlank()) return
            appendLine("## $label")
            appendLine(body)
            appendLine()
        }
        appendFile("Vision", visionFile(context, sessionId))
        appendFile("Constraints", constraintsFile(context, sessionId))
        appendFile("Competitors", competitorsFile(context, sessionId))
        if (mode == PmMode.RELEASE) {
            appendFile("Release brief", releaseBriefFile(context, sessionId))
        }
    }.trim()
}
