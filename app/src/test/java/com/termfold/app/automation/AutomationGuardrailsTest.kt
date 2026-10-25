package com.termfold.app.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject
import java.io.File

class AutomationGuardrailsTest {

    @Test
    fun ensureGuardrailBlock_appendsToLegacyFile() {
        val file = File.createTempFile("constraints", ".md")
        file.writeText("# Constraints\n\nNo piracy.\n")
        assertTrue(AutomationGuardrails.ensureGuardrailBlockInConstraints(file))
        assertTrue(file.readText().contains("legal_sources_only: true"))
        file.delete()
    }

    @Test
    fun parsesGuardrailBlock() {
        val text = """
            # Constraints
            <!-- termfold-guardrails
            legal_sources_only: true
            allow_hosts:
              - localhost
            deny_url_contains:
              - evil
            -->
        """.trimIndent()
        val policy = AutomationGuardrails.policyFromConstraintsText(text)
        assertTrue(policy.legalSourcesOnly)
        assertEquals(listOf("localhost"), policy.allowHosts)
        assertEquals(listOf("evil"), policy.denyUrlContains)
    }

    @Test
    fun blocksPiracyUrlWhenLegalSourcesOnly() {
        val policy = AutomationGuardrailPolicy(legalSourcesOnly = true)
        val params = browserOpenParams("https://1337x.to/movie")
        val verdict = AutomationGuardrails.evaluatePermission(params, policy, null)
        assertTrue(verdict is AutomationGuardrailVerdict.Deny)
    }

    @Test
    fun allowHostEnforced() {
        val policy = AutomationGuardrailPolicy(allowHosts = listOf("shop.example.com"))
        val bad = AutomationGuardrails.evaluatePermission(
            browserOpenParams("https://other.com"),
            policy,
            null,
        )
        assertTrue(bad is AutomationGuardrailVerdict.Deny)
        val ok = AutomationGuardrails.evaluatePermission(
            browserOpenParams("https://shop.example.com"),
            policy,
            null,
        )
        assertTrue(ok is AutomationGuardrailVerdict.Allow)
    }

    @Test
    fun blocksWeakeningConstraintsFile() {
        val constraints = File.createTempFile("constraints", ".md")
        constraints.writeText(
            """
            <!-- termfold-guardrails
            legal_sources_only: true
            -->
            """.trimIndent(),
        )
        val policy = AutomationGuardrails.policyFromConstraintsText(constraints.readText())
        val params = JSONObject()
            .put(
                "toolCall",
                JSONObject()
                    .put("toolCallId", "w1")
                    .put("kind", "edit")
                    .put(
                        "rawInput",
                        JSONObject()
                            .put("path", "docs/constraints.md")
                            .put("content", "<!-- termfold-guardrails\nlegal_sources_only: false\n-->"),
                    ),
            )
        val verdict = AutomationGuardrails.evaluatePermission(params, policy, constraints)
        assertTrue(verdict is AutomationGuardrailVerdict.Deny)
        constraints.delete()
    }

    private fun browserOpenParams(url: String): JSONObject = JSONObject()
        .put(
            "toolCall",
            JSONObject()
                .put("toolCallId", "b1")
                .put("kind", "mcp")
                .put("title", "browser_open $url")
                .put("rawInput", JSONObject().put("url", url)),
        )
}
