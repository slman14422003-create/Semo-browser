package com.nova.browser

import android.annotation.SuppressLint
import android.content.*
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.FrameLayout
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.text.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

/**
 * تسجيل دخول Google: إن اختار المستخدم Chrome Custom Tab يُفتح هناك بالحساب الأساسي،
 * وإلا نُكمل داخل الصفحة مع إضافة login_hint مرة واحدة (غيابه شرط، فلا تتكرر الحلقة). يعيد true إن عالجنا التنقّل.
 */
internal fun googleSignIn(v: WebView, h: Handlers, u: Uri, allowTab: Boolean = true): Boolean {
    val hinted = GoogleAccounts.withHint(u)
    if (allowTab && GoogleAccounts.useChrome && GoogleAccounts.openInCustomTab(h.activity, hinted)) {
        toast(h.activity, L("فُتح تسجيل دخول Google في Chrome — الجلسة هناك منفصلة عن Semo brawser"))
        return true
    }
    if (hinted != u) { v.loadUrl(hinted.toString(), Perf.privacyHeaders); return true }
    return false
}

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
fun createWebView(ctx: Context, tab: BrowserTab, h: Handlers): WebView = NovaWebView(ctx).apply {
    tab.yt = false
    onSelectionAction = { id, text -> SmartAssist.onSelection(ctx, id, text, h.openTab) }
    PageExtras.engineInUse()
    with(settings) {
        javaScriptEnabled = Prefs.js; domStorageEnabled = true; databaseEnabled = true
        mediaPlaybackRequiresUserGesture = false
        javaScriptCanOpenWindowsAutomatically = false   // لا نوافذ منبثقة بلا لمسة من المستخدم
        setSupportMultipleWindows(true)
        allowFileAccess = false
        setSupportZoom(true); builtInZoomControls = true; displayZoomControls = false
        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
    }
    tab.safari = SiteStore.globalSafari
    applyUa(this, tab.desktop, tab.safari)
    SafariMode.sync(this, tab.safari)
    ScriptBundle.begin(this)
    Perf.tune(this)
    Perf.installPrivacy(this)
    Shield.install(this)
    Perf.installSmooth(this)
    Perf.installCosmetic(this)
    WebSupport.configure(this)
    WebSupport.installScrollGuard(this)
    WebCompat.install(this)
    ScriptBundle.flush(this)
    Pwa.install(this)
    ScriptBundle.begin(this)
    PageBrain.install(this)
    Speculate.warmStart(this)
    PasswordBridge.install(this, tab, h)
    ScriptBundle.flush(this)
    importantForAutofill = if (Prefs.pwMode == 1) View.IMPORTANT_FOR_AUTOFILL_YES else View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
    CookieManager.getInstance().setAcceptCookie(true)
    CookieManager.getInstance().setAcceptThirdPartyCookies(this, !Prefs.blockThirdCookies)
    setDownloadListener { u, ua, cd, mime, _ -> h.onDownload(u, ua, cd, mime, this.url) }
    setFindListener { active, total, _ -> tab.findInfo = if (total == 0) "0" else "${active + 1}/$total" }
    setOnLongClickListener {
        val r = hitTestResult
        val ex = r.extra ?: return@setOnLongClickListener false
        when (r.type) {
            WebView.HitTestResult.SRC_ANCHOR_TYPE -> {
                Speculate.preconnect(this, ex)
                choose(ctx, listOf(L("فتح في تبويب جديد") to { h.openTab(ex) }, L("نسخ الرابط") to { copyText(ctx, ex) }, L("مشاركة الرابط") to { shareText(ctx, ex) }))
                true
            }
            WebView.HitTestResult.IMAGE_TYPE, WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                choose(ctx, listOf(L("تنزيل الصورة") to { h.onDownload(ex, settings.userAgentString, null, null, this.url) }, L("فتح الصورة في تبويب جديد") to { h.openTab(ex) }, L("نسخ رابط الصورة") to { copyText(ctx, ex) }, L("مشاركة الصورة") to { shareText(ctx, ex) }))
                true
            }
            else -> false
        }
    }
    webViewClient = object : WebViewClient() {
        override fun onPageStarted(v: WebView, u: String, f: Bitmap?) {
            if (YtWeb.isYtUrl(u)) { v.post { YtWeb.swapIfNeeded(tab, u) }; return }   // وصلنا ليوتيوب (تحويل من الخادم): يُكمل في الـ WebView المخصّص
            tab.clearFailedIfNew()
            tab.thirdOn = false
            SiteStore.apply(tab, v, hostOf(u))
            WebSupport.resetInnerScroll(v)
            WebBoost.onPageStart(v)
            PageBrain.onStart(v, u)
            PageTint.onStart(tab, u)
            tab.loading = true; tab.url = u; tab.shieldHost = Shield.hostFor(u); Shield.onPageStart(v); Perf.onPageStart(v); Pwa.onPageStart(v, u); if (WebCompat.loopTrip(v, tab, u)) return; WebCompat.onPageStart(v, tab, u); WebCompat.sorry(v, tab, h, u)
        }
        override fun doUpdateVisitedHistory(v: WebView, u: String, isReload: Boolean) {
            // تنقّلات الصفحات أحادية الصفحة لا تستدعي onPageStarted
            if (u.startsWith("http")) {
                tab.url = u; tab.shieldHost = Shield.hostFor(u); tab.canBack = v.canGoBack(); tab.canForward = v.canGoForward()
            }
        }
        override fun onPageFinished(v: WebView, u: String) {
            tab.loading = false; tab.url = u
            tab.canBack = v.canGoBack(); tab.canForward = v.canGoForward()
            (v.parent as? SwipeRefreshLayout)?.isRefreshing = false
            WebSupport.onPageDone(v)
            WebBoost.onPageDone(v)
            WebCompat.onPageDone(tab)
            Perf.flushCookies()   // حفظ جلسات تسجيل الدخول (بحدّ أقصى كل 15 ثانية)
            PasswordBridge.onPageDone(v)
            SiteRules.ensureFlag(v, hostOf(u))
            PageTint.sample(v, tab)
            if (!tab.popup) Library.visit(u, v.title)   // سجل التصفح
        }
        override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest): WebResourceResponse? {
            if (!r.isForMainFrame && !tab.thirdOn && SiteRules.authRequest(r.url)) {
                tab.thirdOn = true
                v.post { runCatching { CookieManager.getInstance().setAcceptThirdPartyCookies(v, true) } }
            }
            return Shield.intercept(tab.shieldHost, r.url, r.isForMainFrame) ?: Perf.intercept(r.url, r.isForMainFrame, tab.shieldHost)
        }
        override fun onRenderProcessGone(v: WebView, d: RenderProcessGoneDetail): Boolean {
            if (tab.popup) { Popups.close(v); return true }
            // منع انهيار التطبيق: نُسقط الـ WebView ونعيد إنشاءه بنفس العنوان
            (v.parent as? ViewGroup)?.removeView(v)
            runCatching { v.destroy() }
            tab.webView = null; tab.loading = false
            tab.holdLoad = !WebCompat.rendererGone(tab)   // 3 انهيارات خلال 90 ثانية: لا حلقة إعادة تحميل، صفحة بزر إعادة المحاولة
            tab.epoch++
            return true
        }
        override fun onPageCommitVisible(v: WebView, u: String) {
            PageTint.sample(v, tab)
            PageBrain.onCommit(v, u)
            if (Prefs.smoothAnim && Adaptive.level == 0 && v.alpha > 0.9f) {
                v.animate().cancel()
                v.alpha = 0.94f
                v.animate().alpha(1f).setDuration(140L).withLayer().setInterpolator(android.view.animation.PathInterpolator(0.32f, 0.72f, 0f, 1f)).start()
            }
        }
        override fun onReceivedHttpAuthRequest(v: WebView, handler: HttpAuthHandler, host: String?, realm: String?) {
            PageExtras.httpAuth(ctx, handler, host, realm)
        }
        override fun onReceivedSslError(v: WebView, h: SslErrorHandler, e: SslError) {
            val u = e.url ?: v.url ?: ""
            val host = hostOf(u)
            if (SiteRules.sslAllowed(host)) { h.proceed(); return }
            h.cancel()
            Security.log(L("شهادة"), (L("رُفض اتصال غير موثوق: ") + host))
            val main = u == v.url || u == tab.url
            if (!main) return
            v.loadDataWithBaseURL(u, errorHtml(u, L("شهادة أمان الموقع غير صالحة — تم حظر الاتصال لحمايتك")), "text/html", "UTF-8", u)
            if (!SiteRules.shouldAskSsl(host)) return
            runCatching {
                novaDialog(ctx).setTitle(host).setMessage(S(R.string.ssl_warn))
                    .setPositiveButton(S(R.string.ssl_proceed)) { _, _ -> SiteRules.allowSsl(host); v.loadUrl(u) }
                    .setNegativeButton(S(R.string.ssl_back), null).show()
            }
        }
        override fun onSafeBrowsingHit(v: WebView, r: WebResourceRequest, threatType: Int, cb: SafeBrowsingResponse) {
            cb.backToSafety(true)
            Security.log("Safe Browsing", (L("حُظر موقع خطير: ") + (r.url.host)))
            toast(ctx, L("تم حظر موقع خطير وإعادتك إلى صفحة آمنة"))
        }
        override fun onReceivedError(v: WebView, r: WebResourceRequest, e: WebResourceError) {
            if (r.isForMainFrame && tab.upgradedFrom != null && r.url.toString() == tab.upgradedTo) {
                // الموقع لا يدعم HTTPS: رجوع إلى http مرة واحدة
                val orig = tab.upgradedFrom!!
                tab.noUpgradeHost = Uri.parse(orig).host; tab.upgradedFrom = null; tab.upgradedTo = null
                v.loadUrl(orig, Perf.privacyHeaders); return
            }
            if (r.isForMainFrame) {
                val u = r.url.toString()
                if (WebSupport.tryCacheFallback(v, u)) return
                tab.markFailed()
                v.loadDataWithBaseURL(u, errorHtml(u, e.description.toString()), "text/html", "UTF-8", u)
            }
        }
        override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
            val u = r.url
            return when (u.scheme) {
                "http", "https" -> {
                    if (!r.isForMainFrame) false
                    else if (YtWeb.isYtUrl(u.toString())) { v.post { YtWeb.swapIfNeeded(tab, u.toString()) }; true }   // يوتيوب له WebView مخصّص منفصل
                    else if (Shield.isSpoofed(u)) { toast(ctx, L("تم حظر رابط مخادع يُخفي وجهته الحقيقية")); true }
                    else if (!Shield.navAllowed(v, r.hasGesture(), u.host)) { toast(ctx, L("تم إيقاف تحويلات تلقائية متكررة")); true }
                    else if (GoogleAccounts.isSignInUrl(u) && googleSignIn(v, h, u, !tab.popup)) true
                    else {
                        var t = Security.cleanUrl(u)
                        val host = t.host
                        if (Prefs.httpsFirst && t.scheme == "http" && host != null && host != tab.noUpgradeHost) {
                            val last = tab.lastUpgrade; val now = System.currentTimeMillis()
                            if (last != null && last.first == host && now - last.second < 5000) tab.noUpgradeHost = host   // حلقة تحويل
                            else {
                                tab.upgradedFrom = t.toString(); t = t.buildUpon().scheme("https").build()
                                tab.upgradedTo = t.toString(); tab.lastUpgrade = host to now
                                Security.log("HTTPS", (L("رُقّي ") + host))
                            }
                        }
                        val relang = Prefs.siteLangCode().isNotEmpty() && r.hasGesture() && r.method == "GET" && !r.isRedirect
                        if (t != u || relang) { v.loadUrl(t.toString(), Perf.privacyHeaders); true } else false
                    }
                }
                null, "about", "blob" -> false
                "data" -> r.isForMainFrame
                "intent" -> {
                    // الوجهة http(s) فقط (رابط بديل أو بيانات الرابط نفسه): كان الرابط يُبتلع فتبقى الخريطة معلّقة
                    // إن كانت الوجهة هي الصفحة الحالية نفسها (زر «افتح في التطبيق» في الخرائط) نتجاهلها: تحميلها ثانية يصنع حلقة تحديث
                    WebCompat.intentTarget(u.toString())?.takeIf { !WebCompat.samePage(v.url, it) }?.let { f ->
                        val fu = Uri.parse(f)
                        if (!Shield.isSpoofed(fu)) v.loadUrl(Security.cleanUrl(fu).toString(), Perf.privacyHeaders)
                    }
                    true
                }
                "tel", "mailto", "sms", "geo" -> {
                    if (r.hasGesture()) runCatching { v.context.startActivity(Intent(Intent.ACTION_VIEW, u)) }
                    true
                }
                else -> {
                    if (r.hasGesture() && r.isForMainFrame) PageExtras.openExternal(ctx, u)
                    true
                }
            }
        }
    }
    webChromeClient = object : WebChromeClient() {
        override fun onProgressChanged(v: WebView, p: Int) {
            val f = p / 100f   // نحدّث الحالة كل 5% فقط لتقليل إعادة التركيب
            if (p == 0 || p == 100 || kotlin.math.abs(f - tab.progress) >= 0.05f) tab.progress = f
            WebBoost.onProgress(v, p)
            WebCompat.onProgress(tab, p)   // اكتمال التقدّم = انتهاء التحميل حتى لو تأخّر onPageFinished
        }
        override fun onReceivedIcon(v: WebView, icon: Bitmap?) {
            val u = v.url ?: return
            if (icon != null && u.startsWith("http")) Favicons.put(hostOf(u), icon)
        }
        override fun onReceivedTitle(v: WebView, t: String?) {
            if (!t.isNullOrBlank()) tab.title = t
            WebCompat.challengeTitle(v, tab, t)
        }
        override fun onCreateWindow(v: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean =
            Popups.create(h, tab, isDialog, isUserGesture, resultMsg)
        override fun onCloseWindow(window: WebView) { Popups.close(window) }
        override fun onJsBeforeUnload(v: WebView, url: String, message: String?, r: JsResult): Boolean {
            PageExtras.beforeUnload(ctx, url, r)
            return true
        }
        override fun onShowCustomView(view: View, cb: CustomViewCallback) = h.showCustom(view, cb)
        override fun getDefaultVideoPoster(): Bitmap? = WebCompat.videoPoster()   // null يُسقط بعض إصدارات المحرك عند عرض الفيديو
        override fun onHideCustomView() = h.hideCustom()
        override fun onShowFileChooser(v: WebView, cb: ValueCallback<Array<Uri>>, p: FileChooserParams) = h.chooser(cb, p)
        override fun onPermissionRequest(req: PermissionRequest) = h.permission(req)
        override fun onGeolocationPermissionsShowPrompt(origin: String, cb: GeolocationPermissions.Callback) = h.geo(origin, cb)
        override fun onJsAlert(v: WebView, url: String, msg: String, r: JsResult): Boolean {
            if (!Shield.dialogAllowed(hostOf(url))) { r.cancel(); return true }   // إغراق نوافذ
            novaDialog(ctx).setTitle(hostOf(url)).setMessage(msg.take(600)).setPositiveButton(L("حسناً")) { _, _ -> r.confirm() }
                .setOnCancelListener { r.cancel() }.show()
            return true
        }
        override fun onJsConfirm(v: WebView, url: String, msg: String, r: JsResult): Boolean {
            if (!Shield.dialogAllowed(hostOf(url))) { r.cancel(); return true }
            novaDialog(ctx).setTitle(hostOf(url)).setMessage(msg.take(600)).setPositiveButton(L("موافق")) { _, _ -> r.confirm() }
                .setNegativeButton(L("إلغاء")) { _, _ -> r.cancel() }.setOnCancelListener { r.cancel() }.show()
            return true
        }
        override fun onJsPrompt(v: WebView, url: String, message: String?, defaultValue: String?, r: JsPromptResult): Boolean {
            if (!Shield.dialogAllowed(hostOf(url))) { r.cancel(); return true }
            val et = android.widget.EditText(ctx).apply { setText(defaultValue ?: ""); setSingleLine(); setSelectAllOnFocus(true) }
            val box = android.widget.FrameLayout(ctx).apply {
                val pad = (24 * ctx.resources.displayMetrics.density).toInt()
                setPadding(pad, (8 * ctx.resources.displayMetrics.density).toInt(), pad, 0); addView(et)
            }
            novaDialog(ctx).setTitle(hostOf(url)).setMessage(message?.take(600)).setView(box)
                .setPositiveButton(L("موافق")) { _, _ -> r.confirm(et.text.toString()) }
                .setNegativeButton(L("إلغاء")) { _, _ -> r.cancel() }.setOnCancelListener { r.cancel() }.show()
            return true
        }
    }
}
