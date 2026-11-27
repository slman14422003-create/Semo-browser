package com.nova.browser

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.Window

object FrameRate {
    private const val RELEASE_MS = 700L
    private val main = Handler(Looper.getMainLooper())

    fun onTouch(window: Window, ev: MotionEvent) {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                main.removeCallbacksAndMessages(window)
                apply(window, true)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                main.removeCallbacksAndMessages(window)
                main.postDelayed({ apply(window, false) }, window, RELEASE_MS)
            }
        }
    }

    fun apply(window: Window, boost: Boolean) {
        runCatching {
            val want = when {
                Prefs.cap60 && Adaptive.level >= 1 -> 60f
                !boost || Adaptive.lowEnd || Adaptive.level > 0 -> 0f
                else -> peak(window)
            }
            val lp = window.attributes
            if (lp.preferredRefreshRate != want) {
                lp.preferredRefreshRate = want
                window.attributes = lp
            }
        }
    }

    private fun peak(window: Window): Float {
        val display = window.decorView.display ?: return 0f
        val now = display.mode
        val best = display.supportedModes
            .filter { it.physicalWidth == now.physicalWidth && it.physicalHeight == now.physicalHeight }
            .maxOfOrNull { it.refreshRate } ?: return 0f
        return if (best > 61f) best else 0f
    }
}
