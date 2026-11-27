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


class GeckoBridge(private val tab: BrowserTab, private val h: Handlers) : EngineListener {
    private val ctx: Context get() = h.activity
    private val main = Handler(Looper.getMainLooper())

    override fun onUrl(tab: EngineTab, url: String) {
        if (url.startsWith("http")) {
            PageTint.onStart(this.tab, url)
            if (this.tab.url != url) this.tab.resetScroll()
            this.tab.url = url
            GoogleAccounts.guard(this.tab, tab, url)
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
            Shield.dialogReset(hostOf(this.tab.url).ifBlank { "-" })
            this.tab.clearFailedIfNew()
        } else {
            (this.tab.engineView as? SwipeRefreshLayout)?.isRefreshing = false
            if (!this.tab.popup && !this.tab.isPrivate && tab.url.startsWith("http")) Library.visit(tab.url, tab.title)
        }
    }

    override fun onScroll(tab: EngineTab, y: Int, dy: Int) {
        this.tab.noteScroll(y, dy)
    }

    override fun onNav(tab: EngineTab, canBack: Boolean, canForward: Boolean) {
        this.tab.canBack = canBack
        this.tab.canForward = canForward
    }

    override fun onLoadRequest(tab: EngineTab, uri: String, target: Int, gesture: Boolean, redirect: Boolean, direct: Boolean): Boolean {
        val u = Uri.parse(uri)
        if (NavPolicy.blocksPageNavigation(u.scheme, direct)) {
            Security.log(L("حظر"), (u.scheme ?: "") + ":", this.tab.isPrivate)
            return false
        }
        val current = target == GeckoSession.NavigationDelegate.TARGET_WINDOW_CURRENT
        return when (u.scheme) {
            "http", "https" -> {
                if (Shield.isSpoofed(u, this.tab.isPrivate)) {
                    toast(ctx, L("تم حظر رابط مخادع يُخفي وجهته الحقيقية"))
                    false
                } else {
                    if (NavPolicy.mayInjectHint(direct, current, this.tab.popup) && GoogleAccounts.isSignInUrl(u)) {
                        val hinted = GoogleAccounts.withHint(u)
                        if (hinted != u) {
                            main.post { tab.load(hinted.toString()) }
                            return false
                        }
                    }
                    val c = Security.cleanUrl(u, this.tab.isPrivate)
                    if (NavPolicy.mayRewrite(direct, current) && c != u) {
                        main.post { tab.load(c.toString()) }
                        false
                    } else true
                }
            }
            null, "about", "blob", "data", "resource", "moz-extension", "view-source" -> true
            "intent" -> {
                PageRules.intentTarget(uri)?.takeIf { !PageRules.samePage(tab.url, it) }?.let { f ->
                    val fu = Uri.parse(f)
                    if (!Shield.isSpoofed(fu, this.tab.isPrivate)) main.post { tab.load(Security.cleanUrl(fu, this.tab.isPrivate).toString()) }
                }
                false
            }
            "tel", "mailto", "sms", "geo" -> {
                if (gesture) runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, u)) }
                false
            }
            else -> {
                if (gesture) PageExtras.openExternal(ctx, u, this.tab.isPrivate)
                false
            }
        }
    }

    override fun onHttpsOnly(tab: EngineTab, uri: String) {
        Security.log("HTTPS", hostOf(uri), this.tab.isPrivate)
    }

    override fun onFind(tab: EngineTab, current: Int, total: Int) {
        this.tab.findInfo = if (total == 0) "0" else "$current/$total"
    }

    override fun onNewTab(tab: EngineTab, uri: String): EngineTab? {
        val t = h.newPopup(uri)
        if (t == null) {
            h.openTab(uri)
            return null
        }
        val sel = GeckoSelection(h.activity) { id, text -> SmartAssist.onSelection(h.activity, id, text, h.openTab) }
        return EngineTab(t.id, GeckoBridge(t, h), null, t.desktop, GeckoUi(h, { t.url }, { t.isPrivate }), sel, GeckoMedia(t), false).also { t.engine = it }
    }

    override fun onClose(tab: EngineTab) {
        main.post { h.closePopup(this.tab) }
    }

    override fun onDownload(tab: EngineTab, response: WebResponse) {
        fun header(name: String) = response.headers.entries.firstOrNull { it.key.equals(name, true) }?.value
        val cd = header("Content-Disposition")
        val mime = header("Content-Type")
        val uri = response.uri
        val ref = tab.url
        val body = response.body
        if (!Shield.downloadAllowed(hostOf(ref.ifBlank { uri }), this.tab.isPrivate)) {
            runCatching { body?.close() }
            toast(ctx, L("تم حجب تنزيلات تلقائية متكررة من هذه الصفحة"))
            return
        }
        val encoded = header("Content-Encoding")?.let { !it.equals("identity", true) } ?: false
        val length = if (encoded) -1L else header("Content-Length")?.toLongOrNull() ?: -1L
        val isPrivate = this.tab.isPrivate
        val save = { FileSaver.save(ctx, uri, body, cd, mime, length) }
        val web = uri.startsWith("http://", true) || uri.startsWith("https://", true)
        if (web && (Security.isRiskyFile(uri, cd) || Security.isRiskyName(Downloader.smartName(uri, cd, mime)))) main.post {
            Security.log(L("تنزيل"), L("تحذير ملف تنفيذي من ") + hostOf(uri), isPrivate)
            runCatching {
                novaDialog(ctx).setTitle(L("ملف قد يكون خطيراً"))
                    .setMessage(L("هذا النوع من الملفات (تطبيق/ملف تنفيذي) قد يضر بجهازك. نزّله فقط من مصدر تثق به.\n\n") + hostOf(uri))
                    .setPositiveButton(L("تنزيل")) { _, _ -> save() }
                    .setNegativeButton(L("إلغاء")) { _, _ -> runCatching { body?.close() } }
                    .setOnCancelListener { runCatching { body?.close() } }
                    .show()
            }.onFailure { runCatching { body?.close() } }
        } else save()
    }

    override fun onContextMenu(tab: EngineTab, element: GeckoSession.ContentDelegate.ContextElement) {
        val ref = this.tab.url
        val src = element.srcUri
        val link = element.linkUri
        val items = ArrayList<Pair<String, () -> Unit>>()
        fun add(label: String, act: () -> Unit) { items.add(label to act) }
        fun web(u: String?) = u != null && (u.startsWith("http://", true) || u.startsWith("https://", true))
        fun fetchable(u: String?) = web(u) || (u != null && u.startsWith("data:", true))
        when (element.type) {
            GeckoSession.ContentDelegate.ContextElement.TYPE_IMAGE -> if (src != null) {
                if (fetchable(src)) add(L("تنزيل الصورة")) { h.onDownload(src, null, null, null, ref, this.tab.isPrivate) }
                if (web(src)) {
                    add(L("فتح الصورة في تبويب جديد")) { h.openTab(src) }
                    add(L("نسخ رابط الصورة")) { copyText(ctx, src) }
                    add(L("مشاركة الصورة")) { shareText(ctx, src) }
                }
            }
            GeckoSession.ContentDelegate.ContextElement.TYPE_VIDEO, GeckoSession.ContentDelegate.ContextElement.TYPE_AUDIO -> if (web(src)) {
                add(L("تنزيل")) { h.onDownload(src!!, null, null, null, ref, this.tab.isPrivate) }
                add(L("نسخ الرابط")) { copyText(ctx, src!!) }
            }
        }
        if (web(link) && link != src) {
            add(L("فتح في تبويب جديد")) { h.openTab(link!!) }
            add(L("نسخ الرابط")) { copyText(ctx, link!!) }
            add(L("مشاركة الرابط")) { shareText(ctx, link!!) }
        }
        if (items.isEmpty()) return
        main.post { runCatching { choose(ctx, items) } }
    }

    override fun onCrash(tab: EngineTab) {
        val saved = this.tab.engine?.state
        main.post {
            h.onGeckoFullScreen(false)
            GeckoPage.forget(this.tab)
            this.tab.engine?.close()
            this.tab.engine = null
            this.tab.engineView = null
            this.tab.loading = false
            this.tab.holdLoad = !PageRules.rendererGone(this.tab)
            if (!this.tab.holdLoad) this.tab.engineState = saved
            this.tab.epoch++
        }
    }

    override fun onFullScreen(tab: EngineTab, on: Boolean) {
        h.onGeckoFullScreen(on)
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

fun geckoContainer(ctx: Context, tb: BrowserTab, h: Handlers, primary: Int): View {
    val view = NovaGeckoView(ctx)
    var fresh = false
    val e = tb.engine ?: run {
        fresh = true
        if (tb.engineState == null && tb.url.startsWith("http")) {
            val sd = SiteStore.get(hostOf(tb.url).removePrefix("www.")).desktop
            if (sd >= 0) tb.desktop = sd == 1
        }
        val st = tb.engineState
        tb.engineState = null
        EngineTab(tb.id, GeckoBridge(tb, h), st, tb.desktop, GeckoUi(h, { tb.url }, { tb.isPrivate }), GeckoSelection(h.activity) { id, text -> SmartAssist.onSelection(h.activity, id, text, h.openTab) }, GeckoMedia(tb), true, tb.isPrivate).also { tb.engine = it }
    }
    e.attach(view)
    if (fresh && !e.restored) {
        if (tb.holdLoad) {
            tb.holdLoad = false
            e.loadHtml(errorHtml(tb.url, L("تعطّلت عملية عرض الصفحة عدة مرات. أغلق تبويبات أخرى لتحرير الذاكرة ثم أعد المحاولة.")))
        } else e.load(tb.url)
    }
    val box = ArcRefreshLayout(ctx, primary)
    tb.engineView = box
    var judgedUrl: String? = null
    var judged = false
    box.apply {
        addView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setOnRefreshListener { e.reload() }
        setOnChildScrollUpCallback { _, _ ->
            if (view.pagePullBlocked() != false || e.scrollY > 0 || tb.noPull) true
            else {
                val u = tb.url
                if (u != judgedUrl) {
                    judgedUrl = u
                    judged = PageRules.noPullRefresh(u)
                }
                judged
            }
        }
    }
    return box
}

fun snapEngine(t: BrowserTab, e: EngineTab) {
    if (t.url.isBlank()) return
    if ((Adaptive.level >= 2 || t.loading) && t.thumb != null) return
    e.capture { bmp ->
        val k = 0.4f
        val w = (bmp.width * k).toInt().coerceAtLeast(1)
        val hh = (bmp.height * k).toInt().coerceAtLeast(1)
        Work.img {
            val small = android.graphics.Bitmap.createScaledBitmap(bmp, w, hh, true)
            small.prepareToDraw()
            Work.ui { Thumbs.put(t, small) }
        }
    }
}
