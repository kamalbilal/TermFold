package com.termfold.app.pdftools

import android.content.Context
import java.io.File

object PdfToolsScaffold {
    private val WORK_SUBDIRS = listOf(
        "golden",
        "candidate",
        "diff",
        "assets",
        "crops",
        "qa",
    )

    private val SESSION_ASSETS = listOf(
        "pdfsys/session_scaffold/generate.py" to "work/generate.py",
        "pdfsys/session_scaffold/pack.html" to "work/pack.html",
        "pdfsys/qa_loop.py" to "work/qa_loop.py",
    )

    fun ensureWorkTree(sessionDir: File) {
        val work = PdfToolsPaths.workDir(sessionDir)
        work.mkdirs()
        WORK_SUBDIRS.forEach { File(work, it).mkdirs() }
    }

    fun writeSessionTemplates(context: Context, sessionDir: File) {
        ensureWorkTree(sessionDir)
        for ((asset, relative) in SESSION_ASSETS) {
            val dest = File(sessionDir, relative)
            if (dest.isFile) continue
            dest.parentFile?.mkdirs()
            context.assets.open(asset).use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
        }
        val layout = File(sessionDir, "work/layout_spec.json")
        if (!layout.isFile) {
            layout.writeText(
                """
                {
                  "version": 1,
                  "dpi": 300,
                  "ssim_min": 0.93,
                  "change_masks": [],
                  "note": "Optional per-page ignore regions for name/signature edits"
                }
                """.trimIndent() + "\n",
            )
        }
    }
}
