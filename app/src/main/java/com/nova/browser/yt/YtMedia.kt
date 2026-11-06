package com.nova.browser

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.webkit.WebView
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.net.URL

/** سجل تشخيص تشغيل يوتيوب (يُعرض في الإعدادات ويمكن نسخه). */
object YtLog {
    private val lines = ArrayDeque<String>()
    private val fmt = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US)
    @Synchronized fun add(m: String) {
        lines.addLast(fmt.format(java.util.Date()) + "  " + m)
        while (lines.size > 160) lines.removeFirst()
    }
    @Synchronized fun text(): String = lines.joinToString("\n")
    @Synchronized fun clear() = lines.clear()
}

/** حالة تشغيل يوتيوب الحالية + أوامر التحكم من إشعار الوسائط/شاشة القفل/Now Bar. */
object YtMedia {
    @Volatile var playing = false
    @Volatile var title = ""
    @Volatile var artist = ""
    @Volatile var art: Bitmap? = null
    @Volatile var pos = 0L
    @Volatile var dur = 0L
    @Volatile var vw = 0          // أبعاد الفيديو الحقيقية: نسبة النافذة المنبثقة (طولي للـ Shorts)
    @Volatile var vh = 0
    @Volatile var videoUrl = ""      // last watch URL seen (for the audio hand-off)
    @Volatile private var lastPlayAt = 0L
    @Volatile private var lastHandoff = 0L
    var owner: BrowserTab? = null
    private var wvRef: WeakReference<WebView>? = null
    private var artUrl = ""

    fun control(a: String) {
        if (YtAudio.active) { YtAudio.control(a); return }
        owner?.takeIf { GeckoYt.owns(it) }?.let { GeckoYt.control(it, a); return }   // محرك Gecko: الأمر يمرّ عبر إضافة Semo
        val w = wvRef?.get() ?: return
        w.post {
            // الصفحة قد تكون مجمّدة (onPause/pauseTimers) فنوقظها قبل تنفيذ الأمر؛ وقد تكون دُمّرت: لا نُسقط التطبيق
            runCatching {
                if (a != "pause") { w.resumeTimers(); w.onResume() }
                w.evaluateJavascript("window.__novaYtCtl&&window.__novaYtCtl(${JSONObject.quote(a)})", null)
            }
        }
    }

    fun update(ctx: Context, tab: BrowserTab, wv: WebView, o: JSONObject) {
        wvRef = WeakReference(wv)
        applyState(ctx, tab, wv.url, o)
    }

    /** نفس update لكن لصفحة Gecko (لا WebView): الرابط من التبويب. */
    fun updateGecko(ctx: Context, tab: BrowserTab, url: String?, o: JSONObject) {
        wvRef = null
        applyState(ctx, tab, url, o)
    }

    private fun applyState(ctx: Context, tab: BrowserTab, pageUrl: String?, o: JSONObject) {
        owner = tab
        playing = o.optBoolean("playing"); pos = o.optLong("pos"); dur = o.optLong("dur")
        if (playing) { lastPlayAt = System.currentTimeMillis(); if (YtAudio.active) YtAudio.release() }   // the page plays again: audio-only ends
        pageUrl?.takeIf { isYtVideo(it) }?.let { if (it != videoUrl && tab.app) YtAudio.warm(it); videoUrl = it }
        vw = o.optInt("vw"); vh = o.optInt("vh")
        title = o.optString("title"); artist = o.optString("artist")
        loadArt(o.optString("art"))
        val svc = MediaService.instance
        if (svc != null) svc.refresh()
        else if (playing && title.isNotBlank())
            runCatching { ContextCompat.startForegroundService(ctx, Intent(ctx, MediaService::class.java)) }
    }

    /** Loads the artwork (trusted YouTube/Google hosts only) off the main thread, then refreshes the notification. */
    fun loadArt(url: String) {
        if (url.isNotBlank() && url != artUrl && isTrustedArt(url)) {
            artUrl = url
            Work.img {
                runCatching {
                    val c = URL(url).openConnection().apply { connectTimeout = 8000; readTimeout = 8000 }
                    val bmp = c.getInputStream().use { BitmapFactory.decodeStream(it) }
                    if (bmp != null) {
                        val k = 512f / maxOf(bmp.width, bmp.height)
                        art = if (k < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt(), (bmp.height * k).toInt(), true) else bmp
                        MediaService.instance?.refresh()
                    }
                }
            }
        }
    }

    // نجلب الصور من خوادم يوتيوب/جوجل فقط (لا نطلب روابط عشوائية جاءت من الصفحة)
    private fun isTrustedArt(u: String): Boolean {
        val h = runCatching { java.net.URI(u) }.getOrNull()?.takeIf { it.scheme == "https" }?.host ?: return false
        return h.endsWith("ytimg.com") || h.endsWith("ggpht.com") || h.endsWith("googleusercontent.com")
    }

    /** يُنهي الإشعار والخدمة؛ pause=true يوقف الفيديو أيضاً (إغلاق النافذة المنبثقة). */
    fun stop(pause: Boolean = false) {
        if (pause) control("pause")
        YtAudio.release()
        playing = false; owner = null; vw = 0; vh = 0; MediaService.instance?.shutdown()
    }
    /** Audio hand-off failed: let the page player continue (returns false when there is no page to resume). */
    fun resumePage(): Boolean {
        owner?.takeIf { GeckoYt.owns(it) }?.let { GeckoYt.control(it, "play"); return true }
        val w = wvRef?.get() ?: return false
        w.post {
            runCatching {
                w.resumeTimers(); w.onResume()
                w.evaluateJavascript("window.__novaYtCtl&&window.__novaYtCtl('play')", null)
            }
        }
        return true
    }
    fun killPlayback() {
        owner?.takeIf { GeckoYt.owns(it) }?.let { GeckoYt.control(it, "pause") }
        val w = wvRef?.get()
        if (w != null) runCatching {
            w.evaluateJavascript("window.__novaYtCtl&&window.__novaYtCtl('pause')", null)
            w.onPause()
        }
        YtAudio.release()
        playing = false; owner = null; vw = 0; vh = 0; MediaService.instance?.shutdown()
    }
    /**
     * Leaving a video page: in an installed app the sound continues as audio-only (in-app mini player);
     * everywhere else playback ends as before.
     */
    fun pageChanged(tab: BrowserTab, url: String) {
        if (owner !== tab || isYtVideo(url)) return
        if (YtAudio.active) return
        val ctx = wvRef?.get()?.context?.applicationContext ?: GeckoYt.appContext
        val recent = playing || System.currentTimeMillis() - lastPlayAt < 2500L
        if (tab.app && ctx != null && recent && videoUrl.isNotBlank()) {
            val u = videoUrl; val p = pos; val t = title; val a = artist
            val now = System.currentTimeMillis()
            if (now - lastHandoff < 1200L) return
            lastHandoff = now
            control("pause")                      // pauses the page video (audio engine not active yet)
            YtAudio.start(ctx, u, p, t, a, true)
        } else stop()
    }
    fun tabClosed(tab: BrowserTab) { if (owner === tab) stop() }
}

/** جسر MEDIA في yt.js: يستقبل حالة التشغيل والعنوان والصورة. السكربت نفسه يحقنه YtHub. */
object YtBridge {
    val origins = setOf("https://m.youtube.com", "https://www.youtube.com", "https://music.youtube.com", "https://youtube.com")
    private val listenerOk by lazy { WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) }

    fun listen(wv: WebView, tab: BrowserTab, h: Handlers) {
        if (!listenerOk) return
        runCatching {
            WebViewCompat.addWebMessageListener(wv, "NovaYt", origins, WebViewCompat.WebMessageListener { view, message, _, isMain, _ ->
                if (!isMain) return@WebMessageListener
                val data = message.data ?: return@WebMessageListener
                if (data.length > 6000) return@WebMessageListener
                val o = runCatching { JSONObject(data) }.getOrNull() ?: return@WebMessageListener
                val type = o.optString("t")
                if (type == "log") { YtLog.add(o.optString("m")); return@WebMessageListener }
                if (type != "state") return@WebMessageListener
                tab.ytPlaying = o.optBoolean("playing")
                if (!isYtVideo(view.url ?: "")) return@WebMessageListener   // لا إشعار لمعاينات الصفحة الرئيسية
                YtMedia.update(view.context.applicationContext, tab, view, o)
                h.onYtState(tab)
            })
        }
    }
}
