package com.nova.browser

import android.net.Uri
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.util.concurrent.ConcurrentHashMap

object Speculate {
    private val seen = ConcurrentHashMap<String, Long>()
    private val ok by lazy {
        WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE) && WebViewFeature.isFeatureSupported(WebViewFeature.PRECONNECT)
    }
    @Volatile private var warmed = false

    private fun origin(url: String): String? {
        val u = runCatching { Uri.parse(url) }.getOrNull() ?: return null
        val h = u.host ?: return null
        return if (u.scheme == "https") "https://$h" else null
    }

    fun preconnect(wv: WebView, url: String) {
        if (!ok || !Prefs.boost) return
        val o = origin(url) ?: return
        val now = System.currentTimeMillis()
        val last = seen[o]
        if (last != null && now - last < 25_000L) return
        if (seen.size > 100) seen.clear()
        seen[o] = now
        wv.post { runCatching { WebViewCompat.getProfile(wv).preconnect(o) } }
    }

    fun warmStart(wv: WebView) {
        if (warmed) return
        warmed = true
        preconnect(wv, "https://www.google.com")
        preconnect(wv, "https://m.youtube.com")
    }
}
