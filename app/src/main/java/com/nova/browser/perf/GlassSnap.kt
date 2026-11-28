package com.nova.browser

import android.graphics.Bitmap

object GlassSnap {
    fun make(src: Bitmap): Bitmap? = runCatching {
        val w = UiLayout.SNAP_W
        val h = (src.height.toLong() * w / src.width.coerceAtLeast(1)).toInt().coerceAtLeast(1)
        val mid = Bitmap.createScaledBitmap(src, w * 4, h * 4, true)
        val small = Bitmap.createScaledBitmap(mid, w, h, true)
        if (mid !== src && mid !== small) mid.recycle()
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        if (small !== src) small.recycle()
        BoxBlur.apply(px, w, h, UiLayout.SNAP_BLUR_R, UiLayout.SNAP_PASSES)
        Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }.getOrNull()
}
