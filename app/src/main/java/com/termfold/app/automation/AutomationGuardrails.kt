package com.termfold.app.automation

import org.json.JSONObject
import java.io.File
import java.net.URI

/**
 * Machine-enforced automation policy from `docs/constraints.md`.
 *
 * Embed a block like:
 * ```
 * <!-- termfold-guardrails
 * legal_sources_only: true
 * allow_hosts:
 *   - localhost
 * deny_url_contains:
 *   - bad.example
 * -->
 * ```
 */
data class AutomationGuardrailPolicy(
    val legalSourcesOnly: Boolean = false,
    val allowHosts: List<String> = emptyList(),
    val denyUrlContains: List<String> = emptyList(),
    val protectLegalPolicy: Boolean = true,
) {
    companion object {
        val EMPTY = AutomationGuardrailPolicy()
    }
}

sealed class AutomationGuardrailVerdict {
    data object Allow : AutomationGuardrailVerdict()
    data class Deny(val reason: String) : AutomationGuardrailVerdict()
}

object AutomationGuardrails {

    const val GUARDRAIL_BLOCK_ANCHOR = "termfold-guardrails"

    private const val BLOCK_START = "<!-- $GUARDRAIL_BLOCK_ANCHOR"
    private const val BLOCK_END = "-->"

    /** Appended to older sessions that predate machine-enforced guardrails. */
    val defaultGuardrailBlockMarkdown: String = """
        
        ## Enforced by TermFold (do not remove)
        
        $BLOCK_START
        legal_sources_only: true
        protect_legal_policy: true
        $BLOCK_END
    """.trimIndent() + "\n"

    private val defaultLegalDenySubstrings = listOf(
        "torrent",
        "pirate",
        "piratebay",
        "1337x",
        "rarbg",
        "yts.",
        "limetorrent",
        "kickass",
        "ettv",
        "magnet:",
        "ddl-warez",
        "warez",
        "telegram rip",
    )

    private val urlInText = Regex("""https?://[^\s"'<>]+""", RegexOption.IGNORE_CASE)

    fun policyFromConstraintsFile(file: File): AutomationGuardrailPolicy {
        if (!file.isFile) return AutomationGuardrailPolicy.EMPTY
        return policyFromConstraintsText(file.readText())
    }

    /** Upgrades legacy `constraints.md` so guardrails apply without wiping user prose. */
    fun ensureGuardrailBlockInConstraints(constraintsFile: File): Boolean {
        if (!constraintsFile.isFile) return false
        if (constraintsFile.readText().contains(GUARDRAIL_BLOCK_ANCHOR)) return false
        constraintsFile.appendText("\n" + defaultGuardrailBlockMarkdown)
        return true
    }

    fun policyFromConstraintsText(text: String): AutomationGuardrailPolicy {
        val block = extractGuardrailBlock(text) ?: return AutomationGuardrailPolicy.EMPTY
        var legalSourcesOnly = false
        var protectLegalPolicy = true
        val allowHosts = mutableListOf<String>()
        val denyUrlContains = mutableListOf<String>()
        var inAllowHosts = false
        var inDeny = false
        block.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach
            when {
                line.startsWith("legal_sources_only:") -> {
                    legalSourcesOnly = line.substringAfter(':').trim().equals("true", ignoreCase = true)
                    inAllowHosts = false
                    inDeny = false
                }
                line.startsWith("protect_legal_policy:") -> {
                    protectLegalPolicy = line.substringAfter(':').trim().equals("true", ignoreCase = true)
                    inAllowHosts = false
                    inDeny = false
                }
                line.startsWith("allow_hosts:") -> {
                    inAllowHosts = true
                    inDeny = false
                }
                line.startsWith("deny_url_contains:") -> {
                    inDeny = true
                    inAllowHosts = false
                }
                line.startsWith("- ") && inAllowHosts -> allowHosts.add(line.removePrefix("- ").trim())
                line.startsWith("- ") && inDeny -> denyUrlContains.add(line.removePrefix("- ").trim())
            }
        }
        return AutomationGuardrailPolicy(
            legalSourcesOnly = legalSourcesOnly,
            allowHosts = allowHosts,
            denyUrlContains = denyUrlContains,
            protectLegalPolicy = protectLegalPolicy,
        )
    }

    fun evaluatePermission(
        params: JSONObject?,
        policy: AutomationGuardrailPolicy,
        constraintsFile: File?,
    ): AutomationGuardrailVerdict {
        if (policy == AutomationGuardrailPolicy.EMPTY && constraintsFile == null) {
            return AutomationGuardrailVerdict.Allow
        }
        val writeVerdict = evaluateProtectedConstraintsWrite(params, policy, constraintsFile)
        if (writeVerdict is AutomationGuardrailVerdict.Deny) return writeVerdict

        val urls = urlsFromPermissionParams(params)
        if (urls.isEmpty()) return AutomationGuardrailVerdict.Allow

        val denySubstrings = buildDenySubstrings(policy)
        for (url in urls) {
            val lower = url.lowercase()
            denySubstrings.firstOrNull { sub -> sub.isNotBlank() && lower.contains(sub.lowercase()) }?.let { hit ->
                return AutomationGuardrailVerdict.Deny(
                    "Blocked URL ($hit): automation guardrails in docs/constraints.md do not allow this destination.",
                )
            }
            if (policy.allowHosts.isNotEmpty()) {
                val host = hostOf(url)
                if (host != null && !hostAllowed(host, policy.allowHosts)) {
                    return AutomationGuardrailVerdict.Deny(
                        "Blocked host ($host): not in allow_hosts from docs/constraints.md.",
                    )
                }
            }
        }
        return AutomationGuardrailVerdict.Allow
    }

    fun guardrailSummaryForPrompt(policy: AutomationGuardrailPolicy): String? {
        if (policy == AutomationGuardrailPolicy.EMPTY) return null
        return buildString {
            appendLine("TermFold enforces guardrails from docs/constraints.md at tool-permission time (not only via prompting).")
            if (policy.legalSourcesOnly) appendLine("- legal_sources_only: true (piracy / leak / torrent destinations are blocked)")
            if (policy.allowHosts.isNotEmpty()) {
                appendLine("- allow_hosts: ${policy.allowHosts.joinToString()}")
            }
            if (policy.denyUrlContains.isNotEmpty()) {
                appendLine("- deny_url_contains: ${policy.denyUrlContains.joinToString()}")
            }
            if (policy.protectLegalPolicy) {
                appendLine("- protect_legal_policy: constraints cannot remove legal_sources_only via agent writes")
            }
        }.trim()
    }

    private fun evaluateProtectedConstraintsWrite(
        params: JSONObject?,
        policy: AutomationGuardrailPolicy,
        constraintsFile: File?,
    ): AutomationGuardrailVerdict {
        if (!policy.protectLegalPolicy || !policy.legalSourcesOnly || constraintsFile == null) {
            return AutomationGuardrailVerdict.Allow
        }
        val paths = AutomationReadOnlyPolicy.pathsFromPermissionParams(params)
        val touchesConstraints = paths.any { path ->
            path.endsWith("/docs/constraints.md") || path.endsWith("docs/constraints.md")
        }
        if (!touchesConstraints) return AutomationGuardrailVerdict.Allow
        val proposed = proposedTextFromPermission(params)
        if (proposed == null) return AutomationGuardrailVerdict.Allow
        val merged = proposed
        val newPolicy = policyFromConstraintsText(merged)
        if (!newPolicy.legalSourcesOnly) {
            return AutomationGuardrailVerdict.Deny(
                "Blocked edit to docs/constraints.md: cannot disable legal_sources_only while protect_legal_policy is on.",
            )
        }
        return AutomationGuardrailVerdict.Allow
    }

    private fun proposedTextFromPermission(params: JSONObject?): String? {
        val tool = params?.optJSONObject("toolCall") ?: return null
        val input = tool.optJSONObject("rawInput") ?: return null
        return listOf("content", "text", "new_string", "newText", "body")
            .firstNotNullOfOrNull { key -> input.optString(key).takeIf { it.isNotBlank() } }
    }

    private fun buildDenySubstrings(policy: AutomationGuardrailPolicy): List<String> {
        val out = mutableListOf<String>()
        out.addAll(policy.denyUrlContains)
        if (policy.legalSourcesOnly) out.addAll(defaultLegalDenySubstrings)
        return out.distinct()
    }

    fun urlsFromPermissionParams(params: JSONObject?): List<String> {
        if (params == null) return emptyList()
        val urls = linkedSetOf<String>()
        params.optString("message").takeIf { it.isNotBlank() }?.let { urls.addAll(extractUrls(it)) }
        val tool = params.optJSONObject("toolCall")
        tool?.optString("title")?.takeIf { it.isNotBlank() }?.let { urls.addAll(extractUrls(it)) }
        tool?.optJSONObject("rawInput")?.let { input ->
            listOf("url", "href", "target_url", "targetUrl").forEach { key ->
                input.optString(key).takeIf { it.isNotBlank() }?.let(urls::add)
            }
            urls.addAll(extractUrls(input.toString()))
        }
        return urls.toList()
    }

    private fun extractUrls(text: String): List<String> =
        urlInText.findAll(text).map { it.value.trimEnd('.', ',', ')') }.toList()

    private fun hostOf(url: String): String? = runCatching {
        val normalized = if (url.contains("://")) url else "https://$url"
        URI(normalized).host?.lowercase()
    }.getOrNull()

    private fun hostAllowed(host: String, allowHosts: List<String>): Boolean {
        val h = host.lowercase()
        return allowHosts.any { pattern ->
            val p = pattern.trim().lowercase().removePrefix("*.")
            when {
                pattern.startsWith("*.") -> h == p || h.endsWith(".$p")
                p == "localhost" -> h == "localhost" || h == "127.0.0.1" || h == "::1"
                else -> h == p || h.endsWith(".$p")
            }
        }
    }

    private fun extractGuardrailBlock(text: String): String? {
        val start = text.indexOf(BLOCK_START)
        if (start < 0) return null
        val end = text.indexOf(BLOCK_END, start)
        if (end < 0) return null
        return text.substring(start + BLOCK_START.length, end).trim()
    }
}
