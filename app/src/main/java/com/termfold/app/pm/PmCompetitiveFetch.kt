package com.termfold.app.pm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

object PmCompetitiveFetch {
    private const val MAX_BYTES = 48_000
    private const val TIMEOUT_MS = 15_000

    suspend fun fetchSnapshot(competitors: List<PmCompetitor>): String = withContext(Dispatchers.IO) {
        val array = JSONArray()
        val now = System.currentTimeMillis()
        for (competitor in competitors) {
            val homepage = competitor.homepageUrl.trim()
            if (homepage.isBlank()) {
                array.put(
                    JSONObject()
                        .put("name", competitor.name)
                        .put("gap", "No homepage URL — skipped web fetch")
                        .put("fetchedAt", now),
                )
                continue
            }
            val fetched = runCatching { htmlToText(fetchUrl(homepage)) }.getOrElse { it.message ?: "fetch failed" }
            array.put(
                JSONObject()
                    .put("name", competitor.name)
                    .put("url", homepage)
                    .put("excerpt", fetched.take(4000))
                    .put("fetchedAt", now),
            )
            for (extra in competitor.extraUrls.take(2)) {
                val extraBody = runCatching { htmlToText(fetchUrl(extra)) }.getOrElse { it.message ?: "fetch failed" }
                array.put(
                    JSONObject()
                        .put("name", competitor.name)
                        .put("url", extra)
                        .put("excerpt", extraBody.take(2500))
                        .put("fetchedAt", now),
                )
            }
        }
        JSONObject().put("competitors", array).toString(2)
    }

    private fun fetchUrl(url: String): String {
        val uri = URI(url)
        if (uri.scheme != "http" && uri.scheme != "https") {
            throw IllegalArgumentException("Only http(s) URLs allowed")
        }
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "TermFold-PM/1.0")
        }
        conn.inputStream.use { input ->
            val buf = ByteArray(8192)
            val out = StringBuilder()
            var total = 0
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                total += n
                if (total > MAX_BYTES) break
                out.append(buf.decodeToString(0, n))
            }
            return out.toString()
        }
    }

    internal fun htmlToText(html: String): String =
        html
            .replace(Regex("(?is)<script[^>]*>.*?</script>"), " ")
            .replace(Regex("(?is)<style[^>]*>.*?</style>"), " ")
            .replace(Regex("(?s)<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(Regex("\\s+"), " ")
            .trim()
}
