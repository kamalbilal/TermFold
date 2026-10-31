package com.termfold.app.acp

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Test

class AcpTerminalOutputTest {

    @Test
    fun appendChunksDecodeToText() {
        AcpTerminalOutput.clearSession()
        val id = "term_1"
        AcpTerminalOutput.appendChunk(id, Base64.getEncoder().encodeToString("hello ".toByteArray()))
        AcpTerminalOutput.appendChunk(id, Base64.getEncoder().encodeToString("world".toByteArray()))
        assertEquals("hello world", AcpTerminalOutput.displayText(id))
    }

    @Test
    fun snapshotReplacesPreviousBytes() {
        AcpTerminalOutput.clearSession()
        val id = "term_2"
        AcpTerminalOutput.appendChunk(id, Base64.getEncoder().encodeToString("old".toByteArray()))
        AcpTerminalOutput.replaceSnapshot(id, Base64.getEncoder().encodeToString("new".toByteArray()))
        assertEquals("new", AcpTerminalOutput.displayText(id))
    }

    @Test
    fun utf8BoundaryDoesNotSplitCodePoint() {
        val bytes = "café".toByteArray(Charsets.UTF_8)
        val split = bytes.size - 1
        val partial = bytes.copyOfRange(0, split)
        val decoded = AcpTerminalOutput.decodeUtf8Terminal(partial)
        assertEquals("caf", decoded)
    }
}
