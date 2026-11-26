package com.nova.browser

import android.graphics.Bitmap

object Thumbs {
    private val budget = (Runtime.getRuntime().maxMemory() / 16).coerceIn(12L shl 20, 48L shl 20)
    private val held = LinkedHashMap<BrowserTab, Int>(16, 0.75f, true)

    fun put(tab: BrowserTab, bitmap: Bitmap) {
        tab.thumb = bitmap
        held[tab] = bitmap.allocationByteCount
        var total = held.values.sumOf { it.toLong() }
        val it = held.entries.iterator()
        while (total > budget && held.size > 1 && it.hasNext()) {
            val e = it.next()
            if (e.key === tab) continue
            total -= e.value
            e.key.thumb = null
            it.remove()
        }
    }

    fun drop(tab: BrowserTab) { held.remove(tab) }
}
