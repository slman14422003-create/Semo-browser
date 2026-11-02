package com.nova.browser

import android.app.Dialog
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.text.TextUtils
import android.view.Gravity
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.Window
import android.view.WindowManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

object Popups {
    private val main = Handler(Looper.getMainLooper())
    private var dialog: Dialog? = null
    private var web: WebView? = null
    private var label: TextView? = null

    private val tick = object : Runnable {
        override fun run() {
            val w = web ?: return
            val u = w.url
            if (!u.isNullOrBlank() && u.startsWith("http")) {
                label?.text = (if (u.startsWith("https")) "\uD83D\uDD12 " else "") + hostOf(u)
            }
            main.postDelayed(this, 1000)
        }
    }

    fun create(h: Handlers, tab: BrowserTab, isDialog: Boolean, gesture: Boolean, msg: Message): Boolean {
        if (!gesture) return false
        if (!isDialog) return capture(h, msg)
        if (!Shield.dialogAllowed(hostOf(tab.url))) return false
        return show(h, msg)
    }

    private fun show(h: Handlers, msg: Message): Boolean {
        val a = h.activity
        val transport = msg.obj as? WebView.WebViewTransport ?: return false
        if (dialog != null || a.isFinishing || a.isDestroyed) return false
        val tab = BrowserTab(-1).also { it.popup = true }
        val wv = createWebView(a, tab, h)
        tab.webView = wv
        val density = a.resources.displayMetrics.density
        fun px(v: Int) = (v * density).toInt()
        val night = (a.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val bg = if (night) 0xFF1E1E22.toInt() else Color.WHITE
        val fg = if (night) 0xFFE6E6EA.toInt() else 0xFF1B1B1F.toInt()

        val title = TextView(a).apply {
            textSize = 14f
            setTextColor(fg)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL
        }
        val closeBtn = TextView(a).apply {
            text = "\u2715"
            textSize = 18f
            setTextColor(fg)
            gravity = Gravity.CENTER
            contentDescription = "Close"
            setOnClickListener { close() }
        }
        val bar = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(16), 0, px(4), 0)
        }
        bar.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(closeBtn, LinearLayout.LayoutParams(px(48), px(48)))

        val card = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { setColor(bg); cornerRadius = px(24).toFloat() }
            outlineProvider = ViewOutlineProvider.BACKGROUND
            clipToOutline = true
        }
        card.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(48)))
        (wv.parent as? ViewGroup)?.removeView(wv)
        card.addView(wv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val frame = FrameLayout(a).apply { setPadding(px(10), px(10), px(10), px(10)) }
        frame.addView(card, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val d = object : Dialog(a) {
            @Suppress("OVERRIDE_DEPRECATION")
            override fun onBackPressed() {
                if (wv.canGoBack()) wv.goBack() else dismiss()
            }
        }
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        d.setContentView(frame)
        d.setCancelable(true)
        d.setCanceledOnTouchOutside(false)
        d.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.55f)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        d.setOnDismissListener { cleanup(wv) }

        transport.webView = wv
        msg.sendToTarget()
        dialog = d
        web = wv
        label = title
        runCatching { d.show() }.onFailure { cleanup(wv) }
        main.post(tick)
        return true
    }

    private fun capture(h: Handlers, msg: Message): Boolean {
        val transport = msg.obj as? WebView.WebViewTransport ?: return false
        val tmp = WebView(h.activity)
        val done = BooleanArray(1)
        fun finish(raw: String?) {
            if (done[0]) return
            val s = raw ?: return
            if (!s.startsWith("http://") && !s.startsWith("https://")) return
            val uri = Uri.parse(s)
            if (Shield.isSpoofed(uri)) return
            done[0] = true
            h.openTab(Security.cleanUrl(uri).toString())
            main.post { runCatching { tmp.stopLoading(); tmp.destroy() } }
        }
        tmp.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                finish(r.url.toString())
                return true
            }

            override fun onPageStarted(v: WebView, u: String?, f: Bitmap?) {
                finish(u)
            }
        }
        transport.webView = tmp
        msg.sendToTarget()
        main.postDelayed({
            if (!done[0]) {
                done[0] = true
                runCatching { tmp.destroy() }
            }
        }, 8000)
        return true
    }

    private fun cleanup(wv: WebView) {
        main.removeCallbacks(tick)
        if (web === wv) {
            web = null
            dialog = null
            label = null
        }
        runCatching {
            (wv.parent as? ViewGroup)?.removeView(wv)
            wv.stopLoading()
            wv.destroy()
        }
    }

    fun close() {
        dialog?.let { d -> runCatching { d.dismiss() } }
    }

    fun close(window: WebView) {
        if (window === web) main.post { close() }
    }
}
