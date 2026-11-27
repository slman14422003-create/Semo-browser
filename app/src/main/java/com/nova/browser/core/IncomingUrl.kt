package com.nova.browser

import java.net.URLEncoder

object IncomingUrl {
    const val MAX_LEN = 8192
    private val found = Regex("(?i)https?://[^\\s<>\"]+")
    private val web = Regex("(?i)^https?://[^\\s/?#\\\\]+.*$", RegexOption.DOT_MATCHES_ALL)
    private val scheme = Regex("^[\\s\\u0000-\\u0020]*([a-zA-Z][a-zA-Z0-9+.\\-]*):(.?)", RegexOption.DOT_MATCHES_ALL)

    @JvmStatic
    fun isWeb(u: String): Boolean = web.matches(u) && u.none { it.isISOControl() }

    private fun foreignScheme(c: String): Boolean {
        val m = scheme.find(c) ?: return false
        val name = m.groupValues[1].lowercase()
        if (name == "http" || name == "https") return false
        val next = m.groupValues[2]
        return !(next.isNotEmpty() && next[0].isDigit())
    }

    @JvmStatic
    fun resolve(raw: String, searchBase: String, extract: Boolean): String? {
        val t = raw.trim()
        if (t.isEmpty() || t.length > MAX_LEN) return null
        val c = if (extract) found.find(t)?.value ?: t else t
        val search = { searchBase + URLEncoder.encode(c, "UTF-8") }
        val u = when {
            isWeb(c) -> c
            foreignScheme(c) -> search()
            c.contains('.') && c.none { it.isWhitespace() || it.isISOControl() } -> "https://$c"
            else -> search()
        }
        return u.takeIf { isWeb(it) }
    }
}
