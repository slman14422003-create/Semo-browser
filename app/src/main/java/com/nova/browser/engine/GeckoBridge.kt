package com.nova.browser

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebResponse

const val USE_GECKO = true

class GeckoBridge(private val tab: BrowserTab, private val h: Handlers) : EngineListener {
    private val ctx: Context get() = h.activity
    private val main = Handler(Looper.getMainLooper())

    override fun onUrl(tab: EngineTab, url: String) {
        if (url.startsWith("http")) {
            this.tab.url = url
            this.tab.shieldHost = Shield.hostFor(url)
        }
    }

    override fun onTitle(tab: EngineTab, title: String) {
        if (title.isNotBlank()) this.tab.title = title
    }

    override fun onProgress(tab: EngineTab, progress: Int) {
        val f = progress / 100f
        if (progress == 0 || progress == 100 || kotlin.math.abs(f - this.tab.progress) >= 0.05f) this.tab.progress = f
    }

    override fun onLoading(tab: EngineTab, loading: Boolean) {
        this.tab.loading = loading
        if (loading) {
            this.tab.clearFailedIfNew()
            this.tab.thirdOn = false
        } else {
            (this.tab.engineView as? SwipeRefreshLayout)?.isRefreshing = false
            if (!this.tab.popup && tab.url.startsWith("http")) Library.visit(tab.url, tab.title)
        }
    }

    override fun onNav(tab: EngineTab, canBack: Boolean, canForward: Boolean) {
        this.tab.canBack = canBack
        this.tab.canForward = canForward
    }

    override fun onLoadRequest(tab: EngineTab, uri: String, target: Int, gesture: Boolean, redirect: Boolean): Boolean {
        val u = Uri.parse(uri)
        val current = target == GeckoSession.NavigationDelegate.TARGET_WINDOW_CURRENT
        return when (u.scheme) {
            "http", "https" -> {
                if (Shield.isSpoofed(u)) {
                    toast(ctx, "Blocked spoofed link")
                    false
                } else {
                    var c = Security.cleanUrl(u)
                    val host = c.host
                    if (current && Prefs.httpsFirst && c.scheme == "http" && host != null && host != this.tab.noUpgradeHost) {
                        val last = this.tab.lastUpgrade
                        val now = System.currentTimeMillis()
                        if (last != null && last.first == host && now - last.second < 5000) {
                            this.tab.noUpgradeHost = host
                        } else {
                            this.tab.upgradedFrom = c.toString()
                            c = c.buildUpon().scheme("https").build()
                            this.tab.upgradedTo = c.toString()
                            this.tab.lastUpgrade = host to now
                            Security.log("HTTPS", host)
                            main.post { tab.load(c.toString()) }
                            return false
                        }
                    }
                    if (current && c != u) {
                        main.post { tab.load(c.toString()) }
                        false
                    } else true
                }
            }
            null, "about", "blob", "data", "resource", "moz-extension", "view-source" -> true
            "intent" -> {
                WebCompat.intentTarget(uri)?.takeIf { !WebCompat.samePage(tab.url, it) }?.let { f ->
                    val fu = Uri.parse(f)
                    if (!Shield.isSpoofed(fu)) main.post { tab.load(Security.cleanUrl(fu).toString()) }
                }
                false
            }
            "tel", "mailto", "sms", "geo" -> {
                if (gesture) runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, u)) }
                false
            }
            else -> {
                if (gesture) PageExtras.openExternal(ctx, u)
                false
            }
        }
    }

    override fun onFind(tab: EngineTab, current: Int, total: Int) {
        this.tab.findInfo = if (total == 0) "0" else "$current/$total"
    }

    override fun onNewTab(tab: EngineTab, uri: String): EngineTab? {
        h.openTab(uri)
        return null
    }

    override fun onDownload(tab: EngineTab, response: WebResponse) {
        fun header(name: String) = response.headers.entries.firstOrNull { it.key.equals(name, true) }?.value
        val cd = header("Content-Disposition")
        val mime = header("Content-Type")
        val uri = response.uri
        val ref = tab.url
        if (uri.startsWith("blob:", true) || uri.startsWith("data:", true)) {
            if (!Shield.downloadAllowed(hostOf(ref.ifBlank { uri }))) {
                runCatching { response.body?.close() }
                toast(ctx, L("Blocked repeated automatic downloads from this page"))
            } else StreamDownload.start(ctx, response, cd, mime)
            return
        }
        runCatching { response.body?.close() }
        h.onDownload(uri, null, cd, mime, ref)
    }

    override fun onCrash(tab: EngineTab) {
        main.post {
            this.tab.engine?.close()
            this.tab.engine = null
            this.tab.engineView = null
            this.tab.loading = false
            this.tab.holdLoad = !WebCompat.rendererGone(this.tab)
            this.tab.epoch++
        }
    }

    override fun onFullScreen(tab: EngineTab, on: Boolean) {
        val w = h.activity.window
        val c = WindowCompat.getInsetsController(w, w.decorView)
        if (on) {
            c.hide(WindowInsetsCompat.Type.systemBars())
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            c.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

fun geckoContainer(ctx: Context, tb: BrowserTab, h: Handlers, primary: Int, bg: Int, spinner: Int): View {
    val view = GeckoView(ctx)
    var fresh = false
    val e = tb.engine ?: run {
        fresh = true
        val st = tb.engineState
        tb.engineState = null
        EngineTab(tb.id, GeckoBridge(tb, h), st, tb.desktop, GeckoUi(h), GeckoSelection(h.activity) { id, text -> SmartAssist.onSelection(h.activity, id, text, h.openTab) }).also { tb.engine = it }
    }
    e.attach(view)
    if (fresh && !e.restored) {
        if (tb.holdLoad) {
            tb.holdLoad = false
            e.loadHtml(errorHtml(tb.url, "The page process stopped repeatedly. Close other tabs and retry."))
        } else e.load(tb.url)
    }
    val box = SwipeRefreshLayout(ctx)
    tb.engineView = box
    var judgedUrl: String? = null
    var judged = false
    box.apply {
        addView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setOnRefreshListener { e.reload() }
        setOnChildScrollUpCallback { _, _ ->
            if (e.scrollY > 0) true
            else {
                val u = tb.url
                if (u != judgedUrl) {
                    judgedUrl = u
                    judged = Pwa.kind(u) != SiteKind.NONE || WebCompat.noPullRefresh(u)
                }
                judged
            }
        }
        setDistanceToTriggerSync((120 * ctx.resources.displayMetrics.density).toInt())
        setSlingshotDistance((140 * ctx.resources.displayMetrics.density).toInt())
        setColorSchemeColors(primary)
        setProgressBackgroundColorSchemeColor(bg)
        useIosSpinner(spinner)
    }
    return box
}

fun snapEngine(t: BrowserTab, e: EngineTab) {
    if (t.url.isBlank()) return
    if (Adaptive.level >= 2 && t.thumb != null) return
    e.capture { bmp ->
        val k = 0.4f
        val w = (bmp.width * k).toInt().coerceAtLeast(1)
        val hh = (bmp.height * k).toInt().coerceAtLeast(1)
        t.thumb = android.graphics.Bitmap.createScaledBitmap(bmp, w, hh, true)
    }
}
