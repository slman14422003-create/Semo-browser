package com.nova.browser

import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.runtime.mutableIntStateOf
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.webkit.WebViewRenderProcess
import androidx.webkit.WebViewRenderProcessClient
import androidx.webkit.WebViewStartUpConfig
import java.net.InetAddress
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * أدوات دعم الـ WebView: كل ما يخص صحّة المحرك وتسريع التنقل وأدوات الصفحة (قراءة/تمرير/تحديث كامل).
 * كل استدعاء لميزة في androidx.webkit محمي بفحص الدعم، فلا ينهار التطبيق على محركات قديمة.
 */
object WebSupport {
    private val main = Handler(Looper.getMainLooper())
    private val attached: MutableSet<WebView> = Collections.newSetFromMap(WeakHashMap<WebView, Boolean>())
    private val patient: MutableSet<WebView> = Collections.newSetFromMap(WeakHashMap<WebView, Boolean>())
    /** صفحات التحقق والعمليات الحسابية الطويلة: إمهال أكبر قبل اعتبار الصفحة معلّقة. */
    fun patient(v: WebView, on: Boolean) { if (on) patient.add(v) else patient.remove(v) }
    private val hard: MutableSet<WebView> = Collections.newSetFromMap(WeakHashMap<WebView, Boolean>())
    private val stuck = WeakHashMap<WebView, Runnable>()
    @Volatile var online = true; private set
    private var registered = false

    private val readerJs: String? by lazy { runCatching { Perf.asset("reader.js") }.getOrNull() }

    @Volatile private var warmed0 = false

    /** Starts WebView engine initialisation on a background lane so the first page does not block the UI thread. */
    @Suppress("UnsafeOptInUsageError")
    fun warmStart(c: Context) {
        if (warmed0) return
        warmed0 = true
        runCatching {
            val cfg = WebViewStartUpConfig.Builder(Work.cpuPool).build()
            WebViewCompat.startUpWebView(c.applicationContext, cfg) { }
        }
    }

    // ---------- تهيئة عامة ----------
    fun init(c: Context) {
        val app = c.applicationContext
        WebBoost.init(app)
        // تصحيح الصفحات عن بُعد (chrome://inspect) في نسخة debug فقط
        runCatching { WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG) }
        if (registered) return
        registered = true
        val cm = app.getSystemService(ConnectivityManager::class.java) ?: return
        runCatching {
            online = cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ?: true
            // مراقبة الشبكة: navigator.onLine صحيح داخل الصفحات + إعادة تحميل صفحات الخطأ تلقائياً عند عودة الاتصال
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { main.post { setOnline(true) } }
                override fun onLost(network: Network) { main.post { setOnline(false) } }
            })
        }
    }

    private fun setOnline(v: Boolean) {
        val was = online
        online = v
        attached.toList().forEach { runCatching { it.setNetworkAvailable(v) } }
        if (v && !was) retryFailed()
    }

    /** يضغط زر «إعادة المحاولة» في صفحات الخطأ المعروضة حالياً (علامتها data-nova-err). */
    private fun retryFailed() {
        val js = "(function(){var b=document.querySelector('[data-nova-err] button');if(b){b.click();return 1}return 0})()"
        attached.toList().forEach { runCatching { it.evaluateJavascript(js, null) } }
    }

    // ---------- إعداد كل WebView جديد ----------
    fun configure(wv: WebView) = androidx.tracing.trace("Nova.configure") {
        attached.add(wv)
        runCatching { wv.setNetworkAvailable(online) }
        // المحرك يخفّض أولوية عملية العرض للتبويبات غير الظاهرة (توفير ذاكرة وحرارة)
        runCatching { wv.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, true) }
        // إزالة ترويسة X-Requested-With (تكشف اسم الحزمة وتجعل بعض المواقع تعامل التطبيق كمتصفح مضمَّن)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST))
            runCatching { WebSettingsCompat.setRequestedWithHeaderOriginAllowList(wv.settings, emptySet()) }
        watchRenderer(wv)
        tuneEngine(wv)
        runCatching {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_BASIC_USAGE)) {
                val sw = androidx.webkit.ServiceWorkerControllerCompat.getInstance().serviceWorkerWebSettings
                sw.setAllowContentAccess(false); sw.setAllowFileAccess(false); sw.setBlockNetworkLoads(false)
                sw.setCacheMode(WebSettings.LOAD_DEFAULT)
            }
        }
    }

    private fun tuneEngine(wv: WebView) {
        val st = wv.settings
        runCatching {
            st.useWideViewPort = true
            st.loadWithOverviewMode = false
            st.layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
        }
        wv.overScrollMode = android.view.View.OVER_SCROLL_NEVER
        wv.isNestedScrollingEnabled = true
    }

    // ---------- حارس التمرير الداخلي (يمنع السحب للتحديث من خطف التمرير) ----------
    // WebView داخل SwipeRefreshLayout لا يرى إلا scrollY للصفحة؛ الصفحات التي تتمرّر داخل عنصر (قائمة، دردشة، body بتمرير خاص…)
    // كانت تُلتقط سحباتها كسحب للتحديث فيتوقف التمرير. scrollguard.js يخبرنا عند بدء كل لمسة إن كان تحت الإصبع عنصر متمرّر لأسفل.
    // الكتابة والقراءة على الخيط الرئيسي (مستمع الرسائل يُستدعى عليه).
    private val innerScroll: MutableSet<WebView> = Collections.newSetFromMap(WeakHashMap<WebView, Boolean>())
    private val scrollGuardJs: String? by lazy { runCatching { Perf.asset("scrollguard.js") }.getOrNull() }

    /** هل اللمسة الحالية فوق عنصر داخل الصفحة تمرّر لأسفل؟ إن كان كذلك يُعطَّل السحب للتحديث لهذه اللمسة. */
    fun innerScrolled(wv: WebView): Boolean = innerScroll.contains(wv)

    private val cacheTried = WeakHashMap<WebView, String>()

    /** خطأ شبكة في الصفحة الرئيسية: نجرّب النسخة المخزّنة مرة واحدة لكل رابط قبل عرض صفحة الخطأ. */
    fun tryCacheFallback(wv: WebView, url: String): Boolean {
        if (!url.startsWith("http") || cacheTried[wv] == url) return false
        cacheTried[wv] = url
        cacheFirst(wv)
        wv.loadUrl(url)
        return true
    }

    /** الرجوع/التقدّم من الكاش مباشرة (فوري) مع عودة الإعداد الافتراضي بعد اكتمال الصفحة. */
    private fun restoreCacheSoon(wv: WebView) {
        wv.postDelayed({ if (hard.remove(wv)) runCatching { wv.settings.cacheMode = WebSettings.LOAD_DEFAULT } }, 4000)
    }
    fun goBackFast(wv: WebView) { cacheFirst(wv); wv.goBack(); restoreCacheSoon(wv) }
    fun goForwardFast(wv: WebView) { cacheFirst(wv); wv.goForward(); restoreCacheSoon(wv) }

    /** يُستدعى مع بدء كل صفحة جديدة: لا تبقى حالة من الصفحة السابقة. */
    fun resetInnerScroll(wv: WebView) { innerScroll.remove(wv) }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    fun installScrollGuard(wv: WebView) {
        // بداية كل لمسة تمسح الحالة قبل أن تصل رسالة الصفحة، وتأخذ الصفحة تركيز النظام كي يظهر الكيبورد لخانات الإدخال دائماً
        wv.isFocusable = true; wv.isFocusableInTouchMode = true
        wv.setOnTouchListener { v, e ->
            if (e.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
                innerScroll.remove(wv)
                if (!v.hasFocus()) v.requestFocus()
            }
            false
        }
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) ||
            !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
        val js = scrollGuardJs ?: return
        runCatching {
            WebViewCompat.addWebMessageListener(wv, "NovaScroll", setOf("*"), WebViewCompat.WebMessageListener { view, message, _, _, _ ->
                if (message.data == "1") innerScroll.add(view) else innerScroll.remove(view)
            })
            WebViewCompat.addDocumentStartJavaScript(wv, js, setOf("*"))
        }
    }

    /** إن توقفت عملية العرض عن الاستجابة أكثر من 10 ثوانٍ نُنهيها فيُعيد التطبيق إنشاء التبويب (بدل تجمّد الصفحة). */
    private fun watchRenderer(wv: WebView) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_VIEW_RENDERER_CLIENT_BASIC_USAGE)) return
        runCatching {
            WebViewCompat.setWebViewRenderProcessClient(wv, Executor { r -> main.post(r) }, object : WebViewRenderProcessClient() {
                override fun onRenderProcessUnresponsive(view: WebView, renderer: WebViewRenderProcess?) {
                    if (stuck.containsKey(view)) return
                    val kill = Runnable {
                        stuck.remove(view)
                        if (renderer != null && WebViewFeature.isFeatureSupported(WebViewFeature.WEB_VIEW_RENDERER_TERMINATE)) {
                            toast(view.context, L("الصفحة لا تستجيب — جارٍ إعادة تحميلها"))
                            runCatching { renderer.terminate() }
                        }
                    }
                    stuck[view] = kill
                    // صفحات الأكواد الضخمة وتحدّي التحقق قد تشغل الخيط عشرات الثواني: لا نقتلها بعد 10ث كما كان
                    main.postDelayed({ if (stuck.containsKey(view)) toast(view.context, L("الصفحة ثقيلة… يرجى الانتظار")) }, 8_000)
                    main.postDelayed(kill, if (patient.contains(view)) 60_000L else 30_000L)
                }
                override fun onRenderProcessResponsive(view: WebView, renderer: WebViewRenderProcess?) {
                    stuck.remove(view)?.let { main.removeCallbacks(it) }
                }
            })
        }
    }

    // ---------- كاش الصفحات ----------
    /** تحميل واحد يفضّل الكاش (حتى القديم) ثم يعود للوضع الافتراضي عند انتهاء الصفحة (onPageDone). */
    fun cacheFirst(wv: WebView) {
        runCatching { wv.settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK }
        hard.add(wv)
    }

    /** يُستدعى قبل كل تحميل يبدؤه التطبيق: بلا اتصال تُفتح الصفحة من الكاش بدل صفحة خطأ فورية. */
    fun prepareLoad(wv: WebView) { if (!online) cacheFirst(wv) }

    // ---------- تسريع التنقل ----------
    // الجلب المسبق وإيقاف الفيديو الصامت صارا في smooth.js (Perf.installSmooth)؛ هنا تسخين DNS والتحديث الكامل فقط
    fun onPageDone(wv: WebView) {
        // التحديث الكامل يعطّل الكاش لتحميل واحد فقط ثم يعود الوضع الافتراضي
        cacheTried.remove(wv)
        if (hard.remove(wv)) runCatching { wv.settings.cacheMode = WebSettings.LOAD_DEFAULT }
    }

    private val warmed = ConcurrentHashMap<String, Long>()

    /** يحلّ اسم النطاق مسبقاً في خيط خلفي (يملأ كاش DNS للنظام) كي يبدأ الاتصال أسرع عند الفتح. */
    fun prefetchDns(host: String?) {
        if (!Prefs.boost) return
        val h = host?.trim()?.lowercase()?.takeIf { it.length in 3..100 && it.contains('.') && !it.contains(' ') } ?: return
        val now = SystemClock.elapsedRealtime()
        val last = warmed[h]
        if (last != null && now - last < 120_000) return
        if (warmed.size > 200) warmed.clear()
        warmed[h] = now
        Work.dns { runCatching { InetAddress.getAllByName(h) } }
    }

    // ---------- أدوات الصفحة ----------
    fun hardReload(wv: WebView) {
        runCatching { wv.settings.cacheMode = WebSettings.LOAD_NO_CACHE }
        hard.add(wv)
        wv.reload()
    }

    /** يجلب أحدث تصميم للصفحة: يلغي service workers ومخازن الكاش للموقع ويمسح كاش المتصفح ثم يعيد التحميل دون كاش. */
    fun refreshDesign(wv: WebView) {
        val js = "(async function(){try{if(navigator.serviceWorker){var r=await navigator.serviceWorker.getRegistrations();for(var i=0;i<r.length;i++){await r[i].unregister()}}" +
            "if(window.caches){var k=await caches.keys();for(var j=0;j<k.length;j++){await caches.delete(k[j])}}}catch(e){}return 'ok'})()"
        runCatching { wv.evaluateJavascript(js, null) }
        runCatching { wv.clearCache(true) }
        toast(wv.context, L("جارٍ جلب أحدث تصميم للصفحة…"))
        wv.postDelayed({ hardReload(wv) }, 400)
    }

    /** بعد ظهور الكيبورد: يُظهر خانة الإدخال المحدَّدة في منتصف الصفحة المرئية كي لا يغطيها الكيبورد. */
    fun revealFocused(wv: WebView) {
        val js = "(function(){function deep(d){var e=d.activeElement;while(e&&e.shadowRoot&&e.shadowRoot.activeElement)e=e.shadowRoot.activeElement;" +
            "if(e&&e.tagName==='IFRAME'){try{var n=deep(e.contentDocument);if(n)return n}catch(x){}}return e}" +
            "var e=deep(document);if(!e||e===document.body||e===document.documentElement)return;" +
            "var t=(e.tagName||'').toLowerCase();if(!(t==='input'||t==='textarea'||t==='select'||e.isContentEditable||e.getAttribute('role')==='textbox'))return;" +
            "var vv=window.visualViewport,r=e.getBoundingClientRect(),h=vv?vv.height:window.innerHeight;" +
            "if(r.top<0||r.bottom>h*0.85)e.scrollIntoView({block:'center',behavior:'auto',inline:'nearest'})})()"
        wv.evaluateJavascript(js, null)
        wv.postDelayed({ if (wv.isAttachedToWindow) wv.evaluateJavascript(js, null) }, 260)
    }

    fun scroll(wv: WebView, top: Boolean) {
        val js = if (top) "window.scrollTo({top:0,behavior:'smooth'})"
        else "window.scrollTo({top:Math.max(document.body?document.body.scrollHeight:0,document.documentElement.scrollHeight),behavior:'smooth'})"
        wv.evaluateJavascript(js, null)
    }

    // ---------- أدوات إضافية للصفحة (من قائمة التطبيق) ----------
    private const val FLOAT_JS = "(function(){var h=document.querySelectorAll('[data-nova-hid]');if(h.length){for(var i=0;i<h.length;i++){h[i].style.removeProperty('display');h[i].removeAttribute('data-nova-hid')}return 'r'+h.length}" +
        "var all=document.body?document.body.querySelectorAll('*'):[],n=0,max=Math.min(all.length,4000);" +
        "for(var j=0;j<max;j++){var e=all[j],p=getComputedStyle(e).position;if((p==='fixed'||p==='sticky')&&e.offsetHeight>0){e.setAttribute('data-nova-hid','1');e.style.setProperty('display','none','important');n++}}return 'h'+n})()"

    /** إخفاء/إظهار العناصر العائمة والملصقة (شرائط وإعلانات عائمة تحجب المحتوى). */
    fun toggleFloating(wv: WebView) {
        wv.evaluateJavascript(FLOAT_JS) { r ->
            val v = r?.trim('"') ?: return@evaluateJavascript
            val n = v.drop(1).toIntOrNull() ?: 0
            toast(wv.context, if (v.startsWith("h")) (if (n > 0) L("أُخفيت العناصر العائمة") else L("لا عناصر عائمة في الصفحة")) else L("أُعيدت العناصر العائمة"))
        }
    }

    /** تبديل تحميل الصور في هذه الصفحة فوراً. */
    fun toggleImages(wv: WebView) {
        val block = !wv.settings.blockNetworkImage
        wv.settings.blockNetworkImage = block
        wv.settings.loadsImagesAutomatically = !block
        toast(wv.context, if (block) L("أُوقف تحميل الصور في هذا التبويب") else L("عاد تحميل الصور"))
    }

    /** إبقاء الشاشة مضاءة أثناء القراءة (لهذا التبويب). */
    fun toggleKeepOn(wv: WebView) {
        wv.keepScreenOn = !wv.keepScreenOn
        toast(wv.context, if (wv.keepScreenOn) L("الشاشة تبقى مضاءة") else L("عاد انطفاء الشاشة الطبيعي"))
    }

    /** نسخ نص الصفحة الظاهر كاملاً إلى الحافظة. */
    fun copyPageText(wv: WebView) {
        wv.evaluateJavascript("(function(){var t=(document.body&&document.body.innerText)||'';return t.slice(0,300000)})()") { r ->
            val txt = runCatching { org.json.JSONTokener(r ?: "").nextValue() as? String }.getOrNull().orEmpty()
            if (txt.isBlank()) toast(wv.context, L("لا نص في الصفحة")) else { copyText(wv.context, txt); }
        }
    }

    /** وضع القراءة: يعرض نص المقالة فقط فوق الصفحة (إعادة الضغط تُغلقه). */
    fun reader(wv: WebView) {
        val js = readerJs ?: return
        wv.evaluateJavascript(js) { r ->
            if (r != null && r.contains("none")) toast(wv.context, L("لا توجد مقالة يمكن عرضها في وضع القراءة"))
        }
    }
}

/** أيقونات المواقع (favicon) التي تصل من الصفحات نفسها: لا طلبات شبكة إضافية ولا تسريب للنطاقات. */
object Favicons {
    private val cache = android.util.LruCache<String, Bitmap>(64)
    /** يتغيّر عند وصول أيقونة جديدة ليُعاد رسم الواجهات التي تعرضها. */
    var version by mutableIntStateOf(0); private set

    fun get(host: String): Bitmap? = cache.get(host)

    fun put(host: String, icon: Bitmap) {
        if (host.isBlank() || icon.width <= 0 || icon.height <= 0 || cache.get(host) != null) return
        val b = runCatching { Bitmap.createScaledBitmap(icon, 48, 48, true) }.getOrNull() ?: return
        cache.put(host, b)
        version++
    }
}
