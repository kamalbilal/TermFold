package com.termfold.app.pdftools

import android.content.Context
import com.termfold.app.shell.Projects
import java.io.File

object PdfToolsPaths {
    const val GUEST_ROOT = "/root/.termfold/pdf-tools"
    const val FONTS_GUEST = "/usr/local/share/fonts/termfold"
    const val CLI_GUEST = "/usr/local/bin/pdfsys"

    const val MAX_IMPORT_BYTES = 50L * 1024L * 1024L
    const val MAX_IMPORT_PAGES = 100

    fun sessionGuest(sessionId: String): String =
        "${GUEST_ROOT.trimEnd('/')}/$sessionId"

    fun hostSessionDir(context: Context, sessionId: String): File =
        Projects.hostPath(context, sessionGuest(sessionId))

    fun ensureStub(context: Context, sessionId: String): File {
        val dir = hostSessionDir(context, sessionId)
        listOf("input", "work", "renders", "out").forEach { File(dir, it).mkdirs() }
        PdfToolsScaffold.writeSessionTemplates(context, dir)
        return dir
    }

    fun qaReportFile(sessionDir: File): File = File(workDir(sessionDir), "qa/report.json")
    fun renderManifestFile(sessionDir: File): File = File(workDir(sessionDir), "renders/manifest.json")
    fun visualReviewFile(sessionDir: File): File = File(workDir(sessionDir), "visual_review.json")

    fun inputDir(sessionDir: File): File = File(sessionDir, "input")
    fun workDir(sessionDir: File): File = File(sessionDir, "work")
    fun rendersDir(sessionDir: File): File = File(sessionDir, "renders")
    fun outDir(sessionDir: File): File = File(sessionDir, "out")
    fun inspectFile(sessionDir: File): File = File(sessionDir, "inspect.json")
    fun fieldsFile(sessionDir: File): File = File(sessionDir, "fields.json")
    fun planFile(sessionDir: File): File = File(sessionDir, "plan.json")
    fun reportFile(sessionDir: File): File = File(sessionDir, "report.md")
    fun modelFile(sessionDir: File): File = File(workDir(sessionDir), "model.json")
    fun outputPdf(sessionDir: File): File {
        val canonical = File(outDir(sessionDir), "output.pdf")
        if (canonical.isFile) return canonical
        return File(outDir(sessionDir), "letter.pdf")
    }

    fun deleteSessionDir(dir: File): Boolean = dir.deleteRecursively()
}

object PdfToolsKeys {
    const val PREFIX = "pdf-tools"

    fun acpSessionKey(sessionId: String): String = "$PREFIX/$sessionId"
}
