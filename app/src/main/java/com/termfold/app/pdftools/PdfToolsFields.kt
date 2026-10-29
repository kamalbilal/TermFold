package com.termfold.app.pdftools

import org.json.JSONObject
import java.io.File

object PdfToolsFields {
    fun fromJson(raw: String): Map<String, String> {
        val obj = JSONObject(raw)
        return obj.keys().asSequence().associateWith { key -> obj.optString(key) }
    }

    fun fromCsv(raw: String): Result<Map<String, String>> {
        val lines = raw.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (lines.size < 2) return Result.failure(IllegalArgumentException("CSV needs a header and one data row"))
        if (lines.size > 2) {
            return Result.failure(IllegalArgumentException("CSV has more than one data row; this version accepts one row"))
        }
        val headers = splitCsv(lines[0])
        val values = splitCsv(lines[1])
        if (headers.size != values.size) {
            return Result.failure(IllegalArgumentException("CSV header and row have different column counts"))
        }
        return Result.success(headers.zip(values).toMap())
    }

    fun write(file: File, fields: Map<String, String>) {
        file.parentFile?.mkdirs()
        val obj = JSONObject()
        fields.forEach { (k, v) -> obj.put(k, v) }
        file.writeText(obj.toString())
    }

    fun ensure(file: File): File {
        if (!file.isFile) write(file, emptyMap())
        return file
    }

    fun read(file: File): Map<String, String> =
        if (file.isFile) fromJson(file.readText()) else emptyMap()

    private fun splitCsv(line: String): List<String> =
        line.split(',').map { it.trim().trim('"') }
}
