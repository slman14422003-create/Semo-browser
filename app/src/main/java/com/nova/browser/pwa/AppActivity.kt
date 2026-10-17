package com.nova.browser

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.View
import android.view.ViewGroup
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay

class AppActivity : ComponentActivity() {
    companion object {
        private var seq = 9000

        fun intentFor(ctx: Context, home: String): Intent =
            Intent(Intent.ACTION_VIEW, Uri.parse(home), ctx, AppActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private val tab = BrowserTab(seq++)
    private var startUrl = ""
    private var appName = ""
    private var fileCb: ValueCallback<Array<Uri>>? = null
    private var custom by mutableStateOf<View?>(null)
    private var customCb: WebChromeClient.CustomViewCallback? = null
    private var inPip by mutableStateOf(false)
    private var configTick by mutableStateOf(0)
    private var wasPip = false
    private val permQueue = ArrayDeque<Pair<List<String>, () -> Unit>>()
    private var permBusy = false
    private var permCur: (() -> Unit)? = null

    private val fileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        fileCb?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res.resultCode, res.data))
        fileCb = null
    }

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permBusy = false
        val c = permCur
        permCur = null
        c?.invoke()
        pumpPerms()
    }

    private fun granted(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun pumpPerms() {
        if (permBusy) return
        val next = permQueue.removeFirstOrNull() ?: return
        val need = next.first.filter { !granted(it) }
        if (need.isEmpty()) {
            next.second()
            pumpPerms()
            return
        }
        permBusy = true
        permCur = next.second
        runCatching { permLauncher.launch(need.toTypedArray()) }.onFailure {
            permBusy = false
            permCur = null
            next.second()
            pumpPerms()
        }
    }

    private fun askPerms(perms: List<String>, done: () -> Unit) {
        permQueue.addLast(perms to done)
        pumpPerms()
    }

    private fun boot() {
        val c = applicationContext
        CrashLog.install(c)
        Robust.init(c)
        Prefs.init(c)
        SiteStore.init(c)
        GoogleAccounts.init(c)
        Adaptive.init(c)
        Library.init(c)
        Thread({ Vault.init(c) }, "nova-vault").start()
        Security.init(c)
        Perf.init(c)
        PageExtras.preheat(c)
        Shield.init(c)
        WebEngine.init(c)
        NetKit.init(c)
        WebSupport.init(c)
        Downloader.init(c)
        Notif.ensureChannels(c)
        MainActivity.initialized = true
    }

    private fun openExternal(url: String) {
        runCatching {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url), this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private fun canPip() = tab.yt && YtMedia.owner === tab && YtMedia.playing && isYtVideo(tab.url)

    private fun pipParams(): PictureInPictureParams {
        val w = YtMedia.vw
        val h = YtMedia.vh
        val raw = if (w > 0 && h > 0) Rational(w, h) else Rational(16, 9)
        val f = raw.toFloat()
        val ratio = if (f > 2.39f) Rational(239, 100) else if (f < 0.4184f) Rational(100, 239) else raw
        val b = PictureInPictureParams.Builder().setAspectRatio(ratio)
        b.setAutoEnterEnabled(Prefs.autoPip && canPip()).setSeamlessResizeEnabled(false)
        return b.build()
    }

    private fun refreshPip() {
        runCatching { setPictureInPictureParams(pipParams()) }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
        wasPip = wasPip || isInPictureInPictureMode
        YtWeb.pip = isInPictureInPictureMode
        val w = tab.webView
        if (YtWeb.owns(w)) YtWeb.onPip(w, isInPictureInPictureMode, custom != null)
    }

    private fun reapplyInsets() {
        val root = window.decorView
        ViewCompat.requestApplyInsets(root)
        root.requestLayout()
    }

    private fun refitWeb(w: WebView) {
        val lp = w.layoutParams
        if (lp != null) w.layoutParams = lp
        (w.parent as? View)?.requestLayout()
        w.requestLayout()
        w.invalidate()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        if (inPip || isInPictureInPictureMode) return
        configTick++
        val root = window.decorView
        for (d in longArrayOf(0L, 120L, 400L, 900L)) root.postDelayed({
            if (isFinishing || isDestroyed) return@postDelayed
            reapplyInsets()
            tab.webView?.let { refitWeb(it) }
        }, d)
        val w = tab.webView ?: return
        if (YtWeb.owns(w)) {
            YtWeb.afterRotate(w)
        } else {
            for (d in longArrayOf(120L, 450L, 1000L)) w.postDelayed({
                w.requestLayout()
                w.invalidate()
                w.evaluateJavascript("window.dispatchEvent(new Event('resize'))", null)
            }, d)
        }
    }

    override fun onStart() {
        super.onStart()
        val w = tab.webView
        w?.onResume()
        w?.resumeTimers()
        if (!inPip && YtWeb.owns(w)) {
            YtWeb.background(w, false)
            YtWeb.recover(w)
        }
    }

    override fun onResume() {
        super.onResume()
        tab.webView?.let {
            it.onResume()
            it.resumeTimers()
        }
    }

    override fun onPause() {
        super.onPause()
        if (Prefs.ytBg && YtMedia.owner === tab) YtWeb.background(tab.webView, true)
    }

    override fun onStop() {
        super.onStop()
        Perf.flushCookies(true)
        val w = tab.webView
        if (wasPip && !isInPictureInPictureMode && !isFinishing) {
            wasPip = false
            inPip = false
            YtWeb.pip = false
            YtMedia.stop(pause = true)
            return
        }
        val live = Prefs.ytBg && YtMedia.owner === tab
        if (live) YtWeb.background(w, true)
        else if (Prefs.pauseBg && custom == null) w?.onPause()
    }

    override fun onDestroy() {
        if (YtMedia.owner === tab) YtMedia.killPlayback()
        custom = null
        runCatching { customCb?.onCustomViewHidden() }
        tab.webView?.let { w ->
            (w.parent as? ViewGroup)?.removeView(w)
            runCatching {
                w.stopLoading()
                w.loadUrl("about:blank")
                w.onPause()
                w.destroy()
            }
        }
        tab.webView = null
        super.onDestroy()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        boot()
        window.attributes = window.attributes.apply {
            rotationAnimation = android.view.WindowManager.LayoutParams.ROTATION_ANIMATION_CROSSFADE
            layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        val data = intent?.data
        if (data == null || (data.scheme != "https" && data.scheme != "http")) {
            finishAndRemoveTask()
            return
        }
        startUrl = data.toString()
        val host = hostOf(startUrl)
        appName = Pwa.nameFor(host) ?: host
        tab.app = true
        tab.appHome = startUrl
        tab.url = startUrl
        tab.title = appName

        val handlers = Handlers(
            activity = this,
            chooser = { cb, p ->
                fileCb?.onReceiveValue(null)
                fileCb = cb
                runCatching { fileLauncher.launch(p.createIntent()) }.onFailure {
                    cb.onReceiveValue(null)
                    fileCb = null
                }
                true
            },
            permission = { req ->
                runOnUiThread {
                    val av = req.resources.filter {
                        it == PermissionRequest.RESOURCE_VIDEO_CAPTURE || it == PermissionRequest.RESOURCE_AUDIO_CAPTURE
                    }
                    val prot = req.resources.filter { it == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID }.toTypedArray()
                    if (av.isEmpty()) {
                        if (prot.isNotEmpty()) req.grant(prot) else req.deny()
                    } else {
                        val perms = av.map {
                            if (it == PermissionRequest.RESOURCE_VIDEO_CAPTURE) Manifest.permission.CAMERA else Manifest.permission.RECORD_AUDIO
                        }
                        askPerms(perms) {
                            val ok = av.filter {
                                granted(if (it == PermissionRequest.RESOURCE_VIDEO_CAPTURE) Manifest.permission.CAMERA else Manifest.permission.RECORD_AUDIO)
                            }.toTypedArray()
                            if (ok.isEmpty() && prot.isEmpty()) req.deny() else req.grant(ok + prot)
                        }
                    }
                }
            },
            geo = { origin, cb ->
                runOnUiThread {
                    val perms = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                    askPerms(perms) { cb.invoke(origin, perms.any { granted(it) }, false) }
                }
            },
            openTab = { openExternal(it) },
            showCustom = { v, cb ->
                if (custom != null && custom !== v) {
                    custom?.let { (it.parent as? ViewGroup)?.removeView(it) }
                    runCatching { customCb?.onCustomViewHidden() }
                }
                custom = v
                customCb = cb
                tab.webView?.takeIf { YtWeb.owns(it) }?.let { YtWeb.onFullscreen(it, true) }
            },
            hideCustom = {
                custom?.let { v -> (v.parent as? ViewGroup)?.removeView(v) }
                custom = null
                customCb = null
                tab.webView?.takeIf { YtWeb.owns(it) }?.let {
                    YtWeb.onFullscreen(it, false)
                    YtWeb.afterFullscreen(it)
                }
            },
            onDownload = { u, ua, cd, mime, ref ->
                if (!Shield.downloadAllowed(hostOf(ref ?: u))) toast(this, "Download blocked")
                else if (u.startsWith("blob:") || u.startsWith("data:")) toast(this, "Download not supported")
                else {
                    Downloader.start(this, u, ua, cd, mime, ref)
                    toast(this, "Download started")
                }
            },
            onYtState = { refreshPip() }
        )

        setContent {
            NovaTheme(this) {
                AppHost(handlers)
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun AppHost(handlers: Handlers) {
        val cs = MaterialTheme.colorScheme
        val bg = tab.topColor?.let { Color(it) } ?: cs.background
        val fullscreen = custom != null
        @Suppress("UNUSED_VARIABLE") val tick = configTick
        var splash by remember { mutableStateOf(true) }
        val icon = remember { Favicons.get(hostOf(startUrl)) }

        LaunchedEffect(tab.progress, tab.loading) {
            if ((!tab.loading && tab.progress > 0f) || tab.progress >= 0.7f) splash = false
        }
        LaunchedEffect(Unit) {
            delay(6000)
            splash = false
        }
        LaunchedEffect(Unit) {
            snapshotFlow { tab.topColor }.collect { c ->
                val color = (c ?: android.graphics.Color.BLACK) or (0xFF shl 24)
                window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(color))
                runCatching {
                    @Suppress("DEPRECATION")
                    setTaskDescription(ActivityManager.TaskDescription(appName, Favicons.get(hostOf(startUrl)), color))
                }
            }
        }
        SideEffect {
            val c = WindowInsetsControllerCompat(window, window.decorView)
            c.isAppearanceLightStatusBars = bg.luminance() > 0.5f
            c.isAppearanceLightNavigationBars = bg.luminance() > 0.5f
        }
        DisposableEffect(fullscreen) {
            val c = WindowInsetsControllerCompat(window, window.decorView)
            if (fullscreen) {
                c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                c.hide(WindowInsetsCompat.Type.systemBars())
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
            onDispose {
                c.show(WindowInsetsCompat.Type.systemBars())
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                window.decorView.post { reapplyInsets() }
            }
        }

        BackHandler(enabled = fullscreen) { customCb?.onCustomViewHidden() }
        BackHandler(enabled = !fullscreen && tab.canBack) { tab.webView?.let { WebSupport.goBackFast(it) } }
        BackHandler(enabled = !fullscreen && !tab.canBack) { finishAndRemoveTask() }

        Box(
            Modifier
                .fillMaxSize()
                .background(bg)
                .then(
                    if (fullscreen || inPip) Modifier
                    else Modifier
                        .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
                        .imePadding()
                )
        ) {
            key(tab.epoch) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        val wv = if (YtWeb.isYtUrl(startUrl)) YtWeb.create(ctx, tab, handlers) else createWebView(ctx, tab, handlers)
                        tab.webView = wv
                        wv.visibility = View.VISIBLE
                        wv.onResume()
                        wv.resumeTimers()
                        if (YtWeb.isYtUrl(startUrl)) wv.loadUrl(startUrl) else wv.loadUrl(startUrl, Perf.privacyHeaders)
                        FrameLayout(ctx).apply {
                            addView(wv, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                        }
                    }
                )
            }

            if (tab.loading && !inPip && !fullscreen) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth(tab.progress.coerceIn(0.04f, 1f))
                        .height(2.dp)
                        .background(cs.primary)
                )
            }

            custom?.let { v ->
                AndroidView(
                    modifier = Modifier.fillMaxSize().background(Color.Black),
                    factory = {
                        (v.parent as? ViewGroup)?.removeView(v)
                        v
                    }
                )
            }

            AnimatedVisibility(visible = splash && !inPip, exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(cs.background), contentAlignment = Alignment.Center) {
                    androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (icon != null) {
                            Image(
                                bitmap = icon.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(84.dp).clip(RoundedCornerShape(20.dp))
                            )
                        }
                        androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))
                        Text(appName, style = MaterialTheme.typography.titleMedium, color = cs.onBackground)
                    }
                }
            }
        }
    }
}
