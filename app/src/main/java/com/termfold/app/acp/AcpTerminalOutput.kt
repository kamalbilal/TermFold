package com.termfold.app.acp

import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * Display-only ACP terminal bytes (`terminal_output_chunk` / `terminal_update.output`).
 * Keys are agent-owned [terminalId] values, independent of [AcpItem.ToolCall.toolCallId].
 */
object AcpTerminalOutput {
    private val bytesByTerminalId = ConcurrentHashMap<String, ByteArray>()

    fun replaceSnapshot(terminalId: String, base64Data: String) {
        if (terminalId.isBlank() || base64Data.isBlank()) return
        bytesByTerminalId[terminalId] = Base64.getDecoder().decode(base64Data)
    }

    fun appendChunk(terminalId: String, base64Data: String) {
        if (terminalId.isBlank() || base64Data.isBlank()) return
        val chunk = Base64.getDecoder().decode(base64Data)
        if (chunk.isEmpty()) return
        bytesByTerminalId.compute(terminalId) { _, prev ->
            if (prev == null) chunk else prev + chunk
        }
    }

    fun displayText(terminalId: String): String {
        val bytes = bytesByTerminalId[terminalId] ?: return ""
        return decodeUtf8Terminal(bytes)
    }

    fun clearSession() {
        bytesByTerminalId.clear()
    }

    /**
     * Drop a trailing partial UTF-8 code point so chunk boundaries do not show U+FFFD flicker.
     */
    internal fun decodeUtf8Terminal(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        var end = bytes.size
        while (end > 0) {
            if (isCompleteUtf8(bytes.copyOfRange(0, end))) {
                return String(bytes, 0, end, Charsets.UTF_8)
            }
            end--
        }
        return ""
    }

    private fun isCompleteUtf8(bytes: ByteArray): Boolean {
        var i = 0
        while (i < bytes.size) {
            val b0 = bytes[i].toInt() and 0xFF
            val len = when {
                b0 and 0x80 == 0 -> 1
                b0 and 0xE0 == 0xC0 -> 2
                b0 and 0xF0 == 0xE0 -> 3
                b0 and 0xF8 == 0xF0 -> 4
                else -> return false
            }
            if (i + len > bytes.size) return false
            for (j in 1 until len) {
                if (bytes[i + j].toInt() and 0xC0 != 0x80) return false
            }
            i += len
        }
        return true
    }
}
