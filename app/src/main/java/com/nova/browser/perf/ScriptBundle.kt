package com.nova.browser

import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.util.WeakHashMap

object ScriptBundle {
    private val queues = WeakHashMap<WebView, MutableList<String>>()
    private val supported by lazy { WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) }

    @Synchronized
    fun begin(wv: WebView) {
        if (supported && queues[wv] == null) queues[wv] = ArrayList()
    }

    @Synchronized
    fun add(wv: WebView, js: String) {
        if (!supported) return
        val q = queues[wv]
        if (q != null) q.add(js) else register(wv, wrap(js))
    }

    @Synchronized
    fun flush(wv: WebView) {
        val q = queues.remove(wv) ?: return
        if (q.isEmpty()) return
        val sb = StringBuilder()
        for (s in q) sb.append(wrap(s))
        register(wv, sb.toString())
    }

    private fun wrap(js: String): String = "try{(function(){" + js + "\n}).call(window)}catch(e){}\n"

    private fun register(wv: WebView, js: String) {
        runCatching { WebViewCompat.addDocumentStartJavaScript(wv, js, setOf("*")) }
    }
}
