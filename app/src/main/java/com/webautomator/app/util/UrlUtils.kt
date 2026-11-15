package com.webautomator.app.util

import java.net.URI
import java.net.URISyntaxException

sealed class UrlResult {
    data class Valid(val url: String) : UrlResult()
    object Invalid : UrlResult()
}

object UrlUtils {
    /** Normalizes user input into an http/https URL, or returns Invalid. */
    fun normalize(input: String): UrlResult {
        val text = input.trim()
        if (text.isEmpty() || text.any { it.isWhitespace() }) return UrlResult.Invalid

        val withScheme = if (text.contains("://")) text else "https://$text"
        val uri = try {
            URI(withScheme)
        } catch (e: URISyntaxException) {
            return UrlResult.Invalid
        }

        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return UrlResult.Invalid

        val host = uri.host ?: return UrlResult.Invalid
        if (!host.contains('.')) return UrlResult.Invalid

        return UrlResult.Valid(withScheme)
    }

    fun hostOf(url: String): String =
        try { URI(url).host ?: "" } catch (e: Exception) { "" }

    /** Same site = same host, parent/child domain, or same last two labels. */
    fun sameSite(a: String, b: String): Boolean {
        val x = a.lowercase()
        val y = b.lowercase()
        if (x == y || x.endsWith(".$y") || y.endsWith(".$x")) return true
        return root(x) == root(y)
    }

    private fun root(host: String): String {
        val p = host.split('.')
        return if (p.size <= 2) host else p.takeLast(2).joinToString(".")
    }

    /** Key used to decide whether two URLs are the same page (ignores fragment and trailing slash). */
    fun pageKey(url: String): String = url.substringBefore('#').trimEnd('/')
}
