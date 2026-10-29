package com.termfold.app.pdftools

import java.io.File

object PdfToolsActions {
    fun inspectNeeded(job: PdfToolsJobType, hasInputPdf: Boolean): Boolean =
        hasInputPdf && job != PdfToolsJobType.GENERATE || hasInputPdf

    fun shouldSkipInspectOnCreate(job: PdfToolsJobType): Boolean =
        job == PdfToolsJobType.GENERATE
}

object PdfToolsPdfsysHost {
    fun scriptFile(): File? {
        val candidates = listOf(
            File("app/src/main/assets/pdfsys/pdfsys.py"),
            File("src/main/assets/pdfsys/pdfsys.py"),
        )
        return candidates.firstOrNull { it.isFile }
    }

    fun fontsDir(): File? {
        val candidates = listOf(
            File("app/src/main/assets/pdf-fonts"),
            File("src/main/assets/pdf-fonts"),
        )
        return candidates.firstOrNull { it.isDirectory }
    }

    fun run(args: List<String>): Pair<Int, String> {
        val script = scriptFile() ?: return 127 to "pdfsys.py missing"
        val pb = ProcessBuilder(listOf("python3", script.absolutePath) + args)
        pb.redirectErrorStream(true)
        val proc = pb.start()
        val text = proc.inputStream.bufferedReader().readText()
        val code = proc.waitFor()
        return code to text
    }
}
