package com.termfold.app.gitworkspace

/**
 * Session copies are cloned from the golden checkout, so `origin` often points at a local path.
 * Upstream repair needs the same remote as golden so `fetch` sees branches on the real remote.
 */
object SessionCopyOriginSync {

    fun isNetworkRemoteUrl(url: String): Boolean {
        val u = url.trim()
        return u.startsWith("http://", ignoreCase = true) ||
            u.startsWith("https://", ignoreCase = true) ||
            u.startsWith("git@", ignoreCase = true) ||
            u.startsWith("ssh://", ignoreCase = true) ||
            u.startsWith("git://", ignoreCase = true)
    }

    private fun looksLikeFilesystemCheckoutPath(url: String): Boolean {
        val u = url.trim()
        return u.startsWith("/") || u.matches(Regex("^[A-Za-z]:\\\\.*"))
    }

    /**
     * When [sessionCopyOriginUrl] still points at the golden checkout directory, use
     * [goldenOriginUrl] (`git remote get-url origin` in golden) — typically GitHub/GitLab or
     * the same bare remote golden uses.
     */
    fun resolveOriginUrlForSessionCopy(sessionCopyOriginUrl: String, goldenOriginUrl: String?): String? {
        val copy = sessionCopyOriginUrl.trim()
        if (isNetworkRemoteUrl(copy)) return copy
        val golden = goldenOriginUrl?.trim()?.takeIf { it.isNotBlank() } ?: return null
        if (looksLikeFilesystemCheckoutPath(copy) && golden != copy) return golden
        if (isNetworkRemoteUrl(golden)) return golden
        return null
    }
}
