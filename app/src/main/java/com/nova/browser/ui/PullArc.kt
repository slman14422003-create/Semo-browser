package com.nova.browser

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.ImageView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class ArcRefreshLayout(ctx: Context, color: Int) : SwipeRefreshLayout(ctx) {
    var arcColor: Int = color
        set(v) {
            if (field != v) {
                field = v
                paint.color = v
                postInvalidateOnAnimation()
            }
        }

    private val density = ctx.resources.displayMetrics.density
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private val triggerPx = UiLayout.ARC_TRIGGER_DP * density
    private val radius = UiLayout.ARC_DIAMETER_DP * density / 2f
    private val restY = UiLayout.ARC_REST_DP * density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = UiLayout.ARC_STROKE_DP * density
        this.color = color
    }
    private val oval = RectF()
    private val haptics = PullHaptics(UiLayout.CLICK_GAP_MS)
    private var downY = 0f
    private var pull = 0f
    private var dragging = false
    private var shown = 0f
    private var spinStart = 0L
    private var lastDraw = 0L

    init {
        setDistanceToTriggerSync(triggerPx.toInt())
        setSlingshotDistance((UiLayout.ARC_SLINGSHOT_DP * density).toInt())
        try {
            for (i in 0 until childCount) {
                val v = getChildAt(i)
                if (v is ImageView) {
                    v.setImageDrawable(null)
                    v.background = android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
                    v.outlineProvider = null
                    v.elevation = 0f
                    v.alpha = 0f
                    break
                }
            }
        } catch (_: Throwable) {
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            downY = ev.y
            pull = 0f
            dragging = false
            haptics.reset()
        }
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val r = super.onTouchEvent(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                val raw = ev.y - downY - slop
                if (raw > 0f && !isRefreshing) {
                    dragging = true
                    pull = (raw * DRAG_RATE / triggerPx).coerceIn(0f, 1.5f)
                    invalidateArc()
                    feel(pull)
                } else feel(0f)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                haptics.reset()
                invalidateArc()
            }
        }
        return r
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        val now = SystemClock.uptimeMillis()
        val dt = if (lastDraw == 0L) 16L else (now - lastDraw).coerceIn(0L, 64L)
        lastDraw = now
        val refreshing = isRefreshing
        val active = refreshing || (dragging && pull > 0.02f)
        shown = if (active) (shown + dt / FADE_IN_MS).coerceAtMost(1f) else (shown - dt / FADE_OUT_MS).coerceAtLeast(0f)
        if (refreshing && spinStart == 0L) spinStart = now
        if (!refreshing) spinStart = 0L
        if (shown <= 0f) {
            if (!dragging) pull = 0f
            lastDraw = 0L
            return
        }
        val p = pull.coerceAtMost(1f)
        val frac = if (refreshing) 1f else p
        val cy = restY * frac
        val cx = width / 2f
        oval.set(cx - radius, cy - radius, cx + radius, cy + radius)
        val a = if (refreshing) shown else shown * (p * 2.5f).coerceAtMost(1f)
        paint.alpha = (a * 255f).toInt().coerceIn(0, 255)
        val start: Float
        val sweep: Float
        if (refreshing) {
            start = -90f + ((now - spinStart) % SPIN_MS) / SPIN_MS.toFloat() * 360f
            sweep = 270f
        } else {
            start = -90f + p * 120f
            sweep = 300f * p
        }
        if (sweep > 0f) canvas.drawArc(oval, start, sweep, false, paint)
        invalidateArc()
    }

    private fun feel(fraction: Float) {
        when (haptics.onPull(fraction, SystemClock.uptimeMillis())) {
            PullHaptics.ENGAGED -> Haptics.threshold(this, true)
            PullHaptics.RELEASED -> Haptics.threshold(this, false)
        }
    }

    private fun invalidateArc() {
        val pad = (radius + paint.strokeWidth + 2f).toInt()
        val cx = width / 2
        postInvalidateOnAnimation(cx - pad, 0, cx + pad, (restY + pad).toInt())
    }

    private companion object {
        const val DRAG_RATE = 0.5f
        const val FADE_IN_MS = 120f
        const val FADE_OUT_MS = 180f
        const val SPIN_MS = 900L
    }
}
