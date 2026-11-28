package com.nova.browser

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
import android.view.View
import android.view.ViewGroup
import org.mozilla.geckoview.GeckoView
import java.util.Collections
import java.util.WeakHashMap

object PageSampler {
    private val inflight: MutableSet<BrowserTab> = Collections.newSetFromMap(WeakHashMap<BrowserTab, Boolean>())

    fun sample(tab: BrowserTab) {
        val needColors = tab.topColor == null || tab.botColor == null
        val needGlass = Glass.level != Glass.FLAT && tab.glassUrl != tab.url
        if (!needColors && !needGlass) return
        if (tab.loading) return
        val view = find(tab.engineView) ?: return
        if (!inflight.add(tab)) return
        val url = tab.url
        runCatching {
            view.capturePixels().accept({ bitmap ->
                if (bitmap == null) {
                    release(tab)
                } else if (tab.url != url) {
                    bitmap.recycle()
                    release(tab)
                } else {
                    if (needColors) read(tab, bitmap)
                    if (needGlass) {
                        tab.glassUrl = url
                        Work.img {
                            val snap = GlassSnap.make(bitmap)
                            bitmap.recycle()
                            Work.ui {
                                if (snap != null && tab.url == url) tab.glassSnap = snap.asImageBitmap()
                                release(tab)
                            }
                        }
                    } else {
                        bitmap.recycle()
                        release(tab)
                    }
                }
            }, { release(tab) })
        }.onFailure { release(tab) }
    }

    private fun release(tab: BrowserTab) {
        inflight.remove(tab)
    }

    private fun read(tab: BrowserTab, bitmap: Bitmap) {
        val row = IntArray(bitmap.width)
        if (tab.topColor == null) dominant(bitmap, UiLayout.SAMPLE_TOP_ROW, row)?.let { tab.topColor = it }
        if (tab.botColor == null) dominant(bitmap, bitmap.height - UiLayout.SAMPLE_BOTTOM_INSET, row)?.let { tab.botColor = it }
    }

    private fun find(v: View?): GeckoView? {
        if (v is GeckoView) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) find(v.getChildAt(i))?.let { return it }
        return null
    }

    private fun dominant(bitmap: Bitmap, row: Int, buf: IntArray): Int? {
        val w = bitmap.width
        if (w <= 0 || bitmap.height <= 0) return null
        bitmap.getPixels(buf, 0, w, 0, row.coerceIn(0, bitmap.height - 1), w, 1)
        val counts = HashMap<Int, Int>()
        val last = UiLayout.SAMPLE_POINTS - 1
        for (i in 0 until UiLayout.SAMPLE_POINTS) {
            val color = buf[(w - 1) * i / last]
            if ((color ushr 24) < UiLayout.SAMPLE_MIN_ALPHA) continue
            counts[color] = (counts[color] ?: 0) + 1
        }
        return counts.maxByOrNull { it.value }?.key
    }
}
