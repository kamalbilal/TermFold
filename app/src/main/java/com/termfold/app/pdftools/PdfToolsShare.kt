package com.termfold.app.pdftools

object PdfToolsShare {
    fun isPdf(mime: String?, names: List<String>): Boolean {
        if (mime?.equals("application/pdf", ignoreCase = true) == true) return true
        return names.any { it.endsWith(".pdf", ignoreCase = true) }
    }
}

object PdfToolsLog {
    fun safe(message: String, documentText: String? = null): String {
        if (documentText.isNullOrEmpty()) return message
        return message.replace(documentText, "[redacted]")
    }
}
