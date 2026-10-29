package com.termfold.app.pdftools

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

data class PdfToolsImportResult(
    val dest: File? = null,
    val sha256: String? = null,
    val error: String? = null,
)

object PdfToolsImport {
    fun copy(
        source: File,
        destDir: File,
        maxBytes: Long = PdfToolsPaths.MAX_IMPORT_BYTES,
        maxPages: Int = PdfToolsPaths.MAX_IMPORT_PAGES,
    ): PdfToolsImportResult {
        if (!source.isFile) return PdfToolsImportResult(error = "File not found")
        if (source.length() > maxBytes) {
            return PdfToolsImportResult(
                error = "PDF is over the ${maxBytes / (1024 * 1024)} MB import size limit",
            )
        }
        destDir.mkdirs()
        val dest = File(destDir, "source.pdf")
        source.copyTo(dest, overwrite = true)
        val pages = pageCount(dest)
        if (pages > maxPages) {
            dest.delete()
            return PdfToolsImportResult(
                error = "PDF has more than the $maxPages page import limit",
            )
        }
        return PdfToolsImportResult(dest = dest, sha256 = sha256(dest))
    }

    fun copyFromStream(
        input: InputStream,
        destDir: File,
        maxBytes: Long = PdfToolsPaths.MAX_IMPORT_BYTES,
        maxPages: Int = PdfToolsPaths.MAX_IMPORT_PAGES,
    ): PdfToolsImportResult {
        destDir.mkdirs()
        val tmp = File(destDir, "source.tmp")
        tmp.outputStream().use { out -> input.copyTo(out) }
        if (tmp.length() > maxBytes) {
            tmp.delete()
            return PdfToolsImportResult(
                error = "PDF is over the ${maxBytes / (1024 * 1024)} MB import size limit",
            )
        }
        val dest = File(destDir, "source.pdf")
        if (dest.exists()) dest.delete()
        tmp.renameTo(dest)
        val pages = pageCount(dest)
        if (pages > maxPages) {
            dest.delete()
            return PdfToolsImportResult(
                error = "PDF has more than the $maxPages page import limit",
            )
        }
        return PdfToolsImportResult(dest = dest, sha256 = sha256(dest))
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(8192)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun pageCount(file: File): Int {
        val text = file.readText(Charsets.ISO_8859_1)
        Regex("/Type\\s*/Pages\\b[\\s\\S]{0,800}?/Count\\s+(\\d+)")
            .find(text)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
            ?.takeIf { it > 0 }
            ?.let { return it }
        return Regex("/Type\\s*/Page(?!s)\\b").findAll(text).count().coerceAtLeast(1)
    }

    fun fallbackInspectJson(file: File): String {
        val obj = org.json.JSONObject()
        obj.put("ok", true)
        obj.put("pages", if (file.isFile) pageCount(file) else 0)
        obj.put("encrypted", false)
        obj.put("signed", false)
        obj.put("acroform", false)
        obj.put("actions_executed", false)
        obj.put("source", "local")
        return obj.toString()
    }
}
