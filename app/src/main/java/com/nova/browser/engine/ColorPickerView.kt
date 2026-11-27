package com.nova.browser

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View

class ColorPickerView(ctx: Context) : View(ctx) {
    var onChange: ((Int) -> Unit)? = null

    private val dp = ctx.resources.displayMetrics.density
    private var hue = 0f
    private var sat = 1f
    private var value = 1f
    private var target = 0

    private val svRect = RectF()
    private val hueRect = RectF()
    private val svPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val huePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val corner = 8 * dp
    private val gap = 16 * dp
    private val hueHeight = 28 * dp

    val color: Int get() = ColorPick.fromHsv(hue, sat, value) or 0xFF000000.toInt()

    fun setColor(rgb: Int) {
        val h = ColorPick.toHsv(rgb)
        hue = h[0]
        sat = h[1]
        value = h[2]
        rebuildSv()
        invalidate()
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val w = MeasureSpec.getSize(widthSpec)
        val h = (w * 0.7f + gap + hueHeight).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val svHeight = h - gap - hueHeight
        svRect.set(0f, 0f, w.toFloat(), svHeight)
        hueRect.set(0f, svHeight + gap, w.toFloat(), h.toFloat())
        val stops = intArrayOf(0xFFFF0000.toInt(), 0xFFFFFF00.toInt(), 0xFF00FF00.toInt(), 0xFF00FFFF.toInt(), 0xFF0000FF.toInt(), 0xFFFF00FF.toInt(), 0xFFFF0000.toInt())
        huePaint.shader = LinearGradient(0f, 0f, w.toFloat(), 0f, stops, null, Shader.TileMode.CLAMP)
        rebuildSv()
    }

    private fun rebuildSv() {
        if (svRect.width() <= 0f) return
        val pure = Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
        val horizontal = LinearGradient(svRect.left, 0f, svRect.right, 0f, Color.WHITE, pure, Shader.TileMode.CLAMP)
        val vertical = LinearGradient(0f, svRect.top, 0f, svRect.bottom, Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP)
        svPaint.shader = ComposeShader(horizontal, vertical, PorterDuff.Mode.MULTIPLY)
    }

    override fun onDraw(c: Canvas) {
        c.drawRoundRect(svRect, corner, corner, svPaint)
        c.drawRoundRect(hueRect, corner, corner, huePaint)
        val kx = svRect.left + sat * svRect.width()
        val ky = svRect.top + (1f - value) * svRect.height()
        drawKnob(c, kx, ky, 11 * dp)
        val hx = hueRect.left + hue / 360f * hueRect.width()
        drawKnob(c, hx, hueRect.centerY(), 12 * dp)
    }

    private fun drawKnob(c: Canvas, x: Float, y: Float, r: Float) {
        ring.strokeWidth = 3 * dp
        ring.color = Color.WHITE
        c.drawCircle(x, y, r, ring)
        ring.strokeWidth = 1 * dp
        ring.color = 0x66000000
        c.drawCircle(x, y, r + 2 * dp, ring)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                target = if (ev.y < svRect.bottom + gap / 2) 1 else 2
                move(ev)
            }
            MotionEvent.ACTION_MOVE -> move(ev)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }

    private fun move(ev: MotionEvent) {
        if (target == 1) {
            sat = ((ev.x - svRect.left) / svRect.width()).coerceIn(0f, 1f)
            value = 1f - ((ev.y - svRect.top) / svRect.height()).coerceIn(0f, 1f)
        } else {
            hue = (((ev.x - hueRect.left) / hueRect.width()).coerceIn(0f, 1f) * 360f).coerceAtMost(359.99f)
            rebuildSv()
        }
        invalidate()
        onChange?.invoke(color)
    }
}
