package com.nova.browser

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

object PopupBar {
    private val main = Handler(Looper.getMainLooper())
    private var bar: View? = null
    private var expire: Runnable? = null

    fun show(act: Activity, text: String, action: String, timeoutMs: Long, onAllow: () -> Unit, onGone: () -> Unit) {
        hide()
        val root = act.findViewById<ViewGroup>(android.R.id.content)
        if (root == null) {
            onGone()
            return
        }
        val d = act.resources.displayMetrics.density
        val row = LinearLayout(act)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding((16 * d).toInt(), 0, (4 * d).toInt(), 0)
        val bg = GradientDrawable()
        bg.setColor(0xF02C2C2E.toInt())
        bg.cornerRadius = 14 * d
        row.background = bg
        row.elevation = 8 * d

        val label = TextView(act)
        label.text = text
        label.setTextColor(Color.WHITE)
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        label.setPadding(0, (12 * d).toInt(), 0, (12 * d).toInt())
        row.addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val btn = TextView(act)
        btn.text = action
        btn.setTextColor(0xFF0A84FF.toInt())
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        btn.gravity = Gravity.CENTER
        btn.minHeight = (44 * d).toInt()
        btn.minWidth = (64 * d).toInt()
        btn.setPadding((12 * d).toInt(), 0, (12 * d).toInt(), 0)
        btn.isClickable = true
        btn.contentDescription = action
        row.addView(btn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val nav = root.rootWindowInsets?.getInsets(WindowInsets.Type.navigationBars())?.bottom ?: 0
        val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)
        lp.setMargins((12 * d).toInt(), 0, (12 * d).toInt(), nav + (88 * d).toInt())
        root.addView(row, lp)
        bar = row

        btn.setOnClickListener {
            if (bar === row) {
                hide()
                onAllow()
            }
        }
        val r = Runnable {
            if (bar === row) {
                hide()
                onGone()
            }
        }
        expire = r
        main.postDelayed(r, timeoutMs)
    }

    fun hide() {
        expire?.let { main.removeCallbacks(it) }
        expire = null
        val v = bar
        bar = null
        if (v != null) (v.parent as? ViewGroup)?.removeView(v)
    }
}
