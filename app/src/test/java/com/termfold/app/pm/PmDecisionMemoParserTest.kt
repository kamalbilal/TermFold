package com.termfold.app.pm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PmDecisionMemoParserTest {
    @Test
    fun parsesFencedJsonMemo() {
        val text = """
            Here is the memo.
            ```json
            {"recommendation":"Ship A","options":"B or C","gaps":"none"}
            ```
        """.trimIndent()
        val memo = PmDecisionMemoParser.parseFromAgentText(text)
        assertNotNull(memo)
        assertEquals("Ship A", memo!!.recommendation)
    }

    @Test
    fun stripFencesRemovesJsonBlock() {
        val text = "intro\n```json\n{\"recommendation\":\"x\"}\n```\noutro"
        assertEquals("intro\n\noutro", PmDecisionMemoParser.stripFences(text))
    }

    @Test
    fun ignoresNonMemoJson() {
        assertNull(PmDecisionMemoParser.parseFromAgentText("no structured output"))
    }

    @Test
    fun toJsonRoundTripsThroughFromJson() {
        val memo = PmDecisionMemo(
            recommendation = "Ship",
            landscape = "Busy",
            options = "A / B",
            scopeIn = "MVP",
            scopeOut = "Enterprise",
            risks = "Low",
            gaps = "Pricing",
        )
        val restored = PmDecisionMemoParser.fromJson(org.json.JSONObject(PmDecisionMemoParser.toJson(memo)))
        assertEquals(memo, restored)
    }
}
