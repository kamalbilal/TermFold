package com.termfold.app.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationSpecCommandTest {

    @Test
    fun requestFromPrompt_extractsText() {
        assertEquals(
            "automate checkout on shop.example",
            AutomationSpecCommand.requestFromPrompt("/spec automate checkout on shop.example"),
        )
        assertEquals(
            "x",
            AutomationSpecCommand.requestFromPrompt("  /SPEC  x  "),
        )
    }

    @Test
    fun bareSpecPrompt() {
        assertTrue(AutomationSpecCommand.isBareSpecPrompt("/spec"))
        assertFalse(AutomationSpecCommand.isBareSpecPrompt("/spec need docs"))
        assertFalse(AutomationSpecCommand.isSpecPrompt("/summarize"))
    }

    @Test
    fun wirePrompt_mentionsAllDocFiles() {
        val wire = AutomationSpecCommand.toWirePrompt("test")
        assertTrue(wire.contains("docs/requirements.md"))
        assertTrue(wire.contains("docs/prd.md"))
        assertTrue(wire.contains("docs/constraints.md"))
    }
}
