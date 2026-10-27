package com.termfold.app.prreview

import com.termfold.app.acp.AcpItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrReviewReportParserTest {
    private val validJson = """
        {
          "schemaVersion": 1,
          "summary": "Adds login.",
          "risk": "medium",
          "findings": [
            {
              "id": "A-1",
              "surface": "android",
              "severity": "blocker",
              "title": "NPE on rotate",
              "location": "Foo.kt:12",
              "inDiff": true,
              "evidence": "view is null",
              "suggestion": "null-check"
            },
            {
              "id": "P-1",
              "surface": "shared",
              "severity": "nit",
              "title": "Old typo",
              "location": "Bar.kt:3",
              "inDiff": false,
              "evidence": "unchanged line",
              "suggestion": "optional"
            }
          ],
          "coverage": { "reviewed": ["Foo.kt"], "skipped": ["lockfile"] },
          "missingTests": ["rotation test"],
          "openQuestions": ["Intentional?"]
        }
    """.trimIndent()

    @Test
    fun parsesFencedJson() {
        val text = "Here is the review.\n```pr-review-report\n$validJson\n```\nWhich ids?"
        val parsed = PrReviewReportParser.parseAgentText(text) as PrReviewParse.Ok
        assertEquals("Adds login.", parsed.report.summary)
        assertEquals(PrReviewRisk.MEDIUM, parsed.report.risk)
        assertEquals(listOf("A-1"), parsed.report.inDiffBySurface()[PrReviewSurface.ANDROID]?.map { it.id })
        assertEquals(listOf("P-1"), parsed.report.outOfDiff().map { it.id })
        assertEquals(listOf("Foo.kt"), parsed.report.coverage.reviewed)
        assertEquals(listOf("rotation test"), parsed.report.missingTests)
    }

    @Test
    fun missingFence() {
        assertEquals(PrReviewParse.Missing, PrReviewReportParser.parseAgentText("just prose"))
    }

    @Test
    fun invalidJson() {
        val text = "```pr-review-report\n{not json\n```"
        val parsed = PrReviewReportParser.parseAgentText(text)
        assertTrue(parsed is PrReviewParse.Invalid)
    }

    @Test
    fun parseTurnSliceUsesOnlyThatTurnAgentText() {
        val turnOne = listOf(
            AcpItem.UserMessage(1, "first", emptyList()),
            AcpItem.AgentText(2, "```pr-review-report\n$validJson\n```"),
        )
        val turnTwo = listOf(
            AcpItem.UserMessage(3, "second", emptyList()),
            AcpItem.AgentText(4, "no fence yet"),
        )
        val first = PrReviewReportParser.parseTurnSlice(turnOne) as PrReviewParse.Ok
        assertEquals("Adds login.", first.report.summary)
        assertEquals(PrReviewParse.Missing, PrReviewReportParser.parseTurnSlice(turnTwo))
        assertEquals(PrReviewParse.Missing, PrReviewReportParser.parseTurn(turnOne + turnTwo))
    }

    @Test
    fun ignoresThoughtAndUsesLastAgentText() {
        val items = listOf(
            AcpItem.UserMessage(1, "review", emptyList()),
            AcpItem.Thought(2, "```pr-review-report\n$validJson\n```"),
            AcpItem.AgentText(3, "working…"),
            AcpItem.AgentText(4, "\n```pr-review-report\n$validJson\n```\nask ids"),
        )
        val parsed = PrReviewReportParser.parseTurn(items) as PrReviewParse.Ok
        assertEquals("A-1", parsed.report.findings.first().id)
    }

    @Test
    fun thoughtOnlyIsSkipped() {
        val items = listOf(
            AcpItem.UserMessage(1, "review", emptyList()),
            AcpItem.Thought(2, "```pr-review-report\n$validJson\n```"),
        )
        assertEquals(PrReviewParse.Skipped, PrReviewReportParser.parseTurn(items))
    }

    @Test
    fun noUserMessageIsSkipped() {
        val items = listOf(AcpItem.AgentText(1, "connecting"))
        assertEquals(PrReviewParse.Skipped, PrReviewReportParser.parseTurn(items))
    }

    @Test
    fun stripFencesKeepsProseAroundJson() {
        val text = "Intro.\n```pr-review-report\n$validJson\n```\nWhich ids: A-1?"
        assertEquals("Intro.\n\nWhich ids: A-1?", PrReviewReportParser.stripFences(text))
    }

    @Test
    fun lastFenceWins() {
        val first = validJson.replace("Adds login.", "first")
        val second = validJson.replace("Adds login.", "second")
        val text = "```pr-review-report\n$first\n```\nthen\n```pr-review-report\n$second\n```"
        val parsed = PrReviewReportParser.parseAgentText(text) as PrReviewParse.Ok
        assertEquals("second", parsed.report.summary)
    }

    @Test
    fun inDiffStringFalseIsOutOfDiff() {
        val json = validJson.replace("\"inDiff\": false", "\"inDiff\": \"false\"")
            .replace("\"inDiff\": true", "\"inDiff\": true")
        val parsed = PrReviewReportParser.parseJson(json) as PrReviewParse.Ok
        assertEquals(listOf("P-1"), parsed.report.outOfDiff().map { it.id })
    }
}
