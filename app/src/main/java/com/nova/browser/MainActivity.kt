package com.nova.browser

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.draw.drawBehind
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

    var inPip by mutableStateOf(false)
    var tabProvider: () -> BrowserTab? = { null }
    var netTabs: () -> List<BrowserTab> = { emptyList() }
    private val netRetry by lazy { NetRetry(this) { netTabs() } }

    override fun onStart() {
        super.onStart()
        netRetry.start()
    }

    override fun onResume() {
        super.onResume()
        DefaultBrowser.refresh(this)
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        window.decorView.let { d -> d.post { androidx.core.view.ViewCompat.requestApplyInsets(d); d.requestLayout() } }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        splash.setOnExitAnimationListener { provider ->
            runCatching {
                val view = provider.view
                val icon = provider.iconView
                val startAt = provider.iconAnimationStartMillis
                val left = if (startAt > 0L) (startAt + provider.iconAnimationDurationMillis - System.currentTimeMillis()).coerceIn(0L, 800L) else 0L
                val fadeMs = Adaptive.ms(320).toLong()
                val iconMs = Adaptive.ms(280).toLong()
                val finish = { runCatching { provider.remove() }; Unit }
                view.postDelayed({
                    SplashGate.reveal()
                    if (fadeMs == 0L) { finish(); return@postDelayed }
                    val ease = android.view.animation.PathInterpolator(0.32f, 0.72f, 0f, 1f)
                    icon.animate().scaleX(1.16f).scaleY(1.16f).alpha(0f).setDuration(iconMs).setInterpolator(ease).start()
                    view.animate().alpha(0f).setStartDelay(40L).setDuration(fadeMs)
                        .setInterpolator(android.view.animation.LinearInterpolator())
                        .withEndAction { finish() }.start()
                }, left)
            }.onFailure { runCatching { provider.remove() }; SplashGate.reveal() }
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
        // تدوير بتلاشٍ متقاطع بدل القفزة
        window.attributes = window.attributes.apply {
            rotationAnimation = android.view.WindowManager.LayoutParams.ROTATION_ANIMATION_CROSSFADE
        }
        Prefs.init(this)
        Prefs.syncNightMode(this)
        SiteStore.init(applicationContext)
        GoogleAccounts.init(this)
        Adaptive.init(this)
        Library.init(this)
        Work.cpu { Vault.init(applicationContext) }   // فك التشفير (Keystore) خارج الخيط الرئيسي
        Security.init(this)
        Perf.init(this)
        Shield.init(this)
        NetKit.init(this)
        Work.cpu { Security.deviceWarnings(applicationContext).forEach { Security.log(L("الجهاز"), it) } }
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
        val newBuild = Updater.versionChanged(this)
        if (!cleanedThisProcess && (newBuild || Prefs.autoClean || CacheCleaner.pending(this))) {
            cleanedThisProcess = true
            // التنظيف يجري قبل إنشاء أي WebView كي لا تكون ملفات الكاش مفتوحة
            Work.disk {
                runCatching { if (newBuild || Prefs.autoClean) CacheCleaner.cleanAll(applicationContext) else CacheCleaner.cleanLarge(applicationContext) }
                runOnUiThread { if (!isFinishing && !isDestroyed) showUi(start) else ready = true }
            }
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
            LaunchedEffect(Unit) { kotlinx.coroutines.delay(4000); Updater.check(applicationContext) }
            NovaTheme(this) { BrowserApp(start, dlTrigger, inPip, incoming) }
        }
        ready = true
        android.os.Looper.myQueue().addIdleHandler { runCatching { NovaApp.runtime() }; false }
    }

    override fun onStop() {
        super.onStop()
        netRetry.stop()
        val nowMs = android.os.SystemClock.elapsedRealtime()
        if (nowMs - lastCacheCheck > 10 * 60_000L) {   // قياس حجم الكاش في خيط خلفي وبحدّ أقصى مرة كل 10 دقائق
            lastCacheCheck = nowMs
            Work.disk { runCatching { CacheCleaner.markIfLarge(applicationContext) } }
        }
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
fun BrowserApp(startUrl: String, dlTrigger: Int, inPipIn: Boolean = false, incoming: Pair<Int, String>? = null) {
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
    var notifPrompt by remember { mutableStateOf(false) }
    LaunchedEffect(dlTrigger) { if (dlTrigger > 0) openPanel(activity, "downloads") }
    val notif = rememberNotifState { ok -> if (!ok) toast(activity, L("فعّل الإشعارات من الإعدادات لمتابعة التنزيل في الخلفية")) }
    // يُعرض شرح الإذن مرة واحدة عند أول حاجة (تنزيل أو تشغيل في الخلفية)، ولا تتكرر الطلبات المتفرقة
    val askNotif = { if (Notif.needsPermission && !Notif.enabled(activity) && !Prefs.notifAsked) { Prefs.pickNotifAsked(true); notifPrompt = true } }
    var geckoFull by remember { mutableStateOf(false) }
    val inPip = inPipIn || geckoFull
    val tab = tabs[current.coerceIn(0, tabs.lastIndex)]
    val visit = remember { mutableStateListOf<Int>() }
    var lastTabId by remember { mutableIntStateOf(-1) }
    LaunchedEffect(tab.id) {
        if (lastTabId != -1 && lastTabId != tab.id) { visit.remove(lastTabId); visit.add(lastTabId) }
        visit.remove(tab.id)
        while (visit.size > 30) visit.removeAt(0)
        lastTabId = tab.id
    }

    LaunchedEffect(Unit) {
        snapshotFlow { Triple(tabs.joinToString("\n") { it.url.ifBlank { "-" } }, current, tabs.joinToString(",") { it.tag.toString() + ":" + (if (it.pinned) "1" else "0") }) }
            .debounce(700).collect { (s, c, m) -> prefs.edit().putString("tabs", s).putInt("cur", c).putString("meta", m).apply() }
    }

    var fileCb by remember { mutableStateOf<((Array<Uri>?) -> Unit)?>(null) }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val d = res.data
        val picked: Array<Uri>? = if (res.resultCode != android.app.Activity.RESULT_OK || d == null) null else {
            val clip = d.clipData
            if (clip != null && clip.itemCount > 0) Array(clip.itemCount) { clip.getItemAt(it).uri } else d.data?.let { arrayOf(it) }
        }
        fileCb?.invoke(picked); fileCb = null
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
        t.engine?.load(u)
    }
    fun snap(t: BrowserTab) {
        t.engine?.let { snapEngine(t, it) }
    }
    fun openTabs() { snap(tab); showTabs = true }
    fun newTab(g: Int = Groups.NONE) { snap(tab); tabs.add(BrowserTab(nextId++).also { it.group = g }); current = tabs.lastIndex; showTabs = false; editing = true }
    fun openInNewTab(u: String) { tabs.add(BrowserTab(nextId++, u)); current = tabs.lastIndex }
    // رابط جاء من تطبيق آخر: يُفتح في التبويب الحالي إن كان فارغاً وإلا في تبويب جديد، مع إغلاق أي لوحة مفتوحة
    LaunchedEffect(incoming) {
        val u = incoming?.second ?: return@LaunchedEffect
        showTabs = false; editing = false
        val t = tabs.getOrNull(current)
        if (t != null && t.url.isBlank()) go(t, u) else openInNewTab(u)
    }
    fun dispose(t: BrowserTab) {
        GeckoPage.forget(t)
        t.engine?.close(); t.engine = null; t.engineView = null
    }
    fun openChild(u: String) {
        val p = tabs.getOrNull(current)?.id ?: -1
        tabs.add(BrowserTab(nextId++, u).also { it.parentId = p }); current = tabs.lastIndex
    }
    fun backTarget(): BrowserTab? {
        val cur = tabs.getOrNull(current) ?: return null
        tabs.firstOrNull { it.id == cur.parentId }?.let { return it }
        for (i in visit.indices.reversed()) {
            val t = tabs.firstOrNull { it.id == visit[i] }
            if (t != null && t !== cur) return t
        }
        return null
    }
    fun goBackTab() {
        val from = tabs.getOrNull(current) ?: return
        val to = backTarget() ?: return
        if (from.parentId == to.id) {
            pushClosed(from); dispose(from); tabs.remove(from)
            current = tabs.indexOf(to).coerceAtLeast(0)
        } else {
            snap(from)
            current = tabs.indexOf(to).coerceAtLeast(0)
        }
    }
    fun discard(t: BrowserTab) {   // تحرير الذاكرة مع حفظ الحالة (السجل والتمرير) لاستعادتها لاحقاً
        t.engine?.let { t.engineState = it.state }
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
        dispose(t); t.engineState = null
        t.url = ""; t.title = L("تبويب جديد"); t.canBack = false; t.canForward = false; t.loading = false; t.finding = false
    }

    LaunchedEffect(Prefs.secureScreen) {
        val f = android.view.WindowManager.LayoutParams.FLAG_SECURE
        if (Prefs.secureScreen) activity.window.setFlags(f, f) else activity.window.clearFlags(f)
    }
    fun printPage(t: BrowserTab) {
        val e = t.engine ?: return
        PdfSave.save(activity, e, (t.title.ifBlank { "Semo" } + " - " + hostOf(t.url)).take(60))
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
        openChild(SmartAssist.aiUrl(SmartAssist.pagePrompt(t.title, t.url)))
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
    LaunchedEffect(Prefs.pwMode) { fillOffer = null }
    LaunchedEffect(tab.url) { if (fillOffer?.host != Vault.norm(hostOf(tab.url))) fillOffer = null }
    fun clearData() {
        runCatching { GeckoSettings.clearData() }
        decisions.clear(); Library.clearHistory()
        toast(activity, L("تم مسح بيانات التصفح"))
    }

    fun clearCacheNow() {
        runCatching { GeckoSettings.clearCache() }
        Work.disk { runCatching { CacheCleaner.clean(activity.applicationContext) } }
        toast(activity, L("تم مسح الذاكرة المؤقتة"))
    }
    // اللوحات (الإعدادات/التنزيلات/المكتبة) تعيش في نافذة مستقلة؛ هذا الجسر هو كل ما تطلبه من المتصفح
    DisposableEffect(Unit) {
        PanelBus.clearData = { clearData() }
        PanelBus.clearCache = { clearCacheNow() }
        PanelBus.openUrl = { u -> val t = tabs.getOrNull(current); if (t != null && t.url.isBlank()) go(t, u) else openInNewTab(u) }
        onDispose { PanelBus.clearData = null; PanelBus.clearCache = null; PanelBus.openUrl = null }
    }
    LaunchedEffect(showTabs, tab.id, tab.epoch) {
        if (showTabs) kotlinx.coroutines.delay(260)
        val keep = false
        tab.engineView?.visibility = if (showTabs && !keep) View.INVISIBLE else View.VISIBLE
    }
    // عند ضغط الذاكرة: نحرر الـ WebView للتبويبات الخلفية (تُعاد عند الرجوع لها)
    DisposableEffect(Unit) {
        val cb = object : ComponentCallbacks2 {
            fun release() {
                val cur = tabs.getOrNull(current)
                tabs.toList().forEach { t -> if (t !== cur) { if (t.engine != null) discard(t); t.thumb = null } }
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
        if (am.isLowRamDevice) 2 else if (am.memoryClass >= 512) 5 else if (am.memoryClass >= 256) 4 else 3
    }
    LaunchedEffect(current, tabs.size, Adaptive.level) {
        val cap = Adaptive.liveCap(maxLive)
        val cur = tabs.getOrNull(current)
        cur?.lastUsed = android.os.SystemClock.elapsedRealtime()
        kotlinx.coroutines.delay(1200)   // بعد إنشاء الـ WebView الجديد
        val live = tabs.filter { it.engine != null }
        if (live.size > cap)
            live.filter { it !== cur }.sortedWith(compareBy({ it.pinned }, { it.lastUsed })).take(live.size - cap).forEach { discard(it) }
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
            val acMs = Prefs.autoCloseMs[Prefs.autoClose.coerceIn(0, 3)]
            if (acMs > 0L) tabs.toList().filter { it !== cur && !it.pinned && !it.isPrivate && it.lastUsed > 0 && now - it.lastUsed > acMs }.forEach { t -> val i = tabs.indexOf(t); if (i >= 0 && tabs.size > 1) closeTab(i) }
            tabs.toList().forEach { t ->
                if (t !== cur && t.engine != null && !t.pinned && !t.loading && t.lastUsed > 0 && now - t.lastUsed > idle) discard(t)
            }
        }
    }
    // عند الخروج من التطبيق: إيقاف مؤقتات الصفحات لتوفير المعالج والبطارية
    DisposableEffect(Unit) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            val curTab = tabs.getOrNull(current)
            curTab?.engine?.let { g -> if (e == androidx.lifecycle.Lifecycle.Event.ON_STOP && Prefs.pauseBg) g.setActive(false) else if (e == androidx.lifecycle.Lifecycle.Event.ON_START) g.setActive(true) }
        }
        activity.lifecycle.addObserver(obs)
        onDispose { activity.lifecycle.removeObserver(obs) }
    }

    fun formFound(t: BrowserTab, host: String) {
        if (t === tabs.getOrNull(current)) {
            val cs = Vault.forHost(host)
            if (cs.isNotEmpty()) fillOffer = FillOffer(t.id, host, cs)
            else if (GoogleAccounts.suggest && GoogleAccounts.accounts.isNotEmpty())
                fillOffer = FillOffer(t.id, host, GoogleAccounts.accounts.map { Cred("g:$it", host, it, "", 0L) })
        }
    }
    fun credFound(host: String, user: String, pass: String) {
        if (pass.isNotEmpty() && !Vault.isNever(host)) {
            val k = Vault.classify(host, user, pass)
            if (k != SaveKind.SAME) pendingSave = PendingSave(host, user, pass, k)
        }
    }
    fun fillPw(t: BrowserTab, c: Cred) = GeckoPasswords.fill(t, c)
    DisposableEffect(Unit) {
        GeckoPasswords.bind({ tabs.getOrNull(current) }, { t, host -> formFound(t, host) }, { host, user, pass -> credFound(host, user, pass) })
        onDispose { GeckoPasswords.unbind() }
    }

    val handlers = remember {
        Handlers(
            activity = activity,
            openTab = { openChild(it) },
            filePick = { intent, cb ->
                fileCb?.invoke(null); fileCb = cb
                runCatching { fileLauncher.launch(intent) }.onFailure { cb(null); fileCb = null }
            },
            androidPerm = { perms, cb -> activity.runOnUiThread { askPerms(perms, cb) } },
            sitePerm = { origin, label, perms, done ->
                activity.runOnUiThread {
                    val key = origin + "|" + label
                    fun finish(allow: Boolean) {
                        if (!allow) done(false) else askPerms(perms) { done(perms.isEmpty() || perms.any { granted(it) }) }
                    }
                    when (decisions[key]) {
                        true -> finish(true)
                        false -> done(false)
                        null -> sitePrompt = SitePrompt("Allow access?", hostOf(origin) + " wants to use your " + label,
                            { decisions[key] = true; finish(true) }, { decisions[key] = false; finish(false) })
                    }
                }
            },
            onGeckoFullScreen = { geckoFull = it },
            newPopup = { u ->
                val par = tabs.getOrNull(current)
                val nt = BrowserTab(nextId++, u)
                nt.popup = true
                nt.parentId = par?.id ?: -1
                nt.group = par?.group ?: Groups.NONE
                tabs.add(nt)
                current = tabs.lastIndex
                nt
            },
            closePopup = { t ->
                val i = tabs.indexOf(t)
                if (i >= 0) {
                    val pid = t.parentId
                    closeTab(i)
                    val pi = tabs.indexOfFirst { it.id == pid }
                    if (pi >= 0) current = pi
                }
            },
            onDownload = { u, ua, cd, mime, ref ->
                if (!Shield.downloadAllowed(hostOf(ref ?: u))) toast(activity, L("تم حظر تنزيلات تلقائية متتابعة من الصفحة"))
                else if (u.startsWith("data:", true)) BlobDownload.start(activity, u, cd, mime)
                else if (u.startsWith("blob:", true)) toast(activity, L("تعذّر تنزيل هذا الرابط"))
                else {
                    val start = {
                        Downloader.start(activity, u, ua, cd, mime, ref)
                        toast(activity, L("بدأ التنزيل — القائمة ⋮ ثم التنزيلات"))
                        askNotif()
                        Unit
                    }
                    if (Security.isRiskyFile(u, cd) || Security.isRiskyName(Downloader.smartName(u, cd, mime))) {
                        Security.log(L("تنزيل"), (L("تحذير ملف تنفيذي من ") + (hostOf(u))))
                        novaDialog(activity).setTitle(L("ملف قد يكون خطيراً"))
                            .setMessage((L("هذا النوع من الملفات (تطبيق/ملف تنفيذي) قد يضر بجهازك. نزّله فقط من مصدر تثق به.\n\n") + (hostOf(u))))
                            .setPositiveButton(L("تنزيل")) { _, _ -> start() }.setNegativeButton(L("إلغاء"), null).show()
                    } else start()
                }
            }
        )
    }

    DisposableEffect(geckoFull) {
        val c = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        if (geckoFull) {
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsetsCompat.Type.systemBars())
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        onDispose {
            c.show(WindowInsetsCompat.Type.systemBars())
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    BackHandler(enabled = tab.canBack) { tab.engine?.back() }
    BackHandler(enabled = tab.url.isNotBlank() && !tab.canBack) { home(tab) }   // آخر صفحة في السجل: العودة للرئيسية بدل إغلاق التطبيق
    BackHandler(enabled = !tab.canBack && (tabs.any { it.id == tab.parentId } || visit.any { id -> id != tab.id && tabs.any { it.id == id } })) { goBackTab() }
    BackHandler(enabled = tab.finding) { tab.engine?.clearFind(); tab.finding = false }
    BackHandler(enabled = editing) { editing = false }
    BackHandler(enabled = showTabs) { showTabs = false }
    BackHandler(enabled = geckoFull) { tabs.getOrNull(current)?.engine?.exitFullScreen() }

    // ربط الـ Activity: مزوّد الـ WebView الحالي + تفعيل الدخول التلقائي للنافذة المنبثقة أثناء تشغيل فيديو يوتيوب
    val mainAct = activity as? MainActivity
    SideEffect {
        mainAct?.tabProvider = { tabs.getOrNull(current) }
        mainAct?.netTabs = { tabs.toList() }
    }
    LaunchedEffect(Prefs.js, Prefs.textZoom, Prefs.siteDark, Prefs.theme, LocalIos.current.dark, GeckoCfg.key()) { runCatching { GeckoSettings.update(); GeckoCfg.push() } }
    DisposableEffect(Unit) {
        GeckoPage.register(activity) { u ->
            tabs.firstOrNull { it.engine != null && it.url == u } ?: tabs.getOrNull(current)?.takeIf { it.engine != null }
        }
        onDispose { GeckoPage.unregister(activity) }
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
        }
    }

    Box(Modifier.fillMaxSize()) {
        Surface(Modifier.fillMaxSize().menuBlur(), color = if (inPip) Color.Black else iosC.bg) {
            // وضع التطبيق: الارتفاع يتبدّل فوراً (تحريك ارتفاع الـ WebView كل إطار يسبب تقطيعاً)، والشريط نفسه ينزلق على طبقة الرسم
            val site: Any? = null
            val barH = 0.dp
            val tintTarget = if (tab.url.isBlank()) iosC.groupedBg else (tab.topColor?.let { Color(it) } ?: iosC.bg)
            val stColor by animateColorAsState(if (site != null) iosC.chrome else tintTarget, tween(Adaptive.ms(220)), label = "statusTint")
            SideEffect {
                WindowInsetsControllerCompat(activity.window, activity.window.decorView).isAppearanceLightStatusBars = (if (site != null) iosC.chrome.compositeOver(iosC.bg) else stColor).luminance() > 0.5f
            }
            if (site == null && !inPip) Box(
                Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(stColor)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { tab.engine?.scrollTop() }
            )
            // لون شريط الحالة يتبع لون شريط الموقع (كان خلفية سادة بينما الشريط مائل للون الهوية)
            val bandColor = iosC.chrome.compositeOver(iosC.bg)
            val bandH = WindowInsets.statusBars.getTop(androidx.compose.ui.platform.LocalDensity.current).toFloat()
            Box(
                Modifier.fillMaxSize()
                    .then(if (site != null) Modifier.drawBehind { drawRect(bandColor, size = androidx.compose.ui.geometry.Size(size.width, bandH)) } else Modifier)
                    .statusBarsPadding().displayCutoutPadding()
            ) {
                Box(Modifier.fillMaxSize().padding(top = barH).pageInsets({ imeSettled.value }, navInsets, pageTyping, inPip).background(iosC.bg)) {
                    AnimatedContent(
                        targetState = tab.id to tab.url.isBlank(),
                        transitionSpec = {
                            if (targetState.second) fadeIn(tween(Adaptive.ms(180), easing = NovaMotion.emphasized)) togetherWith fadeOut(tween(Adaptive.ms(110)))
                            else fadeIn(tween(Adaptive.ms(200), easing = NovaMotion.emphasized)) togetherWith fadeOut(tween(Adaptive.ms(110)))
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
                                    factory = { ctx -> geckoContainer(ctx, tb, handlers, primaryInt, bgInt, spinnerInt) }
                                )
                                // التبويب غير الظاهر يُوقَف مؤقتاً (يوفر المعالج والذاكرة) ويُستأنف عند ظهوره
                                DisposableEffect(tb.id, tb.epoch) {
                                    tb.engine?.setActive(true)
                                    onDispose { tb.engine?.setActive(false) }
                                }
                            }
                        }
                    }
                }
                val botTarget = if (tab.url.isBlank()) iosC.groupedBg else ((tab.botColor ?: tab.topColor)?.let { Color(it) } ?: iosC.bg)
                val botColor by animateColorAsState(botTarget, tween(Adaptive.ms(220)), label = "botTint")
                val barIos = if (botColor.luminance() < 0.5f) IosDark else IosLight
                if (!inPip) {
                    SideEffect { activity.window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(botColor.toArgb())) }
                    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                        Box(Modifier.fillMaxWidth().height(UiLayout.BAR_H.dp).background(botColor))
                        Box(Modifier.fillMaxWidth().windowInsetsBottomHeight(WindowInsets.navigationBars).background(botColor))
                    }
                }
                AnimatedVisibility(
                    visible = !inPip && !pageTyping, modifier = Modifier.align(Alignment.BottomCenter),
                    enter = androidx.compose.animation.slideInVertically(tween(Adaptive.ms(300), easing = NovaMotion.emphasized)) { it / 2 } + fadeIn(tween(Adaptive.ms(180))),
                    exit = androidx.compose.animation.slideOutVertically(tween(Adaptive.ms(180), easing = NovaMotion.emphasizedAccel)) { it / 3 } + fadeOut(tween(Adaptive.ms(110)))
                ) {
                CompositionLocalProvider(LocalIos provides barIos, LocalContentColor provides barIos.label) {
                if (tab.finding) key(tab.id) { FindBar(tab) } else BottomPill(
                    tab = tab, tabCount = tabs.size, editing = editing, setEditing = { editing = it },
                    onGo = { go(tab, it) }, onTabs = { openTabs() }, onNewTab = { newTab() }, onHome = { home(tab) },
                    onFind = { tab.findInfo = ""; tab.finding = true },
                    onDesktop = { tab.desktop = !tab.desktop; tab.engine?.setDesktop(tab.desktop) },
                    onShare = { shareText(activity, tab.url) }, onCopy = { copyText(activity, tab.url) },
                    onDownloads = { openPanel(activity, "downloads") }, onSettings = { openPanel(activity, "settings") }, onTranslate = { translatePage(tab) }, onPrint = { printPage(tab) }, onLibrary = { openPanel(activity, "library") }, onCloseCurrent = { closeTab(current) },
                    onSummarize = {
                        GeckoPage.exec(tab, "summarize.js") { r -> SmartAssist.summarizeRaw(activity, r, tab.url) { u -> openChild(u) } }
                    }, onAskAi = { askPage(tab) },
                    onSwitch = { d -> snap(tab); current = (current + d).coerceIn(0, tabs.lastIndex) }
                )
                }
                }
                // زر المنبثقة لا يعتمد على وضع التطبيق: يظهر فوق الشريط السفلي في صفحة فيديو يوتيوب حين يكون الشريط العلوي معطّلاً
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
                onClose = { i -> closeTab(i) }, onNew = { g -> newTab(g) }, onCloseAll = { closeAll() }, onBack = { showTabs = false },
                onDuplicate = { i -> duplicate(i) }, onCloseOthers = { i -> closeOthers(i) }, onCloseTag = { g -> closeByTag(g) },
                canReopen = closedTabs.isNotEmpty(), onReopen = { reopenClosed() }, onCloseDuplicates = { closeDuplicates() }
            )
        }
        fillOffer?.takeIf { it.tabId == tab.id && !inPip && !editing }?.let { o ->
            FillBanner(
                o, modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp),
                onClose = { fillOffer = null },
                onFill = { c ->
                    if (c.pass.isEmpty()) { fillPw(tab, c); fillOffer = null }   // بريد الحساب: لا يحتاج بصمة
                    else Auth.run(activity, L("تأكيد الهوية لتعبئة كلمة المرور")) { fillPw(tab, c); fillOffer = null }
                }
            )
        }
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
