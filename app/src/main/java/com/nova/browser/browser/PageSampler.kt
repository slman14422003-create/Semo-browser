package com.nova.browser

import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import org.mozilla.geckoview.GeckoView

/** احتياطي فقط: يلتقط الشاشة إذا لم تُرسل الإضافة لون الصفحة. لا يكتب فوق ألوان وصلت من الصفحة، ويتجاهل البكسلات الشفافة. */
object PageSampler {
    fun sample(tab: BrowserTab) {
        if (tab.topColor != null && tab.botColor != null) return
        val view = find(tab.engineView) ?: return
        val url = tab.url
        runCatching {
            view.capturePixels().accept({ bitmap ->
                if (bitmap != null && tab.url == url) {
                    if (tab.topColor == null) dominant(bitmap, 2)?.let { tab.topColor = it }
                    if (tab.botColor == null) dominant(bitmap, bitmap.height - 3)?.let { tab.botColor = it }
                    bitmap.recycle()
                }
            }, { })
        }
    }

    private fun find(v: View?): GeckoView? {
        if (v is GeckoView) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) find(v.getChildAt(i))?.let { return it }
        return null
    }

    private fun dominant(bitmap: Bitmap, row: Int): Int? {
        val y = row.coerceIn(0, bitmap.height - 1)
        val counts = HashMap<Int, Int>()
        for (i in 0 until 24) {
            val color = bitmap.getPixel((bitmap.width - 1) * i / 23, y)
            if ((color ushr 24) < 250) continue   // شفاف = لا معلومة (كان يتحول إلى أسود)
            counts[color] = (counts[color] ?: 0) + 1
        }
        return counts.maxByOrNull { it.value }?.key
    }
}
