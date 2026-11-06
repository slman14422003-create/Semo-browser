package com.nova.browser

import android.graphics.Color as AColor
import android.webkit.WebView

object PageTint {
    private const val JS = "(function(){function p(c){return c&&c!=='transparent'&&c!=='rgba(0, 0, 0, 0)'?c:null}var m=document.querySelector('meta[name=theme-color]');var c=m&&m.content;if(!c){var h=getComputedStyle(document.documentElement).backgroundColor;var b=document.body?getComputedStyle(document.body).backgroundColor:null;c=p(b)||p(h)}return c||''})()"
    private val RGB = Regex("rgba?\\(\\s*(\\d+)[,\\s]+(\\d+)[,\\s]+(\\d+)(?:[,/\\s]+([\\d.]+))?")

    fun onStart(tab: BrowserTab, url: String) {
        if (hostOf(tab.url) != hostOf(url)) tab.topColor = null
    }

    fun sample(v: WebView, tab: BrowserTab) {
        v.evaluateJavascript(JS) { raw -> tab.topColor = parse(raw) }
    }

    fun parse(raw: String?): Int? {
        val t = raw?.trim()?.trim('"')?.trim() ?: return null
        if (t.isEmpty() || t == "null") return null
        if (t.startsWith("#")) {
            val h = t.substring(1)
            val full = if (h.length == 3) h.map { "" + it + it }.joinToString("") else h
            return runCatching { AColor.parseColor("#" + full) or (0xFF shl 24) }.getOrNull()
        }
        val m = RGB.find(t)
        if (m != null) {
            val a = m.groupValues[4].toFloatOrNull() ?: 1f
            if (a < 0.5f) return null
            val r = m.groupValues[1].toInt().coerceIn(0, 255)
            val g = m.groupValues[2].toInt().coerceIn(0, 255)
            val b = m.groupValues[3].toInt().coerceIn(0, 255)
            return AColor.rgb(r, g, b)
        }
        return runCatching { AColor.parseColor(t) or (0xFF shl 24) }.getOrNull()
    }
}
