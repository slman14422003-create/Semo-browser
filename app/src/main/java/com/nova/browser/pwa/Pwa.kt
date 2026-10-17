package com.nova.browser

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import java.util.concurrent.ConcurrentHashMap
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/** نوع الصفحة من منظور «وضع التطبيق». */
enum class SiteKind { NONE, YT, YT_VIDEO, AI, APP }

/** هوية الموقع المعروضة في الشريط العلوي. */
class SiteInfo(val kind: SiteKind, val name: String, val host: String)

/**
 * وضع PWA لمواقع الذكاء الاصطناعي + الشريط العلوي ليوتيوب:
 * - يكتشف نوع الصفحة (يوتيوب / فيديو / ذكاء اصطناعي) لعرض الشريط العلوي المناسب.
 * - يحقن pwa.js على مواقع الذكاء الاصطناعي فقط (محاكاة standalone، مظهر تطبيق). يوتيوب لا يُحقن فيه: له WebView مخصّص.
 */
object Pwa {
    const val BAR_H = UiLayout.BAR_DP   // dp: ارتفاع الشريط العلوي (يشمل خط التقدّم) — القيمة في UiLayout

    private val aiNames = mapOf(
        "gemini.google.com" to "Gemini", "chatgpt.com" to "ChatGPT", "chat.openai.com" to "ChatGPT",
        "claude.ai" to "Claude", "perplexity.ai" to "Perplexity", "copilot.microsoft.com" to "Copilot",
        "chat.deepseek.com" to "DeepSeek", "grok.com" to "Grok", "poe.com" to "Poe", "chat.mistral.ai" to "Le Chat"
    )

    /** النطاقات التي يُحقن فيها pwa.js (الدالة داخل السكربت تضيّق google.com على udm=50 فقط). */
    val origins: Set<String> = buildSet {
        add("https://www.google.com")   // يوتيوب خارج القائمة عمداً: له WebView مخصّص (YtWeb) بلا pwa.js
        aiNames.keys.forEach { add("https://$it") }
        add("https://www.perplexity.ai")
    }

    // ---------- تطبيقات مثبّتة (أي موقع يُثبَّت كتطبيق مستقل) ----------
    private val apps = ConcurrentHashMap<String, String>()
    @Volatile private var loaded = false

    private fun ensure(c: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            runCatching {
                ConfStore.open(c).getString("pwa_apps", "").split(";;").forEach { e ->
                    val i = e.indexOf('|')
                    if (i > 0) apps[e.substring(0, i)] = e.substring(i + 1)
                }
            }
            loaded = true
        }
    }

    private fun save(c: Context) {
        ConfStore.open(c).putString("pwa_apps", apps.entries.joinToString(";;") { it.key + "|" + it.value })
        lastUrl = null; lastInfo = null
        publishShortcuts(c)
    }

    /** اختصارات ديناميكية: الضغط المطوّل على أيقونة Nova يعرض التطبيقات المثبّتة (أحدث 4) لفتحها مباشرة. */
    fun publishShortcuts(c: Context) {
        runCatching {
            val list = apps.entries.toList().takeLast(4).map { (host, name) ->
                ShortcutInfoCompat.Builder(c, "dyn_$host").setShortLabel(name).setLongLabel(name)
                    .setIcon(IconCompat.createWithBitmap(Favicons.get(host)?.let { Bitmap.createScaledBitmap(it, 192, 192, true) } ?: letterIcon(host, name)))
                    .setIntent(AppActivity.intentFor(c, homeFor("https://$host/"))).build()
            }
            ShortcutManagerCompat.setDynamicShortcuts(c, list)
        }
    }

    fun isInstalled(host: String): Boolean = apps.containsKey(host.removePrefix("www."))

    fun nameFor(host: String): String? = apps[host.removePrefix("www.")]

    fun homeFor(url: String): String {
        val h = hostOf(url)
        return if (YtWeb.isYtHost(h)) "https://m.youtube.com/" else "https://$h/"
    }

    fun launch(ctx: Context, url: String) {
        ensure(ctx)
        runCatching { ctx.startActivity(AppActivity.intentFor(ctx, homeFor(url))) }
    }

    private fun installedOrigins(): Set<String> = apps.keys.map { "https://$it" }.toSet() + apps.keys.map { "https://www.$it" }.toSet()

    private fun cfgJson(): String {
        val arr = org.json.JSONArray()
        apps.keys.forEach { arr.put(it); arr.put("www.$it") }
        return "{\"v\":3,\"anim\":${Prefs.smoothAnim && Adaptive.level == 0},\"hap\":${Prefs.pwaHaptics},\"apps\":$arr}"
    }

    private fun letterIcon(host: String, label: String): Bitmap {
        val b = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val hue = ((host.hashCode().toLong().let { if (it < 0) -it else it }) % 360).toFloat()
        p.color = android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.55f, 0.72f))
        c.drawRoundRect(0f, 0f, 192f, 192f, 44f, 44f, p)
        p.color = android.graphics.Color.WHITE; p.textSize = 108f; p.textAlign = Paint.Align.CENTER; p.isFakeBoldText = true
        c.drawText(label.take(1).uppercase(), 96f, 96f + 38f, p)
        return b
    }

    fun pinShortcut(ctx: Context, url: String) {
        ensure(ctx)
        val host = hostOf(url)
        val name = apps[host] ?: host
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(ctx)) {
            toast(ctx, "Launcher does not support pinning")
            return
        }
        val ok = runCatching {
            val icon = IconCompat.createWithBitmap(Favicons.get(host)?.let { Bitmap.createScaledBitmap(it, 192, 192, true) } ?: letterIcon(host, name))
            val sc = ShortcutInfoCompat.Builder(ctx, "app_" + host + "_" + System.currentTimeMillis()).setShortLabel(name).setLongLabel(name).setIcon(icon)
                .setIntent(AppActivity.intentFor(ctx, homeFor(url))).build()
            ShortcutManagerCompat.requestPinShortcut(ctx, sc, null)
        }.getOrDefault(false)
        if (!ok) toast(ctx, "Could not add shortcut")
    }

    /** يثبّت الموقع كتطبيق (اختصار على الشاشة الرئيسية + وضع التطبيق) أو يزيله إن كان مثبّتاً. يعيد true إن صار مثبّتاً. */
    fun togglePin(ctx: Context, url: String, title: String): Boolean {
        ensure(ctx)
        val host = hostOf(url)
        if (!url.startsWith("https://") || host.isBlank()) return false
        if (apps.containsKey(host)) {
            apps.remove(host); save(ctx)
            toast(ctx, "App removed")
            return false
        }
        val name = (title.ifBlank { host }).replace("|", " ").replace(";;", " ").trim().take(30)
        apps[host] = name; save(ctx)
        pinShortcut(ctx, url)
        return true
    }

    private val js: String? by lazy {
        runCatching { Perf.asset("pwa.js") }.getOrNull()
    }

    private val docStart by lazy { WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) }
    private val listenerOk by lazy { WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) }

    /** يثبّت جسر NovaPwa (مشاركة أصلية + اهتزاز) ثم يحقن pwa.js مع إعدادات التشغيل. */
    fun install(wv: WebView) {
        val base = js ?: return
        if (!Prefs.pwaMode) return
        ensure(wv.context)
        val allOrigins = origins + installedOrigins()
        if (listenerOk) runCatching {
            WebViewCompat.addWebMessageListener(wv, "NovaPwa", allOrigins, WebViewCompat.WebMessageListener { view, message, _, isMain, _ ->
                if (!isMain) return@WebMessageListener
                val data = message.data ?: return@WebMessageListener
                if (data.length > 4000) return@WebMessageListener
                val o = runCatching { org.json.JSONObject(data) }.getOrNull() ?: return@WebMessageListener
                when (o.optString("t")) {
                    "share" -> { val t = o.optString("text").take(2000); if (t.isNotBlank()) runCatching { shareText(view.context, t) } }
                    "hap" -> if (Prefs.pwaHaptics) view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                }
            })
        }
        if (docStart) {
            runCatching { WebViewCompat.addDocumentStartJavaScript(wv, base.replace("__CFG__", cfgJson()), allOrigins) }
        }
    }

    /**
     * احتياطي للأجهزة التي لا تدعم الحقن قبل الصفحة (DOCUMENT_START_SCRIPT): كان pwa.js لا يعمل عليها إطلاقاً.
     * يُنفَّذ عند بدء الصفحة وللنطاقات المسموحة فقط؛ السكربت نفسه يحمي من التنفيذ المزدوج (__novaPwa).
     */
    fun onPageStart(wv: WebView, url: String) {
        if (docStart || !Prefs.pwaMode) return
        val base = js ?: return
        val o = runCatching { Uri.parse(url) }.getOrNull() ?: return
        ensure(wv.context)
        if (o.scheme != "https" || "https://${o.host}" !in (origins + installedOrigins())) return
        wv.evaluateJavascript(base.replace("__CFG__", cfgJson()), null)
    }

    /** يمرّر الصفحة (أو أكبر حاوية تمرير فيها) إلى الأعلى بحركة ناعمة — عند الضغط على اسم الموقع في الشريط. */
    fun scrollTop(wv: WebView?) { wv?.evaluateJavascript("window.__novaTop&&window.__novaTop()", null) }

    @Volatile private var lastUrl: String? = null
    @Volatile private var lastInfo: SiteInfo? = null

    /** معلومات الموقع أو null إن لم يكن ضمن وضع التطبيق. مُخزَّنة لآخر رابط لأنها تُستدعى مع كل حركة لمس. */
    fun info(url: String?): SiteInfo? {
        if (url.isNullOrEmpty()) return null
        if (url == lastUrl) return lastInfo
        val r = compute(url)
        lastUrl = url; lastInfo = r
        return r
    }

    fun kind(url: String?): SiteKind = info(url)?.kind ?: SiteKind.NONE

    private fun compute(url: String): SiteInfo? {
        val p = runCatching { Uri.parse(url) }.getOrNull() ?: return null
        if (p.scheme != "https") return null
        val raw = (p.host ?: return null).lowercase()
        val h = raw.removePrefix("www.")
        if (h == "youtu.be" || h == "youtube.com" || h.endsWith(".youtube.com")) {
            val music = h == "music.youtube.com"
            return SiteInfo(if (isYtVideo(url)) SiteKind.YT_VIDEO else SiteKind.YT, if (music) "YouTube Music" else "YouTube", raw)
        }
        aiNames[h]?.let { return SiteInfo(SiteKind.AI, it, raw) }
        apps[h]?.let { return SiteInfo(SiteKind.APP, it, raw) }
        if (h.startsWith("google.") && (p.getQueryParameter("udm") == "50" || (p.path ?: "").startsWith("/ai")))
            return SiteInfo(SiteKind.AI, "Google AI Mode", raw)
        return null
    }
}
