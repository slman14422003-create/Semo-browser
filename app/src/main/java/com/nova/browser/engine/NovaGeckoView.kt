package com.nova.browser

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.PanZoomController
import java.lang.ref.WeakReference

class NovaGeckoView(ctx: Context) : GeckoView(ctx) {
    @Volatile
    private var dirs = -1

    @Volatile
    private var content = false

    fun pagePullBlocked(): Boolean? = when {
        content -> true
        dirs < 0 -> null
        else -> (dirs and TOP) != 0
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (session == null) return super.onTouchEvent(ev)
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            dirs = -1
            content = false
        }
        if (ev.actionMasked == MotionEvent.ACTION_DOWN || ev.actionMasked == MotionEvent.ACTION_UP) PopupGate.SHARED.touch()
        onTouchEventForDetailResult(ev).accept { d ->
            if (d != null) {
                dirs = d.scrollableDirections()
                if (d.handledResult() == PanZoomController.INPUT_RESULT_HANDLED_CONTENT) content = true
            }
        }
        return true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        PageBars.bind(this)
    }

    override fun onDetachedFromWindow() {
        PageBars.unbind(this)
        super.onDetachedFromWindow()
    }

    private companion object {
        const val TOP = 1
    }
}

object PageBars {
    private var view: WeakReference<NovaGeckoView>? = null
    private var max = 0
    private var clip = 0

    fun bind(v: NovaGeckoView) {
        view = WeakReference(v)
        push(v, true)
    }

    fun unbind(v: NovaGeckoView) {
        if (view?.get() === v) view = null
    }

    fun set(maxPx: Int, hiddenPx: Int) {
        val m = maxPx.coerceAtLeast(0)
        val c = hiddenPx.coerceIn(0, m)
        val maxChanged = m != max
        if (!maxChanged && c == clip) return
        max = m
        clip = c
        view?.get()?.let { push(it, maxChanged) }
    }

    private fun push(v: NovaGeckoView, withMax: Boolean) {
        runCatching {
            if (withMax) v.setDynamicToolbarMaxHeight(max)
            v.setVerticalClipping(clip)
        }
    }
}
