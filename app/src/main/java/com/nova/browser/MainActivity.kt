package com.nova.browser

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.graphics.luminance
import androidx.compose.animation.animateColorAsState
import android.Manifest
import android.app.PendingIntent
import android.content.*
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.FrameLayout
import kotlinx.coroutines.flow.debounce
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.app.ActivityCompat
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.ui.draw.alpha
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class MainActivity : ComponentActivity() {
    private var dlTrigger by mutableIntStateOf(0)
    /** رابط وصل من تطبيق آخر والتطبيق شغّال: (رقم تسلسلي, عنوان). الرقم يجعل نفس الرابط يُفتح كل مرة. */
    private var incoming by mutableStateOf<Pair<Int, String>?>(null)
    private var incomingSeq = 0
    @Volatile private var ready = false

    companion object {
        private var cleanedThisProcess = false
        private var lastCacheCheck = 0L
        /** صحيح بعد تهيئة الإعدادات والمخازن في هذه العملية (نافذة اللوحات تعتمد عليها). */
        @Volatile var initialized = false
    }

    // ---- نافذة منبثقة (Picture-in-Picture) ----
    var inPip by mutableStateOf(false)
    var pipAuto = false
    var fullscreenActive = false
    var wvProvider: () -> WebView? = { null }

    private val PIP_PLAY = "nova.pip.play"
    private val PIP_PAUSE = "nova.pip.pause"
    private val PIP_BACK = "nova.pip.back"
    private val PIP_FWD = "nova.pip.fwd"
    private var pipExitAt = 0L          // لحظة الخروج من المنبثقة (لاكتشاف الإغلاق بزر ✕ حتى لو تأخر onStop)
    private var pipPlayingShown: Boolean? = null
    private var pipSizeShown = 0L
    private var pipReceiverOn = false

    /** أزرار التحكم داخل النافذة المنبثقة نفسها: رجوع/تشغيل-إيقاف/تقديم (لم تكن موجودة). */
    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            when (i?.action) {
                PIP_PLAY -> YtMedia.control("play")
                PIP_PAUSE -> YtMedia.control("pause")
                PIP_BACK -> YtMedia.control("back")
                PIP_FWD -> YtMedia.control("fwd")
            }
            window.decorView.postDelayed({ refreshPip() }, 350)   // تبديل أيقونة التشغيل/الإيقاف بعد استجابة الصفحة
        }
    }

    private fun pipAction(icon: Int, label: String, act: String, code: Int): android.app.RemoteAction {
        val pi = PendingIntent.getBroadcast(this, code, Intent(act).setPackage(packageName), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return android.app.RemoteAction(android.graphics.drawable.Icon.createWithResource(this, icon), label, label, pi)
    }

    /** نسبة النافذة من أبعاد الفيديو الفعلية (16:9 افتراضياً)، ضمن الحدود التي يقبلها أندرويد (2.39:1 إلى 1:2.39). */
    private fun pipRatio(): android.util.Rational {
        val w = YtMedia.vw; val h = YtMedia.vh
        if (w <= 0 || h <= 0 || YtMedia.owner == null) return android.util.Rational(16, 9)
        val r = w.toFloat() / h
        return when {
            r > 2.39f -> android.util.Rational(239, 100)
            r < 0.42f -> android.util.Rational(42, 100)
            else -> android.util.Rational(w, h)
        }
    }

    private fun pipParams(): android.app.PictureInPictureParams {
        val hasVideo = YtMedia.owner != null
        val ratio = pipRatio()
        pipSizeShown = YtMedia.vw * 100000L + YtMedia.vh
        val b = android.app.PictureInPictureParams.Builder().setAspectRatio(ratio)
        if (hasVideo) {
            val playing = YtMedia.playing
            pipPlayingShown = playing
            b.setActions(listOf(
                pipAction(android.R.drawable.ic_media_rew, L("رجوع 10 ثوانٍ"), PIP_BACK, 21),
                if (playing) pipAction(android.R.drawable.ic_media_pause, L("إيقاف مؤقت"), PIP_PAUSE, 22)
                else pipAction(android.R.drawable.ic_media_play, L("تشغيل"), PIP_PLAY, 23),
                pipAction(android.R.drawable.ic_media_ff, L("تقديم 10 ثوانٍ"), PIP_FWD, 24)
            ))
        }
        b.setAutoEnterEnabled(pipAuto).setSeamlessResizeEnabled(false)
        if (hasVideo) b.setTitle(YtMedia.title.ifBlank { null }).setSubtitle(YtMedia.artist.ifBlank { null })
        // انتقال أنعم: نحدّد مكان المشغّل في الصفحة ليتحوّل منه إطار المنبثقة
        if (hasVideo && !fullscreenActive) runCatching {
            val w = wvProvider()
            val r = android.graphics.Rect()
            if (w != null && w.getGlobalVisibleRect(r) && r.width() > 0) {
                val hh = minOf(r.width() * ratio.denominator / ratio.numerator, r.height())
                if (hh > 0) b.setSourceRectHint(android.graphics.Rect(r.left, r.top, r.right, r.top + hh))
            }
        }
        return b.build()
    }

    fun refreshPip() { runCatching { setPictureInPictureParams(pipParams()) } }

    /** تُستدعى عند كل حالة تشغيل جديدة: نحدّث أزرار المنبثقة فقط عندما يتغيّر تشغيل/إيقاف. */
    fun onYtPlayState() { if (pipPlayingShown != YtMedia.playing || pipSizeShown != YtMedia.vw * 100000L + YtMedia.vh) refreshPip() }

    override fun onStart() {
        super.onStart()
        if (!pipReceiverOn) {
            val f = IntentFilter().apply { addAction(PIP_PLAY); addAction(PIP_PAUSE); addAction(PIP_BACK); addAction(PIP_FWD) }
            pipReceiverOn = runCatching { ContextCompat.registerReceiver(this, pipReceiver, f, ContextCompat.RECEIVER_NOT_EXPORTED) }.isSuccess
        }
    }

    fun enterPip() {
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) { toast(this, L("الجهاز لا يدعم النافذة المنبثقة")); return }
        val ok = runCatching { enterPictureInPictureMode(pipParams()) }.getOrDefault(false)
        if (!ok) {
            YtLog.add("enterPip failed")
            toast(this, L("تعذّر فتح النافذة المنبثقة — فعّلها للتطبيق من إعدادات النظام"))
        }
    }

    // أثناء أي إيقاف مؤقت للـ Activity (زر الرئيسية/المنبثق) نعلم الصفحة أنها في الخلفية قبل أن يتصرف يوتيوب
    override fun onPause() {
        super.onPause()
        if (Prefs.ytBg && YtMedia.owner != null) {
            YtLog.add("native onPause")
            YtWeb.background(wvProvider(), true)
        }
    }

    override fun onResume() {
        super.onResume()
        DefaultBrowser.refresh(this)   // قد يغيّر المستخدم الافتراضي من إعدادات النظام
        pipExitAt = 0L
        inPip = isInPictureInPictureMode   // لا نترك الحالة عالقة إن فاتنا إشعار الخروج من المنبثقة
        YtWeb.pip = inPip
        if (!inPip) { val w = wvProvider(); YtWeb.background(w, false); if (YtWeb.owns(w)) YtWeb.recover(w) }   // تنظيف أنماط المنبثقة إن بقيت
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        window.decorView.let { d -> d.post { androidx.core.view.ViewCompat.requestApplyInsets(d); d.requestLayout() } }
        val w = wvProvider()
        // الدخول للمنبثقة يطلق تغيير الإعدادات قبل onPictureInPictureModeChanged: لا نعتبره دوراناً (كان يعيد قياس الفيديو ويُفسد المنبثقة)
        val pipNow = inPip || isInPictureInPictureMode
        if (pipNow) YtWeb.pip = true
        if (YtWeb.owns(w)) {
            YtLog.add("config orientation=" + newConfig.orientation + " fullscreen=" + fullscreenActive + " pip=" + pipNow)
            if (!pipNow) YtWeb.afterRotate(w)
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
        YtWeb.pip = isInPictureInPictureMode
        pipExitAt = if (isInPictureInPictureMode) 0L else android.os.SystemClock.elapsedRealtime()
        YtLog.add("native pip=$isInPictureInPictureMode fullscreen=$fullscreenActive")
        if (isInPictureInPictureMode) refreshPip()
        // الخروج من المنبثقة بلا عودة للواجهة = أُغلقت بزر ✕ (بعض الأجهزة لا تستدعي onStop فوراً): نتحقق بعد لحظة
        if (!isInPictureInPictureMode) window.decorView.postDelayed({
            if (!isFinishing && !lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) onPipDismissed()
            else if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) pipExitAt = 0L   // عاد المستخدم للواجهة: لا يُعدّ أي إيقاف لاحق إغلاقاً للمنبثقة
        }, 500)
        val w = wvProvider() ?: return
        if (YtWeb.owns(w)) YtWeb.onPip(w, isInPictureInPictureMode, fullscreenActive)
        else {
            if (isInPictureInPictureMode) { w.resumeTimers(); w.onResume() }
            else { w.postDelayed({ w.requestLayout(); w.invalidate() }, 150) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        // نتولى حركة الخروج بأنفسنا بدل الاعتماد على سلوك المكتبة الافتراضي (كان يُسقط التطبيق أحياناً على بعض الأجهزة
        // بخطأ NullPointerException داخلي عند مزامنة حركة الأيقونة). provider.view مضمونة غير فارغة دوماً هنا.
        splash.setOnExitAnimationListener { provider ->
            runCatching {
                provider.view.animate().alpha(0f).setDuration(180L)
                    .withEndAction { runCatching { provider.remove() } }
                    .start()
            }.onFailure { runCatching { provider.remove() } }
        }
        super.onCreate(savedInstanceState)
        CrashLog.install(this)
        Robust.init(this)
        if (BuildConfig.DEBUG) runCatching {
            // JankStats: يسجّل الإطارات التي تتجاوز ميزانيتها (تقطيع) في Logcat بالوسم NovaJank
            androidx.metrics.performance.JankStats.createAndTrack(window, object : androidx.metrics.performance.JankStats.OnFrameListener {
                override fun onFrame(volatileFrameData: androidx.metrics.performance.FrameData) {
                    if (volatileFrameData.isJank) android.util.Log.w("NovaJank", "frame ${volatileFrameData.frameDurationUiNanos / 1_000_000}ms")
                }
            })
        }
        enableEdgeToEdge()
        Prefs.init(this)
        SiteStore.init(applicationContext)
        GoogleAccounts.init(this)
        Adaptive.init(this)
        Library.init(this)
        Thread({ Vault.init(applicationContext) }, "nova-vault").start()   // فك التشفير (Keystore) خارج الخيط الرئيسي
        Security.init(this)
        Perf.init(this)
        PageExtras.preheat(this)
        Shield.init(this)
        WebEngine.init(this)
        NetKit.init(this)
        WebSupport.init(this)
        Thread({ Security.deviceWarnings(applicationContext).forEach { Security.log(L("الجهاز"), it) } }, "nova-sec").start()
        Downloader.init(this)
        Notif.ensureChannels(applicationContext)
        DefaultBrowser.refresh(this)
        initialized = true
        if (intent?.getBooleanExtra("upd", false) == true) Updater.showPrompt()
        // لا نعيد فتح الرابط عند إعادة إنشاء الـ Activity (تدوير/استعادة العملية)
        val start = if (savedInstanceState == null) DefaultBrowser.urlFrom(intent) ?: "" else ""
        if (intent?.getBooleanExtra("dl", false) == true) dlTrigger++
        // نُبقي الـ Splash ظاهرة حتى ينتهي تنظيف المؤقت وتُعرض الواجهة
        splash.setKeepOnScreenCondition { !ready }
        // الكاش يُمسح كلياً عند التشغيل فقط إن فعّل المستخدم «مسح الكاش عند كل تشغيل»؛ وإلا يُنظَّف فقط إن تجاوز الحد (علم يضعه onStop)
        if (!cleanedThisProcess && (Prefs.autoClean || CacheCleaner.pending(this))) {
            cleanedThisProcess = true
            // التنظيف يجري قبل إنشاء أي WebView كي لا تكون ملفات الكاش مفتوحة
            Thread({
                runCatching { if (Prefs.autoClean) CacheCleaner.cleanAll(applicationContext) else CacheCleaner.cleanLarge(applicationContext) }
                runOnUiThread { if (!isFinishing && !isDestroyed) showUi(start) else ready = true }
            }, "nova-clean").start()
        } else showUi(start)
    }

    /** عند السخونة/توفير الطاقة: تحديد معدل التحديث 60Hz (يخفض حرارة الشاشة والمعالج على شاشات 90/120Hz). */
    private fun applyRefreshCap(level: Int) {
        val want = if (Prefs.cap60 && level >= 1) 60f else 0f
        val lp = window.attributes
        if (lp.preferredRefreshRate != want) { lp.preferredRefreshRate = want; window.attributes = lp }
    }

    private fun showUi(start: String) {
        setContent {
            val lvl = Adaptive.level
            LaunchedEffect(lvl, Prefs.cap60) { applyRefreshCap(lvl) }
            LaunchedEffect(Unit) { androidx.compose.runtime.withFrameNanos { }; reportFullyDrawn() }
            LaunchedEffect(Unit) { kotlinx.coroutines.delay(4000); Updater.check(applicationContext); WebEngine.checkLatest(applicationContext) }   // بعد استقرار الواجهة
            // محرك WebView مفقود فعلياً (مزوّد معطّل/غير مثبّت): شاشة توجيه بدل محاولة إنشاء WebView التي تنهار التطبيق بلا تفسير
            NovaTheme(this) {
                if (WebEngine.missing) EngineMissingScreen(this) { WebEngine.init(this) }
                else BrowserApp(start, dlTrigger, inPip, incoming)
            }
        }
        ready = true
        // تسخين محرك الـ WebView عند أول فراغ، حتى لا يتقطع أول بحث
        android.os.Looper.myQueue().addIdleHandler { Perf.warmUp(applicationContext); runCatching { WebView(applicationContext).destroy() }; false }
    }
    private fun onPipDismissed() {
        YtLog.add("native pip dismissed")
        inPip = false
        val w = wvProvider()
        if (YtWeb.owns(w)) YtWeb.background(w, false)
        YtMedia.stop(pause = true)   // إغلاق المنبثقة يوقف الفيديو ويزيل الإشعار
    }

    override fun onStop() {
        super.onStop()
        Perf.flushCookies(true)
        val nowMs = android.os.SystemClock.elapsedRealtime()
        if (nowMs - lastCacheCheck > 10 * 60_000L) {   // قياس حجم الكاش في خيط خلفي وبحدّ أقصى مرة كل 10 دقائق
            lastCacheCheck = nowMs
            Thread({ runCatching { CacheCleaner.markIfLarge(applicationContext) } }, "nova-cache").start()
        }
        if (pipReceiverOn) { runCatching { unregisterReceiver(pipReceiver) }; pipReceiverOn = false }
        // onPictureInPictureModeChanged(false) يسبق onStop دائماً، فـ inPip تكون false هنا؛ نعتمد على لحظة الخروج: إن أعقبه إيقاف بلا عودة = إغلاق ✕
        val exited = pipExitAt != 0L && android.os.SystemClock.elapsedRealtime() - pipExitAt < 4000
        if (exited && !isFinishing) { pipExitAt = 0L; onPipDismissed() }
        else if (inPip && !isInPictureInPictureMode) onPipDismissed()
    }

    override fun onDestroy() {
        // الخروج من التطبيق نهائياً: لا يبقى إشعار تشغيل لفيديو لم يعد له صفحة
        if (isFinishing && YtMedia.owner?.app != true) YtMedia.killPlayback()
        Popups.close()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("dl", false)) dlTrigger++
        if (intent.getBooleanExtra("upd", false)) Updater.showPrompt()
        DefaultBrowser.urlFrom(intent)?.let { incoming = ++incomingSeq to it }   // رابط من واتساب/تيليجرام… والتطبيق مفتوح
    }
}

@OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.FlowPreview::class)
@Composable
fun BrowserApp(startUrl: String, dlTrigger: Int, inPip: Boolean = false, incoming: Pair<Int, String>? = null) {
    val activity = LocalContext.current as ComponentActivity
    val cs = MaterialTheme.colorScheme
    val prefs = remember { activity.getSharedPreferences("nova", Context.MODE_PRIVATE) }
    val tabs = remember {
        mutableStateListOf<BrowserTab>().apply {
            (if (Prefs.restore) prefs.getString("tabs", "")!! else "").split("\n").filter { it.isNotBlank() }
                .forEachIndexed { i, u -> add(BrowserTab(i, if (u == "-") "" else u)) }
            if (startUrl.isNotBlank()) add(BrowserTab(size, startUrl))
            if (isEmpty()) add(BrowserTab(0, ""))
            if (Prefs.restore) prefs.getString("meta", "")!!.split(",").forEachIndexed { i, m ->
                val t = getOrNull(i) ?: return@forEachIndexed
                t.tag = m.substringBefore(':').toIntOrNull()?.coerceIn(0, 6) ?: 0; t.pinned = m.substringAfter(':', "0") == "1"
            }
        }
    }
    var nextId by remember { mutableIntStateOf(tabs.size + 1000) }
    var current by remember { mutableIntStateOf(if (startUrl.isNotBlank()) tabs.lastIndex else prefs.getInt("cur", 0).coerceIn(0, tabs.lastIndex)) }
    var showTabs by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var pendingSave by remember { mutableStateOf<PendingSave?>(null) }
    var fillOffer by remember { mutableStateOf<FillOffer?>(null) }
    var ytUrl by remember { mutableStateOf<String?>(null) }
    var notifPrompt by remember { mutableStateOf(false) }
    LaunchedEffect(dlTrigger) { if (dlTrigger > 0) openPanel(activity, "downloads") }
    val notif = rememberNotifState { ok -> if (!ok) toast(activity, L("فعّل الإشعارات من الإعدادات لمتابعة التنزيل في الخلفية")) }
    // يُعرض شرح الإذن مرة واحدة عند أول حاجة (تنزيل أو تشغيل في الخلفية)، ولا تتكرر الطلبات المتفرقة
    val askNotif = { if (Notif.needsPermission && !Notif.enabled(activity) && !Prefs.notifAsked) { Prefs.pickNotifAsked(true); notifPrompt = true } }
    var customView by remember { mutableStateOf<View?>(null) }
    var customCb by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    val tab = tabs[current.coerceIn(0, tabs.lastIndex)]

    LaunchedEffect(Unit) {
        snapshotFlow { Triple(tabs.joinToString("\n") { it.url.ifBlank { "-" } }, current, tabs.joinToString(",") { it.tag.toString() + ":" + (if (it.pinned) "1" else "0") }) }
            .debounce(700).collect { (s, c, m) -> prefs.edit().putString("tabs", s).putInt("cur", c).putString("meta", m).apply() }
    }

    var fileCb by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        fileCb?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res.resultCode, res.data)); fileCb = null
    }
    var sitePrompt by remember { mutableStateOf<SitePrompt?>(null) }
    var settingsMsg by remember { mutableStateOf<String?>(null) }
    val decisions = remember { mutableStateMapOf<String, Boolean>() }
    // طابور طلبات الأذونات: كان callback واحد يُستبدل عند طلب ثانٍ فلا يُجاب طلب الموقع أبداً (تحميل الخرائط اللانهائي)
    val permQueue = remember { ArrayList<Pair<List<String>, () -> Unit>>() }
    val permCur = remember { arrayOfNulls<Pair<List<String>, () -> Unit>>(1) }
    val permPump = remember { arrayOfNulls<() -> Unit>(1) }
    fun granted(p: String) = ContextCompat.checkSelfPermission(activity, p) == PackageManager.PERMISSION_GRANTED
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { m ->
        val cur = permCur[0]; permCur[0] = null
        val denied = m.filter { !it.value }.keys
        if (denied.any { !ActivityCompat.shouldShowRequestPermissionRationale(activity, it) })
            settingsMsg = L("تم رفض الإذن بشكل دائم. فعّله من إعدادات التطبيق ليعمل هذا الموقع.")
        cur?.second?.invoke()
        permPump[0]?.invoke()
    }
    fun pumpPerms() {
        if (permCur[0] != null || permQueue.isEmpty()) return
        val next = permQueue.removeAt(0)
        val need = next.first.filter { !granted(it) }
        if (need.isEmpty()) { next.second(); pumpPerms(); return }
        permCur[0] = next
        runCatching { permLauncher.launch(need.toTypedArray()) }.onFailure { permCur[0] = null; next.second(); pumpPerms() }
    }
    permPump[0] = { pumpPerms() }
    fun askPerms(perms: List<String>, cb: () -> Unit) {
        if (perms.all { granted(it) }) cb() else { permQueue.add(perms to cb); pumpPerms() }
    }
    val geoWaiters = remember { HashMap<String, MutableList<GeolocationPermissions.Callback>>() }

    // آخر التبويبات المغلقة (لإعادة فتحها من شاشة التبويبات)
    val closedTabs = remember { mutableStateListOf<Pair<String, String>>() }
    fun pushClosed(t: BrowserTab) {
        if (t.url.isBlank()) return
        closedTabs.removeAll { it.first == t.url }
        closedTabs.add(0, t.url to t.title)
        while (closedTabs.size > 15) closedTabs.removeAt(closedTabs.lastIndex)
    }
    fun go(t: BrowserTab, input: String) {
        val u = normalize(input); t.url = u
        if (!YtWeb.swapIfNeeded(t, u)) t.webView?.let { WebSupport.prepareLoad(it); it.loadUrl(u, Perf.privacyHeaders) }   // تبديل بين WebView يوتيوب المخصّص والعام يعيد الإنشاء ويحمّل الرابط
    }
    fun snapSoftware(t: BrowserTab, w: WebView, tw: Int, th: Int) {
        runCatching {
            if (w.width <= 0 || w.height <= 0 || w.visibility != View.VISIBLE) return@runCatching
            val bmp = Bitmap.createBitmap(tw, th, Bitmap.Config.RGB_565)
            val c = android.graphics.Canvas(bmp); c.scale(tw.toFloat() / w.width, th.toFloat() / w.height); w.draw(c)
            t.thumb = bmp
        }
    }
    fun snap(t: BrowserTab) {   // لقطة مصغّرة (40%) للتبويب الظاهر قبل مغادرته
        val w = t.webView ?: return
        if (t.url.isBlank() || w.width <= 0 || w.height <= 0 || w.visibility != View.VISIBLE) return   // مخفي تحت شاشة التبويبات: لا نلتقط لقطة فارغة
        if (Adaptive.level >= 2 && t.thumb != null) return   // وفّر المعالج عند السخونة
        if (t.ytPlaying && t.thumb != null) return           // رسم صفحة فيها فيديو يعمل مكلف ويُسبب تقطيعاً
        val k = 0.4f
        val tw = (w.width * k).toInt().coerceAtLeast(1); val th = (w.height * k).toInt().coerceAtLeast(1)
        // الطريقة السريعة: PixelCopy ينسخ آخر إطار مرسوم بالمعالج الرسومي في خيط الرسم بلا تجميد للواجهة.
        // الطريقة القديمة (w.draw على Canvas برمجي) تجبر المحرك على رسم الصفحة كاملة برمجياً على الخيط الرئيسي (عشرات الميلي ثانية)
        // فكان فتح التبويبات أو التبديل بينها يتجمّد لحظة. عند أي فشل نرجع للطريقة القديمة.
        val async = runCatching {
            if (!w.isAttachedToWindow) error("detached")
            val loc = IntArray(2); w.getLocationInWindow(loc)
            val rect = android.graphics.Rect(loc[0], loc[1], loc[0] + w.width, loc[1] + w.height)
            val bmp = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
            android.view.PixelCopy.request(activity.window, rect, bmp, { r ->
                if (r == android.view.PixelCopy.SUCCESS) t.thumb = bmp else snapSoftware(t, w, tw, th)
            }, android.os.Handler(android.os.Looper.getMainLooper()))
        }.isSuccess
        if (!async) snapSoftware(t, w, tw, th)
    }
    fun openTabs() { snap(tab); showTabs = true }
    fun newTab() { snap(tab); tabs.add(BrowserTab(nextId++)); current = tabs.lastIndex; showTabs = false; editing = true }
    fun openInNewTab(u: String) { tabs.add(BrowserTab(nextId++, u)); current = tabs.lastIndex }
    // رابط جاء من تطبيق آخر: يُفتح في التبويب الحالي إن كان فارغاً وإلا في تبويب جديد، مع إغلاق أي لوحة مفتوحة
    LaunchedEffect(incoming) {
        val u = incoming?.second ?: return@LaunchedEffect
        showTabs = false; editing = false
        val t = tabs.getOrNull(current)
        if (t != null && t.url.isBlank()) go(t, u) else openInNewTab(u)
    }
    fun dispose(t: BrowserTab) {
        YtMedia.tabClosed(t)
        t.webView?.let { w -> (w.parent as? ViewGroup)?.removeView(w); w.destroy() }
        t.webView = null
    }
    fun discard(t: BrowserTab) {   // تحرير الذاكرة مع حفظ الحالة (السجل والتمرير) لاستعادتها لاحقاً
        if (YtWeb.isLive(t)) return   // يوتيوب يعمل (صوت/منبثقة): لا نقطع التشغيل ولا نفقد حالة المشغّل
        t.webView?.let { w -> val b = android.os.Bundle(); runCatching { w.saveState(b) }; t.saved = b }
        dispose(t)
    }
    fun closeTab(i: Int) {
        pushClosed(tabs[i]); dispose(tabs[i]); tabs.removeAt(i)
        if (tabs.isEmpty()) tabs.add(BrowserTab(nextId++))
        current = current.coerceIn(0, tabs.lastIndex)
    }
    fun closeAll() {
        tabs.toList().filter { !it.pinned }.forEach { pushClosed(it); dispose(it); tabs.remove(it) }
        if (tabs.isEmpty()) tabs.add(BrowserTab(nextId++))
        current = current.coerceIn(0, tabs.lastIndex); showTabs = false
    }
    fun reopenClosed() {
        if (closedTabs.isEmpty()) return
        val u = closedTabs.removeAt(0).first
        tabs.add(BrowserTab(nextId++, u)); current = tabs.lastIndex; showTabs = false
    }
    fun duplicate(i: Int) { val o = tabs[i]; snap(o); tabs.add(i + 1, BrowserTab(nextId++, o.url).also { it.tag = o.tag }); current = i + 1; showTabs = false }
    fun closeOthers(i: Int) {
        val keep = tabs[i]
        tabs.toList().filter { it !== keep && !it.pinned }.forEach { pushClosed(it); dispose(it); tabs.remove(it) }
        current = tabs.indexOf(keep).coerceAtLeast(0)
    }
    fun closeByTag(tag: Int) {
        val cur = tabs.getOrNull(current)
        tabs.toList().filter { it.tag == tag && !it.pinned }.forEach { pushClosed(it); dispose(it); tabs.remove(it) }
        if (tabs.isEmpty()) tabs.add(BrowserTab(nextId++))
        current = (cur?.let { tabs.indexOf(it) } ?: -1).let { if (it < 0) 0 else it }
    }
    fun home(t: BrowserTab) {
        dispose(t); t.saved = null
        t.url = ""; t.title = L("تبويب جديد"); t.canBack = false; t.canForward = false; t.loading = false; t.finding = false
    }

    LaunchedEffect(Prefs.secureScreen) {
        val f = android.view.WindowManager.LayoutParams.FLAG_SECURE
        if (Prefs.secureScreen) activity.window.setFlags(f, f) else activity.window.clearFlags(f)
    }
    LaunchedEffect(Prefs.textZoom, Prefs.siteDark) { tabs.forEach { t -> t.webView?.let { Perf.applyDisplay(it) } } }
    fun printPage(t: BrowserTab) {
        val w = t.webView ?: return
        val pm = activity.getSystemService(Context.PRINT_SERVICE) as android.print.PrintManager
        val name = (t.title.ifBlank { "Nova" } + " - " + hostOf(t.url)).take(60)
        runCatching { pm.print(name, w.createPrintDocumentAdapter(name), android.print.PrintAttributes.Builder().build()) }
    }
    fun translatePage(t: BrowserTab) {
        if (t.url.isBlank()) return
        val target = Prefs.siteLangCode().ifEmpty { I18n.code() }
        go(t, "https://translate.google.com/translate?sl=auto&tl=" + target + "&u=" + Uri.encode(t.url))
        toast(activity, L("سيُرسل عنوان الصفحة إلى Google Translate لترجمتها."))
    }
    fun askPage(t: BrowserTab) {
        if (t.url.isBlank()) return
        toast(activity, S(R.string.smart_sent_to_google))
        openInNewTab(SmartAssist.aiUrl(SmartAssist.pagePrompt(t.title, t.url)))
    }
    fun closeDuplicates() {
        val keep = HashMap<String, BrowserTab>()
        val cur = tabs.getOrNull(current)
        tabs.forEach { t ->
            if (t.url.isBlank()) return@forEach
            val k = SmartAssist.dupKey(t.url)
            val prev = keep[k]
            if (prev == null || (t.pinned && !prev.pinned) || (t === cur && !prev.pinned)) keep[k] = t
        }
        val drop = tabs.filter { it.url.isNotBlank() && !it.pinned && keep[SmartAssist.dupKey(it.url)] !== it }
        if (drop.isEmpty()) { toast(activity, S(R.string.smart_dups_none)); return }
        drop.forEach { pushClosed(it); dispose(it); tabs.remove(it) }
        if (tabs.isEmpty()) tabs.add(BrowserTab(nextId++))
        current = (cur?.let { tabs.indexOf(it) } ?: -1).let { if (it < 0) 0 else it }
        toast(activity, S(R.string.smart_dups_closed).format(drop.size))
    }
    LaunchedEffect(Prefs.js) { tabs.forEach { it.webView?.settings?.javaScriptEnabled = Prefs.js } }
    LaunchedEffect(Prefs.pwMode) {
        tabs.forEach { it.webView?.importantForAutofill = if (Prefs.pwMode == 1) View.IMPORTANT_FOR_AUTOFILL_YES else View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS }
        fillOffer = null
    }
    LaunchedEffect(tab.url) { if (fillOffer?.host != Vault.norm(hostOf(tab.url))) fillOffer = null }
    fun clearData() {
        CookieManager.getInstance().removeAllCookies(null); CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
        tabs.forEach { t -> t.webView?.let { it.clearCache(true); it.clearHistory(); it.clearFormData() } }
        decisions.clear(); Library.clearHistory()
        toast(activity, L("تم مسح بيانات التصفح"))
    }

    fun clearCacheNow() {
        tabs.forEach { it.webView?.clearCache(true) }
        Thread { runCatching { CacheCleaner.clean(activity.applicationContext) } }.start()
        toast(activity, L("تم مسح الذاكرة المؤقتة"))
    }
    // اللوحات (الإعدادات/التنزيلات/المكتبة) تعيش في نافذة مستقلة؛ هذا الجسر هو كل ما تطلبه من المتصفح
    DisposableEffect(Unit) {
        PanelBus.clearData = { clearData() }
        PanelBus.clearCache = { clearCacheNow() }
        PanelBus.openUrl = { u -> val t = tabs.getOrNull(current); if (t != null && t.url.isBlank()) go(t, u) else openInNewTab(u) }
        onDispose { PanelBus.clearData = null; PanelBus.clearCache = null; PanelBus.openUrl = null }
    }
    // شاشة التبويبات تغطي الصفحة بالكامل: نُخفي الـ WebView تحتها كي لا يبقى يرسم (ما لم يكن صوت يوتيوب يعمل في الخلفية)
    LaunchedEffect(showTabs, tab.id, tab.epoch) {
        if (showTabs) kotlinx.coroutines.delay(260)
        // فيديو يوتيوب يعمل/مفتوح لا يُخفى أبداً: إخفاء الـ WebView يجعل الصفحة hidden فيفرّغ يوتيوب الفيديو (emptied) ويتوقف
        val keep = (Prefs.ytBg && YtMedia.owner != null) || YtWeb.isLive(tab)
        tab.webView?.visibility = if (showTabs && !keep) View.INVISIBLE else View.VISIBLE
    }
    // عند ضغط الذاكرة: نحرر الـ WebView للتبويبات الخلفية (تُعاد عند الرجوع لها)
    DisposableEffect(Unit) {
        val cb = object : ComponentCallbacks2 {
            fun release() {
                val cur = tabs.getOrNull(current)
                tabs.toList().forEach { t -> if (t !== cur && !YtWeb.isLive(t)) { if (t.webView != null) discard(t); t.thumb = null } }
            }
            override fun onTrimMemory(level: Int) {
                if (level != ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN && level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) release()
            }
            override fun onConfigurationChanged(c: android.content.res.Configuration) {}
            @Deprecated("Deprecated in Java") override fun onLowMemory() { release() }
        }
        activity.registerComponentCallbacks(cb)
        onDispose { activity.unregisterComponentCallbacks(cb) }
    }

    // حدّ أقصى لعدد الـ WebView الحيّة حسب ذاكرة الجهاز؛ الأقدم استخداماً يُحرَّر ويُستعاد عند الرجوع
    val maxLive = remember {
        val am = activity.getSystemService(android.app.ActivityManager::class.java)
        if (am.isLowRamDevice) 2 else if (am.memoryClass >= 256) 5 else 3
    }
    LaunchedEffect(current, tabs.size, Adaptive.level) {
        val cap = Adaptive.liveCap(maxLive)
        val cur = tabs.getOrNull(current)
        cur?.lastUsed = android.os.SystemClock.elapsedRealtime()
        kotlinx.coroutines.delay(1200)   // بعد إنشاء الـ WebView الجديد
        val live = tabs.filter { it.webView != null }
        if (live.size > cap)
            live.filter { it !== cur && !YtWeb.isLive(it) }.sortedWith(compareBy({ it.pinned }, { it.lastUsed })).take(live.size - cap).forEach { discard(it) }
    }
    // تبويب خلفي لم يُستخدم منذ مدة: يُحرَّر الـ WebView (مع حفظ السجل والتمرير) فلا تبقى صفحات خاملة تستهلك الذاكرة والمعالج والبطارية.
    // وقت «آخر استخدام» يُسجَّل لحظة مغادرة التبويب (لا لحظة فتحه) فلا يُحرَّر تبويب تركه المستخدم للتو.
    DisposableEffect(current) {
        val t = tabs.getOrNull(current)
        onDispose { t?.lastUsed = android.os.SystemClock.elapsedRealtime() }
    }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000)
            if (!activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) continue
            val idle = if (Adaptive.level >= 1) 5 * 60_000L else 15 * 60_000L
            val now = android.os.SystemClock.elapsedRealtime()
            val cur = tabs.getOrNull(current)
            tabs.toList().forEach { t ->
                if (t !== cur && t.webView != null && !t.pinned && !t.loading && !YtWeb.isLive(t) && t.lastUsed > 0 && now - t.lastUsed > idle) discard(t)
            }
        }
    }
    // عند الخروج من التطبيق: إيقاف مؤقتات الصفحات لتوفير المعالج والبطارية
    DisposableEffect(Unit) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            val w = tabs.getOrNull(current)?.webView
            val inPipNow = (activity as? MainActivity)?.inPip == true
            if (e == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                val ytLive = Prefs.ytBg && (YtMedia.owner != null || inPipNow)
                if (!ytLive && customView == null) YtMedia.stop()   // الخلفية معطّلة: لا إشعار تشغيل بعد الخروج
                if (ytLive) YtWeb.background(w, true)   // لا نجمّد الصفحة أثناء تشغيل يوتيوب
                else if (Prefs.pauseBg && customView == null) { w?.onPause(); w?.pauseTimers() }
            } else if (e == androidx.lifecycle.Lifecycle.Event.ON_START) {
                // pauseTimers عامة لكل الـ WebView: نستأنفها حتى لو لم يكن للتبويب الحالي WebView (صفحة البداية) وإلا بقيت مؤقتات بقية التبويبات مجمّدة
                (w ?: tabs.firstNotNullOfOrNull { it.webView })?.resumeTimers(); w?.onResume()
                if (!inPipNow) YtWeb.background(w, false)
            }
        }
        activity.lifecycle.addObserver(obs)
        onDispose { activity.lifecycle.removeObserver(obs) }
    }

    val handlers = remember {
        Handlers(
            activity = activity,
            chooser = { cb, p ->
                fileCb?.onReceiveValue(null); fileCb = cb
                runCatching { fileLauncher.launch(p.createIntent()) }.onFailure { cb.onReceiveValue(null); fileCb = null }
                true
            },
            permission = { req ->
                activity.runOnUiThread {
                    val av = req.resources.filter { it == PermissionRequest.RESOURCE_VIDEO_CAPTURE || it == PermissionRequest.RESOURCE_AUDIO_CAPTURE }
                    // فيديو محمي (DRM/EME): كان يُرفض فيتعطّل التشغيل؛ هذا إذن معرّف الوسائط المحمية فقط وليس كاميرا ولا ميكروفون
                    val prot = req.resources.filter { it == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID }.toTypedArray()
                    if (av.isEmpty()) { if (prot.isNotEmpty()) req.grant(prot) else req.deny(); return@runOnUiThread }
                    fun perm(r: String) = if (r == PermissionRequest.RESOURCE_VIDEO_CAPTURE) Manifest.permission.CAMERA else Manifest.permission.RECORD_AUDIO
                    val label = av.joinToString(L(" و")) { if (it == PermissionRequest.RESOURCE_VIDEO_CAPTURE) L("الكاميرا") else L("الميكروفون") }
                    fun finish(allow: Boolean) {
                        if (!allow) { req.deny(); return }
                        askPerms(av.map { perm(it) }) {
                            val ok = av.filter { granted(perm(it)) }.toTypedArray()
                            if (ok.isEmpty() && prot.isEmpty()) req.deny() else req.grant(ok + prot)
                        }
                    }
                    val key = req.origin.toString() + "|av"
                    when (decisions[key]) {
                        true -> finish(true)
                        false -> req.deny()
                        null -> sitePrompt = SitePrompt(L("السماح بالوصول؟"), ("" + (hostOf(req.origin.toString())) + L(" يريد استخدام ") + label),
                            { decisions[key] = true; finish(true) }, { decisions[key] = false; finish(false) })
                    }
                }
            },
            geo = { origin, cb ->
                activity.runOnUiThread {
                    val key = origin + "|geo"
                    val list = geoWaiters.getOrPut(key) { ArrayList() }
                    list.add(cb)
                    if (list.size > 1 && sitePrompt != null) return@runOnUiThread   // طلب مكرر والحوار ظاهر: يُجاب كل المنتظرين معاً
                    fun done(allow: Boolean) {
                        val cbs = geoWaiters.remove(key).orEmpty()
                        if (!allow) { cbs.forEach { it.invoke(origin, false, false) }; return }
                        val perms = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                        askPerms(perms) { val ok = perms.any { granted(it) }; cbs.forEach { it.invoke(origin, ok, false) } }
                    }
                    when (decisions[key]) {
                        true -> done(true)
                        false -> done(false)
                        null -> sitePrompt = SitePrompt(L("السماح بالموقع؟"), ("" + (hostOf(origin)) + L(" يريد معرفة موقعك")),
                            { decisions[key] = true; done(true) }, { decisions[key] = false; done(false) })
                    }
                }
            },
            openTab = { openInNewTab(it) },
            showCustom = { v, cb ->
                if (customView != null && customView !== v) {   // عرض ملء شاشة جديد فوق قديم: نُعلم القديم بإخفائه كي لا يتسرّب
                    customView?.let { (it.parent as? ViewGroup)?.removeView(it) }
                    runCatching { customCb?.onCustomViewHidden() }
                }
                customView = v; customCb = cb
                tabs.getOrNull(current)?.webView?.takeIf { YtWeb.owns(it) }?.let { YtWeb.onFullscreen(it, true) }
            },
            hideCustom = {
                // فصل عرض الفيديو صراحةً من حاويته ثم إنعاش المشغّل بعد الرجوع (كانت الشاشة تبقى سوداء)
                customView?.let { v -> (v.parent as? ViewGroup)?.removeView(v) }
                customView = null; customCb = null
                tabs.getOrNull(current)?.webView?.takeIf { YtWeb.owns(it) }?.let { YtWeb.onFullscreen(it, false); YtWeb.afterFullscreen(it) }
            },
            onLoginForm = { t, _, host ->
                if (t === tabs.getOrNull(current)) {
                    val cs = Vault.forHost(host)
                    if (cs.isNotEmpty()) fillOffer = FillOffer(t.id, host, cs)
                    else if (GoogleAccounts.suggest && GoogleAccounts.accounts.isNotEmpty())
                        fillOffer = FillOffer(t.id, host, GoogleAccounts.accounts.map { Cred("g:$it", host, it, "", 0L) })   // بريد فقط، بلا كلمة مرور
                }
            },
            onCredential = { host, user, pass ->
                if (pass.isNotEmpty() && !Vault.isNever(host)) {
                    val k = Vault.classify(host, user, pass)
                    if (k != SaveKind.SAME) pendingSave = PendingSave(host, user, pass, k)
                }
            },
            onYtState = {
                askNotif()
                (activity as? MainActivity)?.onYtPlayState()
            },
            onDownload = { u, ua, cd, mime, ref ->
                if (!Shield.downloadAllowed(hostOf(ref ?: u))) toast(activity, L("تم حظر تنزيلات تلقائية متتابعة من الصفحة"))
                else if (u.startsWith("blob:") || u.startsWith("data:")) toast(activity, L("هذا النوع من التنزيل غير مدعوم بعد"))
                else {
                    val start = {
                        Downloader.start(activity, u, ua, cd, mime, ref)
                        toast(activity, L("بدأ التنزيل — القائمة ⋮ ثم التنزيلات"))
                        askNotif()
                        Unit
                    }
                    if (Security.isRiskyFile(u, cd)) {
                        Security.log(L("تنزيل"), (L("تحذير ملف تنفيذي من ") + (hostOf(u))))
                        novaDialog(activity).setTitle(L("ملف قد يكون خطيراً"))
                            .setMessage((L("هذا النوع من الملفات (تطبيق/ملف تنفيذي) قد يضر بجهازك. نزّله فقط من مصدر تثق به.\n\n") + (hostOf(u))))
                            .setPositiveButton(L("تنزيل")) { _, _ -> start() }.setNegativeButton(L("إلغاء"), null).show()
                    } else start()
                }
            }
        )
    }

    DisposableEffect(customView != null) {
        val c = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        if (customView != null) {
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsetsCompat.Type.systemBars())
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        onDispose {
            c.show(WindowInsetsCompat.Type.systemBars())
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    BackHandler(enabled = tab.canBack) { tab.webView?.let { WebSupport.goBackFast(it) } }
    BackHandler(enabled = tab.url.isNotBlank() && !tab.canBack) { home(tab) }   // آخر صفحة في السجل: العودة للرئيسية بدل إغلاق التطبيق
    BackHandler(enabled = tab.finding) { tab.webView?.clearMatches(); tab.finding = false }
    BackHandler(enabled = editing) { editing = false }
    BackHandler(enabled = showTabs) { showTabs = false }
    BackHandler(enabled = customView != null) {
        customView?.let { v -> (v.parent as? ViewGroup)?.removeView(v) }
        customCb?.onCustomViewHidden(); customView = null; customCb = null
        tabs.getOrNull(current)?.webView?.takeIf { YtWeb.owns(it) }?.let { YtWeb.onFullscreen(it, false); YtWeb.afterFullscreen(it) }
    }

    // ربط الـ Activity: مزوّد الـ WebView الحالي + تفعيل الدخول التلقائي للنافذة المنبثقة أثناء تشغيل فيديو يوتيوب
    val mainAct = activity as? MainActivity
    SideEffect {
        mainAct?.wvProvider = { tabs.getOrNull(current)?.webView }
        mainAct?.fullscreenActive = customView != null
        val want = Prefs.autoPip && ((tab.yt && isYtVideo(tab.url)) || customView != null)
        if (mainAct != null && mainAct.pipAuto != want) { mainAct.pipAuto = want; mainAct.refreshPip() }
    }

    val iosC = LocalIos.current
    val primaryInt = cs.primary.toArgb()
    val bgInt = cs.surfaceContainerHigh.toArgb()
    val spinnerInt = cs.onSurface.toArgb()

    // الكيبورد أثناء الكتابة داخل صفحة (مثل خانة سؤال الذكاء الاصطناعي): تنكمش الصفحة فوق الكيبورد ويختفي شريط العنوان السفلي.
    // derivedStateOf يتغيّر مرتين فقط (فتح/إغلاق) فلا إعادة تركيب أثناء حركة الكيبورد.
    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime
    val navInsets = WindowInsets.navigationBars
    val imeVisible by remember { derivedStateOf { imeInsets.getBottom(density) > 0 } }
    val imeSettled = rememberSettledIme(imeInsets)   // ارتفاع الكيبورد بعد استقراره (تغيير واحد للـ WebView بدل ~20)
    var pageTyping by remember { mutableStateOf(false) }
    var siteSheet by remember { mutableStateOf(false) }
    var wasBlocked by remember { mutableStateOf(false) }
    LaunchedEffect(imeVisible, editing, tab.finding) {
        val blocked = editing || tab.finding
        if (imeVisible && !blocked) {
            if (wasBlocked) kotlinx.coroutines.delay(700)   // بعد إنهاء كتابة العنوان: ننتظر إغلاق الكيبورد كي لا يومض الشريط
            pageTyping = true
        } else pageTyping = false
        wasBlocked = blocked
    }
    // بعد استقرار الكيبورد: أظهر خانة الإدخال المحدَّدة فوقه (المواقع ذات شريط الإدخال كانت تبقى مغطّاة)
    LaunchedEffect(imeSettled.value, pageTyping) {
        if (pageTyping && imeSettled.value > 0) {
            kotlinx.coroutines.delay(140)
            tab.webView?.let { WebSupport.revealFocused(it) }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Surface(Modifier.fillMaxSize(), color = if (inPip) Color.Black else iosC.bg) {
            // وضع التطبيق: الارتفاع يتبدّل فوراً (تحريك ارتفاع الـ WebView كل إطار يسبب تقطيعاً)، والشريط نفسه ينزلق على طبقة الرسم
            val site = if (Prefs.pwaMode && !inPip && customView == null) Pwa.info(tab.url) else null
            val barH = if (site != null) Pwa.BAR_H.dp else 0.dp
            val tintTarget = if (tab.url.isBlank()) iosC.groupedBg else (tab.topColor?.let { Color(it) } ?: iosC.bg)
            val stColor by animateColorAsState(if (site != null) iosC.chrome else tintTarget, tween(Adaptive.ms(220)), label = "statusTint")
            SideEffect {
                WindowInsetsControllerCompat(activity.window, activity.window.decorView).isAppearanceLightStatusBars = stColor.luminance() > 0.5f
            }
            if (site == null && !inPip) Box(
                Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(stColor)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { tab.webView?.let { WebSupport.scroll(it, true) } }
            )
            // لون شريط الحالة يتبع لون شريط الموقع (كان خلفية سادة بينما الشريط مائل للون الهوية)
            AnimatedVisibility(visible = site != null, enter = fadeIn(tween(Adaptive.ms(160))), exit = fadeOut(tween(Adaptive.ms(120)))) { StatusBarWash() }
            Box(Modifier.fillMaxSize().statusBarsPadding().displayCutoutPadding()) {
                Box(Modifier.fillMaxSize().padding(top = barH).pageInsets({ imeSettled.value }, navInsets, pageTyping, inPip).background(iosC.bg)) {
                    AnimatedContent(
                        targetState = tab.id to tab.url.isBlank(),
                        transitionSpec = {
                            if (targetState.second) fadeIn(tween(Adaptive.ms(180), easing = NovaMotion.emphasized)) togetherWith fadeOut(tween(Adaptive.ms(110)))
                            else (fadeIn(tween(Adaptive.ms(220), easing = NovaMotion.emphasized)) + scaleIn(tween(Adaptive.ms(260), easing = NovaMotion.emphasized), initialScale = 0.985f)) togetherWith fadeOut(tween(Adaptive.ms(120)))
                        },
                        label = "page"
                    ) { (id, blank) ->
                        val tb = tabs.firstOrNull { it.id == id }
                        if (tb != null) {
                            if (blank) {
                                StartPage(
                                    tabsCount = tabs.size,
                                    activeDl = Downloader.tasks.count { it.status == Downloader.DOWNLOADING || it.status == Downloader.PREPARING },
                                    onSearchClick = { editing = true }, onAi = { go(tb, AI_MODE_URL) }, onOpen = { go(tb, it) },
                                    onTabs = { openTabs() }, onDownloads = { openPanel(activity, "downloads") },
                                    onSettings = { openPanel(activity, "settings") }, onLibrary = { openPanel(activity, "library") }
                                )
                            } else key(id, tb.epoch) {
                                AndroidView(
                                    modifier = Modifier.fillMaxSize(),
                                    factory = { ctx ->
                                        // يوتيوب له WebView مخصّص منفصل (YtWeb)؛ بقية المواقع تستخدم الـ WebView العام
                                        val wv = tb.webView ?: (if (YtWeb.isYtUrl(tb.url)) YtWeb.create(ctx, tb, handlers) else createWebView(ctx, tb, handlers)).also { w ->
                                            tb.webView = w
                                            val sv = tb.saved; tb.saved = null
                                            if (sv != null && !tb.yt) WebSupport.cacheFirst(w)   // تبويب حُرّر لتوفير الذاكرة: يعود من الكاش فوراً
                                            val restored = sv != null && w.restoreState(sv) != null
                                            if (!restored) {
                                                if (tb.holdLoad) {   // انهارت العملية مراراً: صفحة بزر إعادة المحاولة بدل حلقة تحميل لا تنتهي
                                                    tb.holdLoad = false
                                                    w.loadDataWithBaseURL(tb.url, errorHtml(tb.url, L("تعطّلت عملية عرض الصفحة عدة مرات. أغلق تبويبات أخرى لتحرير الذاكرة ثم أعد المحاولة.")), "text/html", "UTF-8", tb.url)
                                                } else { WebSupport.prepareLoad(w); w.loadUrl(tb.url, Perf.privacyHeaders) }
                                            }
                                        }
                                        (wv.parent as? ViewGroup)?.removeView(wv)
                                        wv.visibility = View.VISIBLE   // قد يكون أُخفي أثناء عرض شاشة التبويبات
                                        if (tb.yt) {
                                            // يوتيوب: بلا SwipeRefreshLayout (يعترض اللمس فيقطّع التمرير ويتعارض مع تمرير الصفحة الداخلي)
                                            FrameLayout(ctx).apply { addView(wv, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)) }
                                        } else SwipeRefreshLayout(ctx).apply {
                                            addView(wv, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                                            setOnRefreshListener { wv.reload() }
                                            // يُستدعى مع كل حركة لمس أثناء التمرير: حكم الرابط (تحليل Uri) يُحفظ لكل رابط بدل إعادة حسابه في كل مرة
                                            var judgedUrl: String? = null; var judged = false
                                            setOnChildScrollUpCallback { _, _ ->
                                                if (wv.scrollY > 0 || wv.canScrollVertically(-1) || WebSupport.innerScrolled(wv)) true
                                                else {
                                                    val u = wv.url
                                                    if (u != judgedUrl) { judgedUrl = u; judged = Pwa.kind(u) != SiteKind.NONE || WebCompat.noPullRefresh(u) }
                                                    judged
                                                }
                                            }
                                            setDistanceToTriggerSync((120 * ctx.resources.displayMetrics.density).toInt())
                                            setSlingshotDistance((140 * ctx.resources.displayMetrics.density).toInt())
                                            setColorSchemeColors(primaryInt)
                                            setProgressBackgroundColorSchemeColor(bgInt)
                                            useIosSpinner(spinnerInt)
                                        }
                                    }
                                )
                                // التبويب غير الظاهر يُوقَف مؤقتاً (يوفر المعالج والذاكرة) ويُستأنف عند ظهوره
                                DisposableEffect(tb.id, tb.epoch) {
                                    tb.webView?.onResume()
                                    onDispose { tb.webView?.onPause() }
                                }
                            }
                        }
                    }
                }
                AnimatedVisibility(
                    visible = site != null,
                    enter = NovaMotion.barEnter, exit = NovaMotion.barExit, modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    // آخر معلومات صالحة تبقى أثناء حركة الخروج كي لا يفرغ الشريط فجأة
                    val shown = remember { mutableStateOf(site) }
                    if (site != null) shown.value = site
                    shown.value?.let { si ->
                        SiteBar(
                            info = si, progress = tab.progress, loading = tab.loading,
                            onReload = { tab.webView?.reload() }, onShare = { siteSheet = true }, onTitleTap = { Pwa.scrollTop(tab.webView) },
                            onPip = if (si.kind == SiteKind.YT_VIDEO) ({ mainAct?.enterPip() }) else null,
                            onDownload = if (si.kind == SiteKind.YT_VIDEO) ({ ytUrl = tab.url }) else null
                        )
                    }
                }
                AnimatedVisibility(
                    visible = !inPip && !pageTyping, modifier = Modifier.align(Alignment.BottomCenter),
                    enter = fadeIn(tween(Adaptive.ms(160))), exit = fadeOut(tween(Adaptive.ms(90)))
                ) {
                if (tab.finding) key(tab.id) { FindBar(tab) } else BottomPill(
                    tab = tab, tabCount = tabs.size, editing = editing, setEditing = { editing = it },
                    onGo = { go(tab, it) }, onTabs = { openTabs() }, onNewTab = { newTab() }, onHome = { home(tab) },
                    onFind = { tab.findInfo = ""; tab.finding = true },
                    onDesktop = { tab.desktop = !tab.desktop; tab.webView?.let { applyUa(it, tab.desktop, tab.safari); it.reload() } },
                    onShare = { shareText(activity, tab.url) }, onCopy = { copyText(activity, tab.url) },
                    onDownloads = { openPanel(activity, "downloads") }, onSettings = { openPanel(activity, "settings") }, onTranslate = { translatePage(tab) }, onPrint = { printPage(tab) }, onLibrary = { openPanel(activity, "library") }, onCloseCurrent = { closeTab(current) },
                    onSummarize = { tab.webView?.let { w -> SmartAssist.summarize(activity, w) { u -> openInNewTab(u) } } }, onAskAi = { askPage(tab) },
                    onSwitch = { d -> snap(tab); current = (current + d).coerceIn(0, tabs.lastIndex) }
                )
                }
                // زر المنبثقة لا يعتمد على وضع التطبيق: يظهر فوق الشريط السفلي في صفحة فيديو يوتيوب حين يكون الشريط العلوي معطّلاً
                if (site == null && !inPip && !pageTyping && customView == null && tab.yt && isYtVideo(tab.url)) {
                    Surface(
                        onClick = { mainAct?.enterPip() }, shape = CircleShape, color = cs.surfaceContainerHigh.copy(alpha = 0.92f), shadowElevation = 3.dp,
                        modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 12.dp, bottom = UiLayout.PILL + 14.dp)
                    ) { PipGlyph(cs.onSurface, Modifier.padding(11.dp).size(22.dp)) }
                }
            }
        }
        if (Updater.prompt) UpdateDialog()
        if (siteSheet) MenuSheet(
            tab, { siteSheet = false }, { newTab() }, { tab.findInfo = ""; tab.finding = true },
            { shareText(activity, tab.url) }, { copyText(activity, tab.url) }, { printPage(tab) }
        )
        AnimatedVisibility(
            visible = showTabs,
            enter = fadeIn(tween(Adaptive.ms(220))) + scaleIn(tween(Adaptive.ms(340), easing = NovaMotion.emphasized), initialScale = 1.1f),
            exit = fadeOut(tween(Adaptive.ms(200))) + scaleOut(tween(Adaptive.ms(280), easing = NovaMotion.emphasized), targetScale = 1.1f)
        ) {
            TabSwitcher(
                tabs = tabs, current = current.coerceIn(0, tabs.lastIndex),
                onSelect = { i -> if (i != current) snap(tab); current = i; showTabs = false },
                onClose = { i -> closeTab(i) }, onNew = { newTab() }, onCloseAll = { closeAll() }, onBack = { showTabs = false },
                onDuplicate = { i -> duplicate(i) }, onCloseOthers = { i -> closeOthers(i) }, onCloseTag = { g -> closeByTag(g) },
                canReopen = closedTabs.isNotEmpty(), onReopen = { reopenClosed() }, onCloseDuplicates = { closeDuplicates() }
            )
        }
        fillOffer?.takeIf { it.tabId == tab.id && !inPip && !editing }?.let { o ->
            FillBanner(
                o, modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp),
                onClose = { fillOffer = null },
                onFill = { c ->
                    if (c.pass.isEmpty()) { tab.webView?.let { PasswordBridge.fill(it, c) }; fillOffer = null }   // بريد الحساب: لا يحتاج بصمة
                    else Auth.run(activity, L("تأكيد الهوية لتعبئة كلمة المرور")) { tab.webView?.let { PasswordBridge.fill(it, c) }; fillOffer = null }
                }
            )
        }
        customView?.let { v ->
            var pipTouch by remember(v) { mutableIntStateOf(0) }
            var pipBtnShown by remember(v) { mutableStateOf(true) }
            LaunchedEffect(pipTouch) { pipBtnShown = true; kotlinx.coroutines.delay(3500); pipBtnShown = false }
            Box(
                Modifier.fillMaxSize().background(Color.Black).pointerInput(v) {
                    // نراقب اللمس دون استهلاكه: يظهر الزر مع أزرار يوتيوب ويختفي معها
                    awaitPointerEventScope {
                        while (true) {
                            val ev = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                            if (ev.changes.any { it.pressed && !it.previousPressed }) pipTouch++
                        }
                    }
                }
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx -> FrameLayout(ctx).apply { setBackgroundColor(android.graphics.Color.BLACK); (v.parent as? ViewGroup)?.removeView(v); addView(v) } }
                )
                // زر النافذة المنبثقة: أعلى المنتصف (أزرار يوتيوب يميناً ويساراً فلا تداخل) ويختفي تلقائياً
                if (!inPip && pipBtnShown) Surface(
                    onClick = { mainAct?.enterPip() }, shape = CircleShape, color = Color.Black.copy(alpha = 0.5f),
                    modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 10.dp)
                ) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp), tint = Color.White); Spacer(Modifier.width(6.dp))
                        Text(L("منبثق"), style = MaterialTheme.typography.labelLarge, color = Color.White)
                    }
                }
            }
        }
        // شريط تنبيه محرك العرض (قديم جداً/مفقود): يعلو كل شيء — القرار بالتحديث لا يجب أن يُخفى داخل الإعدادات فقط
        if (!inPip) Box(Modifier.align(Alignment.TopCenter)) { EngineAlertBanner(activity) }
    }

    if (notifPrompt) NotifPermissionDialog(
        onAllow = { notifPrompt = false; notif.request() },
        onLater = { notifPrompt = false }
    )
    sitePrompt?.let { p ->
        NovaDialog(
            title = p.title, icon = Icons.Default.Info, onDismiss = { sitePrompt = null; p.onDeny() },
            confirmText = L("سماح"), onConfirm = { sitePrompt = null; p.onAllow() },
            dismissText = L("رفض"), onDismissClick = { sitePrompt = null; p.onDeny() }
        ) { DialogText(p.message) }
    }
    pendingSave?.let { p ->
        SavePasswordDialog(
            p,
            onSave = { Vault.upsert("", p.host, p.user, p.pass); pendingSave = null; toast(activity, L("تم حفظ كلمة المرور")) },
            onNever = { Vault.neverSave(p.host); pendingSave = null },
            onDismiss = { pendingSave = null }
        )
    }
    ytUrl?.let { u ->
        YtDownloadSheet(u, onDismiss = { ytUrl = null }, onStarted = {
            askNotif()
        })
    }
    settingsMsg?.let { m ->
        NovaDialog(
            title = L("الإذن مطلوب"), icon = Icons.Default.Lock, onDismiss = { settingsMsg = null },
            confirmText = L("فتح الإعدادات"), dismissText = L("لاحقاً"),
            onConfirm = {
                settingsMsg = null
                activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", activity.packageName, null)))
            }
        ) { DialogText(m) }
    }
}
