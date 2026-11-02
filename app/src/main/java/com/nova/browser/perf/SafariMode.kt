package com.nova.browser

import android.webkit.WebView
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.util.WeakHashMap

object SafariMode {
    private val handlers = WeakHashMap<WebView, ScriptHandler>()
    private val js: String? by lazy { runCatching { Perf.asset("safari.js") }.getOrNull() }

    fun sync(wv: WebView, on: Boolean) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
        val cur = handlers[wv]
        if (on && cur == null) {
            val s = js ?: return
            runCatching { handlers[wv] = WebViewCompat.addDocumentStartJavaScript(wv, s, setOf("*")) }
        } else if (!on && cur != null) {
            runCatching { cur.remove() }
            handlers.remove(wv)
        }
    }
}
