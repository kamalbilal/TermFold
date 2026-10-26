package com.termfold.app.prreview

import android.content.Context
import com.termfold.app.acp.AcpInstaller
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

data class GitHubPrInfo(
    val title: String,
    val headRef: String,
    val htmlUrl: String,
)

sealed class GitHubPrFetch {
    data class Ok(val info: GitHubPrInfo) : GitHubPrFetch()
    data class Error(val message: String) : GitHubPrFetch()
}

fun interface PrHttpGet {
    fun get(url: String): Pair<Int, String>
}

object GitHubPrApi {
    fun fetch(
        context: Context,
        ref: GitHubPrRef,
        onProgress: (String) -> Unit = {},
        http: PrHttpGet = DefaultPrHttp,
    ): GitHubPrFetch {
        AcpInstaller.ensureGh(context, onProgress)
        onProgress("Loading pull request from GitHub…")
        val ghResult = fetchWithGh(context, ref)
        if (ghResult != null) return ghResult
        return fetchWithHttp(ref, http)
    }

    /** For unit tests without a guest. */
    fun fetch(ref: GitHubPrRef, http: PrHttpGet = DefaultPrHttp): GitHubPrFetch =
        fetchWithHttp(ref, http)

    private fun fetchWithGh(context: Context, ref: GitHubPrRef): GitHubPrFetch? {
        val probe = AcpInstaller.runCapture(context, "command -v gh", timeoutSeconds = 30)
        if (probe.first != 0 || probe.second.trim().isEmpty()) return null
        val repo = "${ref.owner}/${ref.repo}"
        val quotedRepo = "'" + repo.replace("'", "'\\''") + "'"
        val cmd = "gh pr view ${ref.number} --repo $quotedRepo --json title,headRefName,url"
        val (code, out) = AcpInstaller.runCapture(context, cmd, timeoutSeconds = 120)
        if (code == 0) return parseGhBody(out, ref)
        val message = ghErrorMessage(out, code)
        if (message != null) return GitHubPrFetch.Error(message)
        return null
    }

    internal fun parseGhBody(body: String, ref: GitHubPrRef): GitHubPrFetch {
        val json = runCatching { JSONObject(body.trim()) }.getOrElse {
            return GitHubPrFetch.Error("GitHub CLI returned unexpected output.")
        }
        val title = json.optString("title").ifBlank { "PR #${ref.number}" }
        val headRef = json.optString("headRefName").ifBlank {
            return GitHubPrFetch.Error("GitHub CLI did not include the PR head branch.")
        }
        val htmlUrl = json.optString("url").ifBlank {
            "https://github.com/${ref.owner}/${ref.repo}/pull/${ref.number}"
        }
        return GitHubPrFetch.Ok(GitHubPrInfo(title = title, headRef = headRef, htmlUrl = htmlUrl))
    }

    private fun ghErrorMessage(output: String, exitCode: Int): String? {
        val lower = output.lowercase()
        when {
            lower.contains("not logged") ||
                lower.contains("auth login") ||
                lower.contains("authentication") ||
                lower.contains("401") -> {
                return "GitHub CLI is not signed in. Open Shell and run gh auth login, then retry."
            }
            lower.contains("could not resolve") && lower.contains("pr") -> {
                return "Could not find that pull request. Check the link and that you can see the PR on GitHub."
            }
            exitCode != 0 && output.isNotBlank() -> {
                val line = output.lines().firstOrNull { it.isNotBlank() }?.trim()
                if (line != null) return line.take(400)
            }
        }
        return if (exitCode != 0) {
            "GitHub CLI could not load the pull request (exit $exitCode)."
        } else {
            null
        }
    }

    private fun fetchWithHttp(ref: GitHubPrRef, http: PrHttpGet): GitHubPrFetch {
        val url = "https://api.github.com/repos/${ref.owner}/${ref.repo}/pulls/${ref.number}"
        val (code, body) = runCatching { http.get(url) }.getOrElse { err ->
            return GitHubPrFetch.Error(err.message?.takeIf { it.isNotBlank() } ?: "Could not reach GitHub.")
        }
        return when (code) {
            200 -> parseHttpBody(body, ref)
            401, 403, 404 -> GitHubPrFetch.Error(
                "Could not load that pull request (GitHub $code). Run gh auth login in Shell, then retry.",
            )
            else -> GitHubPrFetch.Error("GitHub answered $code.")
        }
    }

    internal fun parseHttpBody(body: String, ref: GitHubPrRef): GitHubPrFetch {
        val json = runCatching { JSONObject(body) }.getOrElse {
            return GitHubPrFetch.Error("GitHub returned a response that was not JSON.")
        }
        val title = json.optString("title").ifBlank { "PR #${ref.number}" }
        val head = json.optJSONObject("head")
        val headRef = head?.optString("ref").orEmpty()
        if (headRef.isBlank()) {
            return GitHubPrFetch.Error("GitHub did not include the PR head branch.")
        }
        val htmlUrl = json.optString("html_url").ifBlank {
            "https://github.com/${ref.owner}/${ref.repo}/pull/${ref.number}"
        }
        return GitHubPrFetch.Ok(GitHubPrInfo(title = title, headRef = headRef, htmlUrl = htmlUrl))
    }
}

object DefaultPrHttp : PrHttpGet {
    override fun get(url: String): Pair<Int, String> {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 20_000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "TermFold")
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            return code to body
        } catch (e: IOException) {
            throw e
        } finally {
            connection.disconnect()
        }
    }
}
